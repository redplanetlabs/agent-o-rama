(ns com.rpl.agent-o-rama.impl.ui.handlers.http
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.util.response :as resp]
   [jsonista.core :as j]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(def ^:private mapper (j/object-mapper))

(defn- parse-export-params
  "Extract module-id and dataset-id from export route: /api/datasets/:module-id/:dataset-id/export"
  [uri]
  (when-let [[_ module-id dataset-id]
             (re-matches #"/api/datasets/([^/]+)/([^/]+)/export" uri)]
    [(common/url-decode module-id)
     (UUID/fromString (common/url-decode dataset-id))]))

(defn- parse-import-params
  "Extract module-id from import route: /api/datasets/:module-id/import"
  [uri]
  (when-let [[_ module-id]
             (re-matches #"/api/datasets/([^/]+)/import" uri)]
    [(common/url-decode module-id)]))

(defn- dataset-filename
  [dataset-name]
  (-> (or dataset-name "dataset")
      (str/replace #"[^A-Za-z0-9._-]" "_")
      (str ".jsonl")))

(defn handle-dataset-export
  [request]
  (let [{:keys [uri params]} request
        [module-id dataset-id] (parse-export-params uri)
        snapshot (not-empty (get params "snapshot"))
        manager (common/get-manager module-id)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (let [{:keys [search-examples-query]} (aor-types/underlying-objects manager)
          datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
          ds-props (queries/get-dataset-properties datasets-pstate dataset-id)
          ds-name (:name ds-props)
          page-limit 10000
          ;; Single query with 10k limit
          {:keys [examples pagination-params] :as res}
          (foreign-invoke-query search-examples-query
                                dataset-id
                                snapshot
                                {} ; no filters
                                page-limit
                                nil) ; no page-key
          examples (or examples [])]
      ;; Throw if there are more items (pagination required)
      (when (seq pagination-params)
        (throw (ex-info "Dataset too large for export (>10k examples)"
                        {:dataset-id dataset-id :example-count "10000+"})))
      ;; Build JSONL with proper newlines
      (let [jsonl-lines (for [{:keys [input reference-output tags]} examples]
                          (let [frozen-input (common/->ui-serializable input)
                                frozen-output (when (some? reference-output)
                                                (common/->ui-serializable reference-output))
                                line-map (cond-> {"input" frozen-input}
                                           (some? frozen-output) (assoc "output" frozen-output)
                                           (seq tags) (assoc "tags" (->> tags (map name) vec)))]
                            (j/write-value-as-string line-map mapper)))
            jsonl-str (str/join "\n" jsonl-lines)]
        (-> (resp/response jsonl-str)
            (resp/content-type "application/jsonl; charset=utf-8")
            (resp/header "Content-Disposition"
                         (str "attachment; filename=\"" (dataset-filename ds-name) "\"")))))))

(def ^:const max-bytes (long (* 5 1024 1024)))

(defn handle-dataset-import
  [request]
  (let [{:keys [uri params multipart-params]} request
        [module-id] (parse-import-params uri)
        snapshot (not-empty (get params :snapshot))
        manager (common/get-manager module-id)
        file-param (or (get multipart-params :file)
                       (get multipart-params "file")
                       (get params :file)
                       (get params "file"))
        tempfile (:tempfile file-param)
        filename (:filename file-param)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (when-not (and (map? file-param) (instance? java.io.File tempfile))
      (-> (resp/response (j/write-value-as-string
                          {:error "Missing file upload under form field 'file'"}))
          (resp/status 400)
          (resp/content-type "application/json; charset=utf-8")
          (throw)))
    (let [^java.io.File f tempfile]
      (when (> (.length f) max-bytes)
        (-> (resp/response (j/write-value-as-string
                            {:error (str "File exceeds 5MB limit (" (.length f) " bytes)")}))
            (resp/status 413)
            (resp/content-type "application/json; charset=utf-8")
            (throw)))
      ;; Create a new dataset with filename as the name
      (let [dataset-name (or filename "imported-dataset")
            dataset-id (aor/create-dataset! manager dataset-name)
            ;; Count non-blank lines for success calculation
            total-lines (with-open [r (io/reader f)]
                          (->> (line-seq r)
                               (remove str/blank?)
                               count))
            failures* (volatile! [])]
        (datasets/upload-jsonl-examples!
         manager dataset-id snapshot (.getPath f)
         (fn [line ex]
           (vswap! failures* conj {:line_content line
                                   :error (h/throwable->str ex)})))
        (let [failure-count (count @failures*)
              success-count (max 0 (- total-lines failure-count))
              body (j/write-value-as-string
                    {:success_count success-count
                     :failure_count failure-count
                     :errors @failures*
                     :dataset_id (str dataset-id)}
                    mapper)]
          (-> (resp/response body)
              (resp/status 200)
              (resp/content-type "application/json; charset=utf-8")))))))