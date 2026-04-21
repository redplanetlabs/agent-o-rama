(ns com.rpl.agent-o-rama.impl.ui.handlers.http
  (:require
   [cognitect.transit :as transit]
   [clojure.java.io :as io]
   [clojure.string :as str]
   [org.httpkit.server :as http-kit]
   [ring.util.response :as resp]
   [jsonista.core :as j]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.datasets :as datasets])
  (:import [java.util UUID]))

(def ^:private mapper (j/object-mapper))
(def ^:private transit-content-type "application/transit+json; charset=utf-8")
(def ^:private rpc-allowlist-prefix "com.rpl.agent-o-rama.impl.ui.rpc.")

(defn- parse-rpc-route
  "Returns {:sym ..., :is-sse? true} for methods ending in !!sse (Server-Sent Events);
   {:sym ..., :is-sse? false} for ordinary !! request-response RPCs."
  [uri]
  (when-let [[_ namespace method]
             (re-matches #"(?i)/api/rpc/(.+)/([^/]+)" uri)]
    (let [method-str (common/url-decode method)
          is-sse? (str/ends-with? method-str "!!sse")]
      {:sym (symbol (str namespace "/" method-str))
       :is-sse? is-sse?})))

(defn- allowlisted-rpc-symbol?
  [sym]
  (let [n (name sym)]
    (and (str/starts-with? (str (namespace sym)) rpc-allowlist-prefix)
         (or (str/ends-with? n "!!sse")
             (str/ends-with? n "!!")))))

(defn- resolve-rpc-var
  [sym]
  (when (allowlisted-rpc-symbol? sym)
    (requiring-resolve sym)))

(defn- preprocess-rpc-payload
  [payload]
  (common/from-ui-serializable payload))

(defn invoke-rpc
  "Invoke an allowlisted RPC var directly and return a standard reply envelope."
  [{:keys [rpc-id data]}]
  (try
    (let [processed-data (preprocess-rpc-payload data)
          rpc-var (resolve-rpc-var rpc-id)]
      (cond
        (nil? rpc-var)
        {:success false
         :error (str "RPC not allowlisted or not found: " rpc-id)
         :http-status 404}

        (not (fn? @rpc-var))
        {:success false
         :error (str "RPC target is not callable: " rpc-id)
         :http-status 500}

        :else
        (try
          {:success true
           :data (common/->ui-serializable (@rpc-var @ui/system processed-data))}
          (catch Throwable e
            {:success false
             :error (or (.getMessage e) (str e) "Unknown error occurred")
             :http-status 500}))))
    (catch Throwable e
      {:success false
       :error (str "Fatal error: " (.getMessage e))
       :http-status 500})))

(defn- parse-transit-body
  [body]
  (if body
    (with-open [in ^java.io.Closeable body]
      (transit/read (transit/reader in :json)))
    nil))

(defn- transit-response
  [body]
  (-> (resp/response
       (let [out (java.io.ByteArrayOutputStream.)]
         (transit/write (transit/writer out :json) body)
         (.toString out "UTF-8")))
      (resp/content-type transit-content-type)))

(defn- write-transit-str [data]
  (let [out (java.io.ByteArrayOutputStream.)]
    (transit/write (transit/writer out :json) (common/->ui-serializable data))
    (.toString out "UTF-8")))

(def ^:private sse-stream-handles*
  "Per-tab SSE subscription handles: key = client `:sse-client-id` string from the UI (sessionStorage per tab)."
  (atom {}))


(defn handle-sse-rpc [rpc-var system processed-data request]
  (cond
    (nil? rpc-var)
    (-> (transit-response {:success false
                           :error "RPC not allowlisted or not found"
                           :http-status 404})
        (resp/status 404))

    (not (fn? @rpc-var))
    (-> (transit-response {:success false
                           :error (str "RPC target is not callable: " rpc-var)
                           :http-status 500})
        (resp/status 500))

    :else
    (let [client-id (str (or (:sse-client-id processed-data)
                             (str (UUID/randomUUID))))]
      (http-kit/as-channel
       request
       (let [this-closeable (atom nil)]
         {:on-open
          (fn [ch]
            (http-kit/send! ch
                            {:headers {"Content-Type" "text/event-stream"
                                       "Cache-Control" "no-cache"
                                       "Connection" "keep-alive"
                                       "X-Accel-Buffering" "no"}
                             :status 200}
                            false)
            (let [closeable
                  (@rpc-var
                   system
                   processed-data
                   (fn emit [data]
                     (let [payload (str "data: " (write-transit-str data) "\n\n")
                           close-after? (boolean (:complete? data))]
                       (http-kit/send! ch payload close-after?))))]
              (reset! this-closeable closeable)
              (swap! sse-stream-handles*
                     (fn [m]
                       (when-let [old (get m client-id)]
                         (when (not (identical? old closeable))
                           (.close ^java.io.Closeable old)))
                       (assoc m client-id closeable)))))

          :on-close
          (fn [_ch _status]
            (when-let [c @this-closeable]
              (swap! sse-stream-handles*
                     (fn [m]
                       (if (identical? (get m client-id) c)
                         (do
                           (.close ^java.io.Closeable c)
                           (dissoc m client-id))
                         m)))))})))))

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

(defn handle-rpc
  [request]
  (let [{:keys [uri]} request
        route (parse-rpc-route uri)
        request-body (parse-transit-body (:body request))
        payload (cond
                  (map? request-body) request-body
                  (nil? request-body) {}
                  :else (throw (ex-info "RPC request body must be a map" {:body-type (type request-body)
                                                                          :uri uri})))]
    (if-not route
      (-> (transit-response {:success false
                             :error "Bad RPC route"
                             :http-status 404})
          (resp/status 404))
      (let [{:keys [sym is-sse?]} route]
        (if is-sse?
          (let [processed-data (preprocess-rpc-payload payload)
                rpc-var (resolve-rpc-var sym)]
            (handle-sse-rpc rpc-var @ui/system processed-data request))
          (let [reply (invoke-rpc {:rpc-id sym
                                   :data payload})]
            (if (:success reply)
              (transit-response reply)
              (-> (transit-response reply)
                  (resp/status (or (:http-status reply) 400))))))))))
