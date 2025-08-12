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


(defn generate-implicit-edges
  "Compares the static historical graph with the dynamic invocation trace to find
   paths to aggregation nodes that could have been taken but were not."
  [invokes-map historical-graph]
  (let [;; A map from {agg-node-invoke-id -> #{emitter-invoke-ids}}
        actual-emits-to-aggs (into {}
                                   (for [[id data] invokes-map
                                         :when (:agg-state data)] ; Check if it's an agg-node trace
                                     [id (set (map :invoke-id (:agg-inputs-first-10 data)))]))]
    (->> invokes-map
         (mapcat (fn [[invoke-id invoke-data]]
                   (let [node-name     (:node invoke-data)
                         static-info   (get-in historical-graph [:node-map node-name])
                         agg-context   (:agg-context static-info)
                         potential-outputs (:output-nodes static-info)]
                     
                     ;; Only consider nodes that are inside an aggregation context
                     (when agg-context
                       (for [out-name potential-outputs
                             ;; We only care about potential outputs that ARE aggregation nodes
                             :when (= :agg-node (get-in historical-graph [:node-map out-name :node-type]))]
                         (let [;; This is the invoke-id of the aggregation this node belongs to.
                               agg-node-invoke-id (:agg-invoke-id invoke-data)
                               actual-emitters    (get actual-emits-to-aggs agg-node-invoke-id)
                               did-emit?          (contains? actual-emitters invoke-id)]
                           
                           (when (and (not did-emit?) agg-node-invoke-id)
                             {:source      (str invoke-id)
                              :target      (str agg-node-invoke-id)
                              :id          (str "implicit-" invoke-id "-" agg-node-invoke-id)
                              :implicit?   true})))))))
         (filter some?)
         (vec))))

(defn parse-url-pair [s]
  (let [[task-id agent-id] (clojure.string/split s #"-")]
    [(parse-long task-id) (parse-long agent-id)]))

(defn invoke-paginated
  [{{:keys [module-id agent-name invoke-id]} :path-params
    {:strs [paginate-task-id missing-node-id]} :query-params
    :as req}]

  (let [
        client-objects (objects module-id agent-name)
        root-pstate (:root-pstate client-objects)
        history-pstate (:graph-history-pstate client-objects)
        tracing-query (:tracing-query client-objects)
        
        [agent-task-id agent-id] (parse-url-pair invoke-id)
        
        ;; 1. Fetch the summary info for this invocation
        ;;    (This is the main new piece of logic)
        summary-info (foreign-select-one [
                                          (keypath agent-id)
                                          (submap [:invoke-args :result :start-time-millis :finish-time-millis :graph-version])]
                                         root-pstate
                                         {:pkey agent-task-id})
        
        ;; 2. Use graph-version from summary to get historical graph
        graph-version (:graph-version summary-info)
        
        ;; 3. Fetch the corresponding historical graph
        historical-graph (foreign-select-one [(keypath graph-version)]
                                             history-pstate
                                             {:pkey agent-task-id})
        
        ;; 4. Fetch the dynamic trace (existing logic)
        root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                           root-pstate
                                           {:pkey agent-task-id})

        pair (cond
               (and (string? paginate-task-id) (string? missing-node-id))
               [(parse-long paginate-task-id) (parse-long missing-node-id)]
               
               (and (nil? paginate-task-id) (nil? missing-node-id))
               [agent-task-id root-invoke-id])]

    (when-let [dynamic-trace (when (and pair historical-graph)
                               (foreign-invoke-query tracing-query agent-task-id [pair] 100))]
      (let [invokes-map-cleaned (-> (:invokes-map dynamic-trace)
                                    (remove-implicit-nodes)
                                    (filter-encodable))
            
            ;; 5. Generate implicit edges (existing logic)
            implicit-edges (generate-implicit-edges invokes-map-cleaned historical-graph)]
        {:status 200
         :body {:invokes-map          invokes-map-cleaned
                :next-task-invoke-pairs (:next-task-invoke-pairs dynamic-trace)
                :implicit-edges       implicit-edges
                ;; 6. Add the summary-info to the response payload
                :summary              (filter-encodable summary-info)}}))))

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

(defmethod api-handler :api/get-invocation-summary
  [_ {:keys [module-id agent-name invoke-id]} uid]
  (let [client-objects (objects module-id agent-name)
        root-pstate (:root-pstate client-objects)
        [agent-task-id agent-id] (parse-url-pair invoke-id)
        
        ;; Get basic summary info
        summary-info (foreign-select-one [(keypath agent-id)
                                          (submap [:result :start-time-millis :finish-time-millis])]
                                        root-pstate
                                        {:pkey agent-task-id})
        
        ;; Determine if invocation is complete
        is-complete (boolean (:finish-time-millis summary-info))
        
        ;; Get root invoke id for live invocations
        root-invoke-id (when-not is-complete
                        (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                          root-pstate
                                          {:pkey agent-task-id}))]
    
    {:is-complete is-complete
     :root-invoke-id root-invoke-id
     :task-id agent-task-id
     :agent-id agent-id
     :result (:result summary-info)
     :start-time-millis (:start-time-millis summary-info)
     :finish-time-millis (:finish-time-millis summary-info)}))

(defmethod api-handler :api/get-full-graph
  [_ {:keys [module-id agent-name invoke-id]} uid]
  ;; Reuse the existing paginated logic but fetch everything at once
  (let [client-objects (objects module-id agent-name)
        root-pstate (:root-pstate client-objects)
        history-pstate (:graph-history-pstate client-objects)
        tracing-query (:tracing-query client-objects)
        
        [agent-task-id agent-id] (parse-url-pair invoke-id)
        
        ;; Fetch summary and graph version
        summary-info (foreign-select-one [(keypath agent-id)
                                         (submap [:invoke-args :result :start-time-millis :finish-time-millis :graph-version])]
                                        root-pstate
                                        {:pkey agent-task-id})
        
        graph-version (:graph-version summary-info)
        
        ;; Fetch historical graph
        historical-graph (foreign-select-one [(keypath graph-version)]
                                           history-pstate
                                           {:pkey agent-task-id})
        
        ;; Get root invoke id
        root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                          root-pstate
                                          {:pkey agent-task-id})
        
        ;; Fetch complete trace - use a larger limit for complete graphs
        dynamic-trace (when (and root-invoke-id historical-graph)
                       (foreign-invoke-query tracing-query 
                                           agent-task-id 
                                           [[agent-task-id root-invoke-id]] 
                                           1000)) ;; Higher limit for complete graphs
        
        invokes-map-cleaned (when dynamic-trace
                             (-> (:invokes-map dynamic-trace)
                                 (remove-implicit-nodes)
                                 (filter-encodable)))
        
        implicit-edges (when (and invokes-map-cleaned historical-graph)
                        (generate-implicit-edges invokes-map-cleaned historical-graph))]
    
    {:invokes-map invokes-map-cleaned
     :implicit-edges implicit-edges
     :summary (filter-encodable summary-info)}))

(defmethod api-handler :api/paginate-node
  [_ {:keys [module-id agent-name invoke-id missing-node-id]} uid]
  ;; Find the task-id for the missing node and fetch it
  (let [client-objects (objects module-id agent-name)
        root-pstate (:root-pstate client-objects)
        tracing-query (:tracing-query client-objects)
        
        [agent-task-id _] (parse-url-pair invoke-id)
        
        ;; TODO: Need to find the task-id for the missing node
        ;; For now, we'll need to track this in the client state
        dynamic-trace (foreign-invoke-query tracing-query
                                           agent-task-id
                                           [[agent-task-id (parse-long missing-node-id)]]
                                           100)]
    
    (when dynamic-trace
      {:invokes-map (-> (:invokes-map dynamic-trace)
                       (remove-implicit-nodes)
                       (filter-encodable))})))

(defmethod api-handler :api/execute-fork
  [_ {:keys [module-id agent-name invoke-id changed-nodes]} uid]
  (let [[task-id agent-invoke-id] (parse-url-pair invoke-id)
        ^AgentInvoke result (aor/agent-initiate-fork
                            (get-client module-id agent-name)
                            (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                            (transform [MAP-VALS] read-string changed-nodes))]
    {:agent-invoke-id (:agentInvokeId (bean result))
     :task-id (:taskId (bean result))}))
