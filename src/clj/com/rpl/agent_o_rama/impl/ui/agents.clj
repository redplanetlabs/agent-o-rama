(ns com.rpl.agent-o-rama.impl.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [com.rpl.agent-o-rama.impl.json-serialize :as jser]
   [clojure.walk :as walk]
   [jsonista.core :as j])
  (:import
   [com.rpl.agentorama AgentInvoke]
   [java.net URLEncoder URLDecoder]))

(defn url-encode [s]
  "Encode string for safe use in URLs using standard URL encoding"
  (java.net.URLEncoder/encode ^String s "UTF-8"))

(defn url-decode [s]
  "Decode URL-encoded string using standard URL decoding"
  (java.net.URLDecoder/decode ^String s "UTF-8"))

(defn get-client [module-id agent-name]
  ;; Expects already-decoded module-id and agent-name (API handlers decode them)
  (select-one [module-id
               :clients
               agent-name]
              (ui/get-object :aor-cache)))

(defn objects [module-id agent-name]
  (aor-types/underlying-objects (get-client module-id agent-name)))

(defn manually-trigger-invoke [{{:keys [module-id agent-name]} :path-params
                                {:keys [args]} :body-params
                                :as req}]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  (let [^AgentInvoke inv (apply aor/agent-initiate (get-client module-id agent-name) args)]
    {:status 200
     :body
     {:task-id (.getTaskId inv)
      :invoke-id (.getAgentInvokeId inv)}}))

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

        ;; Get summary info on first request
        summary-info (when is-initial-load?
                       (foreign-select-one [(keypath agent-id)
                                            (submap [:result :start-time-millis :finish-time-millis :graph-version])]
                                           root-pstate
                                           {:pkey agent-task-id}))

        ;; Get historical graph on first request for implicit edge calculation
        historical-graph (when is-initial-load?
                           (when-let [graph-version (:graph-version summary-info)]
                             (foreign-select-one [(keypath graph-version)]
                                                 history-pstate
                                                 {:pkey agent-task-id})))

        ;; If no leaves, bootstrap from root
        start-pairs (if is-initial-load?
                      (let [root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                                               root-pstate {:pkey agent-task-id})]
                        [[agent-task-id root-invoke-id]])
                      leaves)

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

        ;; NEW: Apply UI serialization to make data safe for the UI
        final-cleaned-nodes (->ui-serializable cleaned-nodes)
        final-summary (->ui-serializable summary-info)
        final-historical-graph (->ui-serializable historical-graph)]

    (let [;; Always fetch completion status directly - simple and consistent
          root-status (foreign-select-one [(keypath agent-id)
                                           (submap [:result :finish-time-millis])]
                                          root-pstate
                                          {:pkey agent-task-id})
          agent-is-complete? (boolean (or (:finish-time-millis root-status)
                                          (:result root-status)))
          ;; Keep legacy variable for logging only; client no longer depends on it
          has-more-leaves? (and (not agent-is-complete?) (seq next-leaves))]
      ;; Construct simplified response. Only include keys that are present.
      (cond-> {:is-complete agent-is-complete?}
        (seq final-cleaned-nodes) (assoc :nodes final-cleaned-nodes)
        (seq next-leaves) (assoc :next-leaves next-leaves)
        is-initial-load? (assoc :summary final-summary
                                :historical-graph final-historical-graph
                                :root-invoke-id (when (seq start-pairs) (second (first start-pairs)))
                                :task-id agent-task-id
                                :agent-id agent-id)))))

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

