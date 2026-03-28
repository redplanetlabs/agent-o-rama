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
  (:import [java.util UUID]
           [com.rpl.agentorama AgentInvoke]
           [com.fasterxml.jackson.core JsonFactory StreamWriteConstraints])
  (:use [com.rpl.rama]
        [com.rpl.rama.path]))

(def ^:private mapper (j/object-mapper))

(def ^:private experiments-json-mapper
  "Deeper nesting than the default mapper so evaluator traces can serialize."
  (let [swc (.build (doto (StreamWriteConstraints/builder)
                      (.maxNestingDepth (int 10000))))
        factory (.build (doto (JsonFactory/builder)
                          (.setStreamWriteConstraints swc)))]
    (j/object-mapper {:factory factory})))

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

(defn- parse-experiments-export-params
  "module-id and dataset-id from /api/datasets/:module-id/:dataset-id/experiments/export"
  [uri]
  (when-let [[_ module-id dataset-id]
             (re-matches #"/api/datasets/([^/]+)/([^/]+)/experiments/export" uri)]
    [(common/url-decode module-id)
     (UUID/fromString (common/url-decode dataset-id))]))

(defn- experiments-export-filename
  [dataset-name]
  (-> (or dataset-name "experiments")
      (str/replace #"[^A-Za-z0-9._-]" "_")
      (str "-experiments.json")))

(defn- fetch-all-experiment-index-rows
  [search-query dataset-id]
  (loop [pagination nil
         acc []]
    (let [{:keys [items pagination-params]}
          (foreign-invoke-query search-query dataset-id {} 500 pagination)]
      (let [acc' (into acc items)]
        (if pagination-params
          (recur pagination-params acc')
          acc')))))

(defn- clean-trace-invokes-map
  [invokes-map]
  (when invokes-map
    (->> invokes-map
         common/remove-implicit-nodes
         (transform
          [MAP-VALS :feedback :results ALL]
          (fn [feedback-result]
            (let [feedback-map (into {} feedback-result)
                  source (:source feedback-map)]
              (if source
                (assoc feedback-map :source-string (aor-types/source-string source))
                feedback-map))))
         (transform
          [MAP-VALS :feedback :results ALL :scores MAP-KEYS]
          name)
         (transform
          [MAP-VALS :feedback :actions MAP-KEYS]
          name))))

(defn- evaluator-trace-for-invoke
  "Uses the same tracing query topology as the UI graph view (`_agent-get-trace-page`)."
  [evaluator-client-objects ^AgentInvoke invoke]
  (when invoke
    (let [tracing-query (:tracing-query evaluator-client-objects)
          root-pstate (:root-pstate evaluator-client-objects)
          task-id (.getTaskId invoke)
          agent-id (.getAgentInvokeId invoke)]
      (when (and tracing-query root-pstate)
        (when-let [root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                                      root-pstate
                                                      {:pkey task-id})]
          (let [dynamic-trace (foreign-invoke-query tracing-query
                                                    task-id
                                                    [[task-id root-invoke-id]]
                                                    10000)]
            (update dynamic-trace :invokes-map clean-trace-invokes-map)))))))

(defn- agent-invoke->edn-map
  [^AgentInvoke inv]
  (when inv
    {:task-id (.getTaskId inv)
     :agent-invoke-id (str (.getAgentInvokeId inv))}))

(defn- finalize-experiment-for-export
  "Full `_aor-experiment-results` row, optional `_agent-get-trace-page` under :evaluator-trace."
  [results-q dataset-id eid eco include-trace?]
  (let [base (foreign-invoke-query results-q dataset-id eid)
        inv (:experiment-invoke base)
        trace (when (and include-trace? eco (instance? AgentInvoke inv))
                (evaluator-trace-for-invoke eco inv))
        base (cond-> base
               (instance? AgentInvoke inv)
               (update :experiment-invoke agent-invoke->edn-map))]
    (cond-> base (some? trace) (assoc :evaluator-trace trace))))

(defn- parse-single-experiment-export-params
  "module-id, dataset-id, experiment-id from .../experiments/:eid/export"
  [uri]
  (when-let [[_ module-id dataset-id experiment-id]
             (re-matches #"/api/datasets/([^/]+)/([^/]+)/experiments/([^/]+)/export" uri)]
    [(common/url-decode module-id)
     (UUID/fromString (common/url-decode dataset-id))
     (UUID/fromString (common/url-decode experiment-id))]))

(defn- sanitize-filename-part
  [s fallback]
  (let [t (-> (if (str/blank? s) fallback s)
              (str/replace #"[^A-Za-z0-9._-]" "_"))]
    (if (> (count t) 48)
      (subs t 0 48)
      t)))

(defn- single-experiment-export-filename
  [dataset-name experiment-name experiment-id]
  (str (sanitize-filename-part dataset-name "dataset")
       "-"
       (sanitize-filename-part experiment-name "experiment")
       "-"
       experiment-id
       ".json"))

(defn handle-single-experiment-export
  "Export one experiment (same payload shape as batch export, :experiment-count 1)."
  [request]
  (let [{:keys [uri params]} request
        [module-id dataset-id experiment-id] (parse-single-experiment-export-params uri)
        include-trace? (not= "false" (str (get params "trace")))
        manager (common/get-manager module-id)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (let [objs (aor-types/underlying-objects manager)
          results-q (:experiments-results-query objs)
          datasets-pstate (:datasets-pstate objs)
          ds-props (queries/get-dataset-properties datasets-pstate dataset-id)
          ds-name (:name ds-props)
          experiments (if include-trace?
                          (with-open [exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)]
                            (let [eco (aor-types/underlying-objects exp-client)]
                              [(finalize-experiment-for-export results-q dataset-id experiment-id eco true)]))
                          [(finalize-experiment-for-export results-q dataset-id experiment-id nil false)])
          exp-name (get-in (first experiments) [:experiment-info :name])
          payload (common/->ui-serializable
                   {:dataset-id (str dataset-id)
                    :dataset-name ds-name
                    :experiment-id (str experiment-id)
                    :experiment-count (count experiments)
                    :experiments experiments})]
      (-> (resp/response (j/write-value-as-string payload experiments-json-mapper))
          (resp/content-type "application/json; charset=utf-8")
          (resp/header "Content-Disposition"
                       (str "attachment; filename=\""
                            (single-experiment-export-filename ds-name exp-name experiment-id)
                            "\""))))))

(defn handle-experiments-export
  "Export all experiments for a dataset as JSON (full results query per experiment).
  Optional query param: trace=false to skip `_agent-get-trace-page` enrichment."
  [request]
  (let [{:keys [uri params]} request
        [module-id dataset-id] (parse-experiments-export-params uri)
        include-trace? (not= "false" (str (get params "trace")))
        manager (common/get-manager module-id)]
    (when-not manager
      (throw (ex-info "Unknown module" {:module-id module-id})))
    (let [objs (aor-types/underlying-objects manager)
          search-q (:search-experiments-query objs)
          results-q (:experiments-results-query objs)
          datasets-pstate (:datasets-pstate objs)
          ds-props (queries/get-dataset-properties datasets-pstate dataset-id)
          ds-name (:name ds-props)
          index-rows (fetch-all-experiment-index-rows search-q dataset-id)
          experiments
          (if include-trace?
            (with-open [exp-client (aor/agent-client manager aor-types/EVALUATOR-AGENT-NAME)]
              (let [eco (aor-types/underlying-objects exp-client)]
                (mapv
                 (fn [row]
                   (finalize-experiment-for-export
                    results-q
                    dataset-id
                    (:id (:experiment-info row))
                    eco
                    true))
                 index-rows)))
            (mapv
             (fn [row]
               (finalize-experiment-for-export
                results-q
                dataset-id
                (:id (:experiment-info row))
                nil
                false))
             index-rows))
          payload (common/->ui-serializable
                   {:dataset-id (str dataset-id)
                    :dataset-name ds-name
                    :experiment-count (count experiments)
                    :experiments experiments})]
      (-> (resp/response (j/write-value-as-string payload experiments-json-mapper))
          (resp/content-type "application/json; charset=utf-8")
          (resp/header "Content-Disposition"
                       (str "attachment; filename=\""
                            (experiments-export-filename ds-name) "\""))))))

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
