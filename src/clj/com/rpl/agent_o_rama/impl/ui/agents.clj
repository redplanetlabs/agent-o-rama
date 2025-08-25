(ns com.rpl.agent-o-rama.impl.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.json-serialize :as jser]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [clojure.walk :as walk]
   [clojure.string :as str]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama AgentInvoke]
   [java.net URLEncoder URLDecoder]
   [java.util UUID]))

(defn url-encode [s]
  "Encode string for safe use in URLs using standard URL encoding"
  (java.net.URLEncoder/encode ^String s "UTF-8"))

(defn url-decode [s]
  "Decode URL-encoded string using standard URL decoding"
  (java.net.URLDecoder/decode ^String s "UTF-8"))

(defn get-client [module-id agent-name]
  (def module-id module-id)
  ;; Expects already-decoded module-id and agent-name (API handlers decode them)
  (select-one [module-id
               :clients
               agent-name]
              (ui/get-object :aor-cache)))

(defn get-manager [module-id]
  (select-one [module-id :manager] (ui/get-object :aor-cache)))

(defn objects [module-id agent-name]
  (aor-types/underlying-objects (get-client module-id agent-name)))

(defn remove-implicit-nodes
  "Preprocesses the invokes-map to remove implicit nodes and rewire edges to real nodes.
   Returns a new map without implicit nodes where all references are updated."
  [invokes-map]
  (let [implicit->real
        (into {}
              (select [ALL
                       (selected? LAST (must :invoked-agg-invoke-id))
                       (view (fn [[id node]]
                               [id (:invoked-agg-invoke-id node)]))]
                      invokes-map))]
    (->> invokes-map
         (setval [ALL
                  (selected? LAST (must :invoked-agg-invoke-id))]
                 NONE)
         (transform [ALL
                     LAST
                     (must :emits)
                     ALL
                     :invoke-id]
                    #(get implicit->real % %)))))

(defn ->ui-serializable
  [data]
  (walk/postwalk
   (fn [item]
     (if (satisfies? jser/JSONFreeze item)
       (jser/json-freeze*-with-type item)
       item))
   data))

(defn from-ui-serializable
  [data]
  (walk/postwalk
   jser/json-thaw*
   data))

(defn parse-url-pair [s]
  (let [[task-id agent-id] (clojure.string/split s #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

;; ============================================================================
;; LIVE GRAPH SUPPORT (server-side helper)
;; ============================================================================

(defn current-invocation-invokes-map
  "Return the cleaned invokes-map for a specific invocation starting from given leaves.
   - Keeps filter-encodable and remove-implicit-nodes
   - Supports pagination from leaf nodes or root
   - Returns both the invokes-map and next-task-invoke-pairs for continued pagination"
  [module-id agent-name invoke-id start-pairs]
  (let [client-objects (objects module-id agent-name)
        tracing-query (:tracing-query client-objects)
        [agent-task-id _] (parse-url-pair invoke-id)
        dynamic-trace (when (and agent-task-id (seq start-pairs))
                        (foreign-invoke-query tracing-query
                                              agent-task-id
                                              start-pairs
                                              100))]
    (when dynamic-trace
      {:invokes-map (when-let [invokes-map (:invokes-map dynamic-trace)]
                      (-> invokes-map
                          (remove-implicit-nodes)))
       :next-task-invoke-pairs (:next-task-invoke-pairs dynamic-trace)})))

;; =============================================================================
;; SENTE API HANDLERS
;; =============================================================================

(defmulti api-handler
  "Handle API requests. Receives [event-id data uid] and returns response data.
   Exceptions are automatically caught and returned as errors."
  (fn [event-id data uid] event-id))

(defmethod api-handler :api/get-agents
  [_ data uid]
  (for [[module-name agent-name]
        (select [ALL (collect-one FIRST) LAST :clients MAP-KEYS] (ui/get-object :aor-cache))]
    {:module-id (url-encode module-name) ; Use standard URL encoding
     :agent-name (url-encode agent-name)}))

(defmethod api-handler :api/get-invocations
  [_ {:keys [module-id agent-name pagination]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        pages (if (empty? pagination) nil pagination)]
    (foreign-invoke-query
     (:invokes-page-query (objects decoded-module-id decoded-agent-name))
     10 pages)))

(defmethod api-handler :api/get-graph
  [_ {:keys [module-id agent-name]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)]
    {:graph (foreign-invoke-query
             (:current-graph-query
              (objects decoded-module-id decoded-agent-name)))}))

(defmethod api-handler :api/run-agent
  [_ {:keys [module-id agent-name args]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)]
    (when-not (vector? args)
      (throw (ex-info "must be a json list of args" {:bad-args args})))
    (let [^AgentInvoke inv (apply aor/agent-initiate (get-client decoded-module-id decoded-agent-name) args)]
      {:task-id (.getTaskId inv)
       :invoke-id (.getAgentInvokeId inv)})))

;; Unified graph page fetcher - replaces separate live/historical flows
(defmethod api-handler :api/fetch-graph-page
  [_ {:keys [module-id agent-name invoke-id leaves initial?]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        client-objects (objects decoded-module-id decoded-agent-name)
        tracing-query (:tracing-query client-objects)
        root-pstate (:root-pstate client-objects)
        history-pstate (:graph-history-pstate client-objects)
        [agent-task-id agent-id] (parse-url-pair invoke-id)

        ;; Explicit initial flag from client; fallback to leaves-empty for backward compat
        is-initial-load? (boolean initial?)

        ;; ALWAYS get summary info on EVERY request now
        summary-info (merge
                      {:forks (foreign-select-one [(keypath agent-id)
                                                   :forks
                                                   (sorted-set-range-to-end 100)]
                                                  root-pstate
                                                  {:pkey agent-task-id})}
                      (foreign-select-one [(keypath agent-id)
                                           (submap [:result :start-time-millis :finish-time-millis :graph-version :retry-num
                                                    :fork-of :exception-summaries])]
                                          root-pstate
                                          {:pkey agent-task-id}))

        ;; Only fetch root-invoke-id on the initial load
        root-invoke-id (when is-initial-load?
                         (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                             root-pstate {:pkey agent-task-id}))

        ;; Get historical graph on first request for implicit edge calculation
        historical-graph (when is-initial-load?
                           (when-let [graph-version (:graph-version summary-info)]
                             (foreign-select-one [(keypath graph-version)]
                                                 history-pstate
                                                 {:pkey agent-task-id})))

        start-pairs (if is-initial-load?
                      [[agent-task-id root-invoke-id]] ; bootstrap from the fetched root
                      leaves) ; use client-provided leaves

        ;; Use larger page size on first fetch to fast-path historical data
        page-limit (if is-initial-load? 1000 100)
        dynamic-trace (when (seq start-pairs)
                        (foreign-invoke-query tracing-query
                                              agent-task-id
                                              start-pairs
                                              page-limit))
        cleaned-nodes (when-let [m (:invokes-map dynamic-trace)]
                        (-> m remove-implicit-nodes))
        next-leaves (:next-task-invoke-pairs dynamic-trace)
        ;; Always fetch completion status directly - simple and consistent
        root-status (foreign-select-one [(keypath agent-id)
                                         (submap [:result :finish-time-millis])]
                                        root-pstate
                                        {:pkey agent-task-id})
        agent-is-complete? (boolean (or (:finish-time-millis root-status)
                                        (:result root-status)))
        ;; Keep legacy variable for logging only; client no longer depends on it
        has-more-leaves? (and (not agent-is-complete?) (seq next-leaves))]

    ;; Construct simplified response. Only include keys that are present.
    ;; Serialization now handled centrally in Sente handler
    (cond-> {:is-complete agent-is-complete?}
      (seq cleaned-nodes) (assoc :nodes cleaned-nodes)
      (seq next-leaves) (assoc :next-leaves next-leaves)
      true (assoc :summary summary-info
                  :task-id agent-task-id
                  :agent-id agent-id)
      ;; Conditionally add :root-invoke-id and :historical-graph only on the first load
      is-initial-load? (assoc :root-invoke-id root-invoke-id
                              :historical-graph historical-graph))))

(defmethod api-handler :api/execute-fork
  [_ {:keys [module-id agent-name invoke-id changed-nodes]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        [task-id agent-invoke-id] (parse-url-pair invoke-id)

        ;; 1. Parse each node's input string from JSON into Clojure data structures.
        ;; We use string keys to preserve "_aor-type" for multimethod dispatch.
        json-parsed-nodes (transform [MAP-VALS]
                                     #(j/read-value %)
                                     changed-nodes)

        ;; 2. Walk the resulting Clojure data and deserialize the special maps
        ;;    (with _aor-type) back into their Java object instances.
        rehydrated-nodes (from-ui-serializable json-parsed-nodes)

        ;; 3. Now pass the correctly-typed data to the agent framework.
        ^AgentInvoke result (aor/agent-initiate-fork
                             (get-client decoded-module-id decoded-agent-name)
                             (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                             rehydrated-nodes)]
    {:agent-invoke-id (:agentInvokeId (bean result))
     :task-id (:taskId (bean result))}))

(defmethod api-handler :api/provide-human-input
  [_ {:keys [module-id agent-name request response]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        {:keys [agent-task-id agent-id node node-task-id invoke-id uuid prompt]} request
        ;; Rebuild a NodeHumanInputRequest record on the server side
        req (aor-types/->NodeHumanInputRequest
             agent-task-id agent-id node node-task-id invoke-id prompt uuid)]
    (aor/provide-human-input (get-client decoded-module-id decoded-agent-name) req response)
    {:ok true}))

;; Helper to determine UI input type from config's validation function
(defn- schema-fn->input-type [schema-fn]
  (cond
    (or (= schema-fn aor-types/natural-long?)
        (= schema-fn aor-types/positive-long?)) :number
    (= schema-fn h/boolean-spec) :boolean
    :else :text))

;; Get the available configs and their current values
(defmethod api-handler :api/get-agent-config
  [_ {:keys [module-id agent-name]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        client-objects (objects decoded-module-id decoded-agent-name)
        config-pstate (:config-pstate client-objects)
        current-config-map (or (foreign-select-one STAY config-pstate {:pkey 0}) {})]
    ;; Iterate through all defined configs, get their current value, and add metadata
    (for [[key config-def] aor-types/ALL-CONFIGS]
      (let [current-value (get current-config-map key (:default config-def))]
        {:key key
         :doc (:doc config-def)
         :current-value (str current-value) ;; Send as string to UI
         :default-value (str (:default config-def))
         :input-type (schema-fn->input-type (:schema-fn config-def))}))))

;; Set a new config value
(defmethod api-handler :api/set-agent-config
  [_ {:keys [module-id agent-name key value]} uid]
  (let [decoded-module-id (url-decode module-id)
        decoded-agent-name (url-decode agent-name)
        client-objects (objects decoded-module-id decoded-agent-name)
        agent-config-depot (:agent-config-depot client-objects)
        config-def (get aor-types/ALL-CONFIGS key)]
    (when-not config-def
      (throw (ex-info "Unknown configuration key" {:key key})))

    ;; Parse the value from the UI and create the change message
    (try
      (let [parsed-value (case (schema-fn->input-type (:schema-fn config-def))
                           :number (Long/parseLong value)
                           value)
            ;; Get the change function directly from the config definition
            change-fn (:change-fn config-def)
            change-record (change-fn parsed-value)]
        (foreign-append! agent-config-depot change-record)
        {:success true})
      (catch Exception e
        (throw (ex-info (str "Failed to set config: " (.getMessage e))
                        {:key key :value value}))))))

(defmethod api-handler :api/get-agents-for-module
  [_ {:keys [module-id]} uid]
  (let [decoded-module-id (url-decode module-id)]
    (if-let [manager (get-manager decoded-module-id)]
      ;; If we found the manager for the module, get its agent names
      (let [agent-names (aor/agent-names manager)]
        (mapv (fn [agent-name]
                {:module-id module-id ; Return the original encoded ID for URL generation
                 :agent-name (url-encode agent-name)})
              agent-names))
      ;; If no manager is found for the module-id, return an empty list.
      ;; This can happen transiently or if the module has no agents.
      [])))

;; =============================================================================
;; DATASET CRUD API HANDLERS
;; =============================================================================

(defmethod api-handler :api/get-datasets
  [_ {:keys [module-id pagination]} uid]
  "Fetches a paginated list of all datasets for a given module."
  (let [decoded-module-id (url-decode module-id)
        manager (get-manager decoded-module-id)
        datasets-page-query (:datasets-page-query (aor-types/underlying-objects manager))]
    ;; Use page size of 100 to avoid needing pagination on the UI for now
    (foreign-invoke-query datasets-page-query 100 pagination)))

(defmethod api-handler :api/get-dataset-props
  [_ {:keys [module-id dataset-id]} uid]
  "Fetches the properties (name, description, schemas) for a single dataset."
  (let [decoded-module-id (url-decode module-id)
        manager (get-manager decoded-module-id)
        datasets-pstate (:datasets-pstate (aor-types/underlying-objects manager))]
    (queries/get-dataset-properties datasets-pstate (UUID/fromString dataset-id))))

(defmethod api-handler :api/create-dataset
  [_ {:keys [module-id name description input-schema output-schema]} uid]
  "Creates a new dataset. Handles validation errors from the backend."
  (let [decoded-module-id (url-decode module-id)
        manager (get-manager decoded-module-id)]
    (try
      (let [dataset-id (aor/create-dataset!
                        manager
                        name
                        {:description (when-not (str/blank? description) description)
                         :input-json-schema (when-not (str/blank? input-schema) input-schema)
                         :output-json-schema (when-not (str/blank? output-schema) output-schema)})]
        {:status :ok, :dataset-id dataset-id})
      (catch Exception e
        ;; Propagate validation or other errors back to the UI
        (throw (ex-info (-> e .getCause .getMessage) {}))))))

(defmethod api-handler :api/update-dataset-props
  [_ {:keys [module-id dataset-id name description]} uid]
  "Updates the properties of an existing dataset."
  (let [decoded-module-id (url-decode module-id)
        manager (get-manager decoded-module-id)
        uuid (UUID/fromString dataset-id)]
    (aor/set-dataset-name! manager uuid name)
    (aor/set-dataset-description! manager uuid description)
    {:status :ok}))

(defmethod api-handler :api/delete-dataset
  [_ {:keys [module-id dataset-id]} uid]
  "Deletes a dataset."
  (let [decoded-module-id (url-decode module-id)
        manager (get-manager decoded-module-id)
        uuid (UUID/fromString dataset-id)]
    (aor/destroy-dataset! manager uuid)
    {:status :ok}))

