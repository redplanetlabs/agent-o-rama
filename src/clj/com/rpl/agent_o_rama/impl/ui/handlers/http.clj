(ns com.rpl.agent-o-rama.impl.ui.handlers.http
  (:require
   [clojure.java.io :as io]
   [clojure.string :as str]
   [ring.util.response :as resp]
   [jsonista.core :as j]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets]
   [com.rpl.agent-o-rama.impl.helpers :as h])
  (:import [java.util UUID])
  (:use [com.rpl.rama]))

(def ^:private mapper (j/object-mapper))

(defn- parse-path-params
  "Extract `[module-id dataset-id action]` from /api/datasets/:module-id/:dataset-id/:action"
  [uri]
  (when-let [[_ module-id dataset-id action]
             (re-matches #"/api/datasets/([^/]+)/([^/]+)/(export|import)" uri)]
    [(common/url-decode module-id)
     (UUID/fromString (common/url-decode dataset-id))
     action]))

(defn- dataset-filename
  [dataset-name]
  (-> (or dataset-name "dataset")
      (str/replace #"[^A-Za-z0-9._-]" "_")
      (str ".jsonl")))

(defn handle-dataset-export
  [request]
  (let [{:keys [uri params]} request
        [module-id dataset-id _] (parse-path-params uri)
        snapshot (not-empty (get params "snapshot"))
        manager (common/get-manager module-id)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (let [{:keys [search-examples-query]} (aor-types/underlying-objects manager)
          datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
          ds-props (queries/get-dataset-properties datasets-pstate dataset-id)
          ds-name (:name ds-props)
          page-limit 1000
          ;; paginate through all examples
          jsonl-str (loop [page-key nil
                           acc (StringBuilder.)
                           first? true]
                      (let [{:keys [examples pagination-params] :as res}
                            (foreign-invoke-query search-examples-query
                                                  dataset-id
                                                  snapshot
                                                  {} ; no filters
                                                  page-limit
                                                  page-key)
                            examples (or examples [])]
                        (doseq [{:keys [input reference-output tags]} examples]
                          (let [line-map (cond-> {"input" input}
                                           (some? reference-output) (assoc "output" reference-output)
                                           (seq tags) (assoc "tags" (->> tags (map name) vec)))
                                line-json (j/write-value-as-string line-map mapper)]
                            (when-not first?
                              (.append acc \newline))
                            (.append acc line-json)))
                        (if pagination-params
                          (recur pagination-params acc false)
                          (str acc))))]
      (-> (resp/response jsonl-str)
          (resp/content-type "application/jsonl; charset=utf-8")
          (resp/header "Content-Disposition"
                       (str "attachment; filename=\"" (dataset-filename ds-name) "\""))))))

(def ^:const max-bytes (long (* 5 1024 1024)))

(defn handle-dataset-import
  [request]
  (let [{:keys [uri params]} request
        [module-id dataset-id _] (parse-path-params uri)
        snapshot (not-empty (get params "snapshot"))
        manager (common/get-manager module-id)
        file-param (get params "file")
        tempfile (:tempfile file-param)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (when-not (and (map? file-param) (instance? java.io.File tempfile))
      (throw (ex-info "Missing file upload under form field 'file'" {})))
    (let [^java.io.File f tempfile]
      (when (> (.length f) max-bytes)
        (-> (resp/response (j/write-value-as-string
                            {:error (str "File exceeds 5MB limit (" (.length f) " bytes)")}))
            (resp/status 413)
            (resp/content-type "application/json; charset=utf-8")
            (throw)))
      ;; Count non-blank lines for success calculation
      (let [total-lines (with-open [r (io/reader f)]
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
                     :errors @failures*}
                    mapper)]
          (-> (resp/response body)
              (resp/status (if (pos? failure-count) 200 200))
              (resp/content-type "application/json; charset=utf-8")))))))
