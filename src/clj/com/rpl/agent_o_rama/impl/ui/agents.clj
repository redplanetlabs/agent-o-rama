(ns com.rpl.agent-o-rama.impl.ui.agents
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui :as ui]
   [clojure.walk :as walk]
   [muuntaja.core :as m])
  (:import
   [com.rpl.agentorama AgentInvoke]))

(def m (m/create))
(def encoder (m/encoder m "application/transit+json"))

(defn filter-encodable
  [data]
  (walk/postwalk
   (fn [x]
     (try
       (encoder x)
       x
       (catch Exception e
         (str x))))
   data))

(defn replace-slash [s]
  "because urlencoding causes jetty to 400 with Ambiguous URI path separator"
  ;; TODO use proper urlencoding, fix jetty error
  (clojure.string/replace s #"/" "::"))

(defn unreplace-slash [s]
  "reverse of above function"
  (clojure.string/replace s #"::" "/"))

(comment
  (replace-slash "example.core/FlowModule")
  (unreplace-slash "example.core::FlowModule"))


(defn get-client [module-id agent-name]
  (select-one [(unreplace-slash module-id)
               :clients
               (unreplace-slash agent-name)]
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
                          (remove-implicit-nodes)
                          (filter-encodable)))
       :next-task-invoke-pairs (:next-task-invoke-pairs dynamic-trace)})))

(defn fork [{{:keys [module-id agent-name]} :path-params
             {:keys [changed-nodes invoke-id]} :body-params}]
  (let [^AgentInvoke result (let [[task-id agent-invoke-id]
                                  (parse-url-pair invoke-id)]
                              (aor/agent-initiate-fork
                               (get-client module-id agent-name)
                               (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                               (transform [MAP-VALS] read-string changed-nodes)))]
    {:status 200
     :body
     {:agent-invoke-id (:agentInvokeId (bean result))
      :task-id (:taskId (bean result))}}))

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
    {:module-id (replace-slash module-name)
     :agent-name (replace-slash agent-name)}))

(defmethod api-handler :api/get-invocations
  [_ {:keys [module-id agent-name pagination]} uid]
  (let [pages (if (empty? pagination) nil pagination)]
    (filter-encodable (foreign-invoke-query
                       (:invokes-page-query (objects module-id agent-name))
                       10 pages))))

(defmethod api-handler :api/get-graph
  [_ {:keys [module-id agent-name]} uid]
  {:graph (foreign-invoke-query
           (:current-graph-query
            (objects module-id agent-name)))})

(defmethod api-handler :api/run-agent
  [_ {:keys [module-id agent-name args]} uid]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  (let [^AgentInvoke inv (apply aor/agent-initiate (get-client module-id agent-name) args)]
    {:task-id (.getTaskId inv)
     :invoke-id (.getAgentInvokeId inv)}))


;; Unified graph page fetcher - replaces separate live/historical flows
(defmethod api-handler :api/fetch-graph-page
  [_ {:keys [module-id agent-name invoke-id leaves]} uid]
  (let [client-objects (objects module-id agent-name)
        tracing-query (:tracing-query client-objects)
        root-pstate (:root-pstate client-objects)
        history-pstate (:graph-history-pstate client-objects)
        [agent-task-id agent-id] (parse-url-pair invoke-id)
        
        ;; On first request (empty leaves), fetch summary data too
        is-first-request? (or (nil? leaves) (empty? leaves))
        
        ;; Get summary info on first request
        summary-info (when is-first-request?
                       (foreign-select-one [(keypath agent-id)
                                           (submap [:result :start-time-millis :finish-time-millis :graph-version])]
                                          root-pstate
                                          {:pkey agent-task-id}))
        
        ;; Get historical graph on first request for implicit edge calculation
        historical-graph (when is-first-request?
                          (when-let [graph-version (:graph-version summary-info)]
                            (foreign-select-one [(keypath graph-version)]
                                               history-pstate
                                               {:pkey agent-task-id})))
        
        ;; If no leaves, bootstrap from root
        start-pairs (if is-first-request?
                      (let [root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                                              root-pstate {:pkey agent-task-id})]
                        [[agent-task-id root-invoke-id]])
                      leaves)
        
        ;; Use larger page size on first fetch to fast-path historical data
        page-limit (if is-first-request? 1000 100)
        dynamic-trace (when (seq start-pairs)
                        (foreign-invoke-query tracing-query
                                              agent-task-id
                                              start-pairs
                                              page-limit))
        cleaned-nodes (when-let [m (:invokes-map dynamic-trace)]
                        (-> m remove-implicit-nodes filter-encodable))
        next-leaves (:next-task-invoke-pairs dynamic-trace)]
    
    (let [;; Always fetch completion status directly - simple and consistent
          agent-is-complete? (boolean 
                               (:finish-time-millis 
                                 (foreign-select-one [(keypath agent-id) :finish-time-millis]
                                                    root-pstate
                                                    {:pkey agent-task-id})))
          ;; Stream should continue if agent is still running AND there are more leaves
          has-more-leaves? (and (not agent-is-complete?) (seq next-leaves))]
      (merge {:nodes cleaned-nodes
              :next-leaves next-leaves
              :has-more-leaves? has-more-leaves?
              ;; ALWAYS include is-complete status for client state machine
              :is-complete agent-is-complete?}
             ;; Include summary data only on first request
             (when is-first-request?
               {:summary (filter-encodable summary-info)
                :historical-graph (filter-encodable historical-graph)
                :root-invoke-id (when (seq start-pairs) (second (first start-pairs)))
                :task-id agent-task-id
                :agent-id agent-id})))))

(defmethod api-handler :api/execute-fork
  [_ {:keys [module-id agent-name invoke-id changed-nodes]} uid]
  (let [[task-id agent-invoke-id] (parse-url-pair invoke-id)
        ^AgentInvoke result (aor/agent-initiate-fork
                            (get-client module-id agent-name)
                            (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                            (transform [MAP-VALS] read-string changed-nodes))]
    {:agent-invoke-id (:agentInvokeId (bean result))
     :task-id (:taskId (bean result))}))




(defmethod api-handler :api/provide-human-input
  [_ {:keys [module-id agent-name request response]} uid]
  (let [{:keys [agent-task-id agent-id node node-task-id invoke-id uuid prompt]} request
        ;; Rebuild a NodeHumanInputRequest record on the server side
        req (aor-types/->NodeHumanInputRequest
             agent-task-id agent-id node node-task-id invoke-id prompt uuid)]
    (aor/provide-human-input (get-client module-id agent-name) req response)
    {:ok true}))

