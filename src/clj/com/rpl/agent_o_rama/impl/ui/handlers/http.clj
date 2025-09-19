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
  "Extract module-id and dataset-id from import route: /api/datasets/:module-id/:dataset-id/import"
  [uri]
  (when-let [[_ module-id dataset-id]
             (re-matches #"/api/datasets/([^/]+)/([^/]+)/import" uri)]
    [(common/url-decode module-id)
     (UUID/fromString (common/url-decode dataset-id))]))

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
    
    (let [datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))
          ds-props (queries/get-dataset-properties datasets-pstate dataset-id)
          ds-name (:name ds-props)
          output (java.io.StringWriter.)
          failures* (volatile! [])]
      ;; Use the centralized download function
      (datasets/download-jsonl-examples-impl!
       manager
       dataset-id
       snapshot
       output
       (fn [example-id ex]
         (vswap! failures* conj {:example-id example-id :error (ex-message ex)})))
      ;; If there were failures, we could log them or handle them differently
      ;; For now, we'll include successful examples in the response
      ;; TODO throw failures.
      (let [jsonl-str (.toString output)]
        (-> (resp/response jsonl-str)
            (resp/content-type "application/jsonl; charset=utf-8")
            (resp/header "Content-Disposition"
                         (str "attachment; filename=\"" (dataset-filename ds-name) "\"")))))))

(def ^:const max-bytes (long (* 5 1024 1024)))

(defn handle-dataset-import
  [request]
  (let [{:keys [uri params multipart-params]} request
        [module-id dataset-id] (parse-import-params uri)
        snapshot (not-empty (get params :snapshot))
        manager (common/get-manager module-id)
        file-param (or (get multipart-params :file)
                       (get params :file))
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
      ;; Import into existing dataset
      (let [;; Count non-blank lines for success calculation
            total-lines (with-open [r (io/reader f)]
                          (->> (line-seq r)
                               (remove str/blank?)
                               count))
            failures* (volatile! [])]
        (datasets/upload-jsonl-examples!
         manager dataset-id snapshot (.getPath f)
         (fn [line ex]
           (vswap! failures* conj {:line_content line
                                   :error (ex-message ex)})))
        (let [failure-count (count @failures*)
              success-count (max 0 (- total-lines failure-count))
              body (j/write-value-as-string
                    {:success_count success-count
                     :failure_count failure-count
                     :errors @failures*}
                    mapper)]
          (-> (resp/response body)
              (resp/status 200)
              (resp/content-type "application/json; charset=utf-8")))))))
