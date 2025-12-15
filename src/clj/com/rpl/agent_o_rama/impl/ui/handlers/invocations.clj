(ns com.rpl.agent-o-rama.impl.ui.handlers.invocations
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.stats :as stats]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [jsonista.core :as j])
  (:import [com.rpl.agentorama AgentInvoke]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-page
  [{:keys [client pagination]} uid]
  (let [pages (if (empty? pagination) nil pagination)]
    (when client ; this can be nil on restarts of the backend
      (foreign-invoke-query
       (:invokes-page-query (aor-types/underlying-objects client))
       10 pages))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/run-agent
  [{:keys [client args metadata]} uid]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  (let [metadata (or metadata {})
        ^AgentInvoke inv (apply aor/agent-initiate-with-context client {:metadata metadata} args)]
    {:task-id (.getTaskId inv)
     :invoke-id (.getAgentInvokeId inv)}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-graph-page
  [{:keys [client invoke-pair]} _uid]
  (when client
    (let [;; Get all underlying objects from the agent-specific client
          client-objects (aor-types/underlying-objects client)
          tracing-query (:tracing-query client-objects)
          root-pstate (:root-pstate client-objects)
          stream-shared-pstate (:stream-shared-pstate client-objects)

          [agent-task-id agent-id] invoke-pair

          ;; Fetch summary info - always needed
          summary-info-raw (foreign-select-one
                            [(keypath agent-id)
                             (submap
                              [:result :start-time-millis :finish-time-millis :graph-version
                               :retry-num :fork-of :exception-summaries :invoke-args :stats
                               :feedback :metadata])]
                            root-pstate
                            {:pkey agent-task-id})

          ;; Add aggregated stats to the stats object
          summary-info (merge
                        {:forks (foreign-select-one
                                 [(keypath agent-id) :forks
                                  (sorted-set-range-to-end 100)]
                                 root-pstate
                                 {:pkey agent-task-id})}
                        (->> summary-info-raw
                             (transform
                              [:feedback :results ALL :source :source]
                              aor-types/source-string)
                             (transform [:feedback :actions MAP-KEYS] name))
                        (when-let [stats (:stats summary-info-raw)]
                          {:stats (merge {:aggregated-stats
                                          (stats/aggregated-basic-stats stats)}
                                         stats)}))

          ;; Always fetch root invoke ID
          root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                             root-pstate
                                             {:pkey agent-task-id})

          ;; Always fetch historical graph (static topology)
          historical-graph (when-let [graph-version (:graph-version summary-info)]
                             (foreign-select-one [:history (keypath graph-version)]
                                                 stream-shared-pstate
                                                 {:pkey 0}))

          ;; SIMPLIFIED: Always query from root with reasonable page limit
          dynamic-trace (foreign-invoke-query tracing-query
                                              agent-task-id
                                              [[agent-task-id root-invoke-id]]
                                              10000)

          cleaned-nodes (when-let [m (:invokes-map dynamic-trace)]
                          (->> m
                               common/remove-implicit-nodes
                               (transform
                                [MAP-VALS :feedback :results ALL :source :source]
                                aor-types/source-string)
                               (transform
                                [MAP-VALS :feedback :results ALL :scores MAP-KEYS]
                                name)
                               (transform
                                [MAP-VALS :feedback :actions MAP-KEYS]
                                name)))

          ;; Determine completion from the summary data
          agent-is-complete? (boolean (or (:finish-time-millis summary-info)
                                          (:result summary-info)))]

      ;; Simplified response - always return same structure
      {:is-complete agent-is-complete?
       :nodes cleaned-nodes
       :summary summary-info
       :task-id agent-task-id
       :agent-id agent-id
       :root-invoke-id root-invoke-id
       :historical-graph historical-graph})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/execute-fork
  [{:keys [client invoke-pair changed-nodes]} uid]
  (let [[task-id agent-invoke-id] invoke-pair
        json-parsed-nodes (transform [MAP-VALS] #(j/read-value %) changed-nodes)
        rehydrated-nodes (common/from-ui-serializable json-parsed-nodes)
        ^AgentInvoke result (aor/agent-initiate-fork
                             client
                             (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                             rehydrated-nodes)]
    {:agent-invoke-id (:agentInvokeId (bean result))
     :task-id (:taskId (bean result))}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/provide-human-input
  [{:keys [client request response]} uid]
  (let [{:keys [agent-task-id agent-id node node-task-id invoke-id uuid prompt]} request
        req (aor-types/->NodeHumanInputRequest agent-task-id agent-id node node-task-id invoke-id prompt uuid)]
    (aor/provide-human-input client req response)
    {:ok true}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-graph
  [{:keys [client]} uid]
  (when client
    {:graph (foreign-invoke-query
             (:current-graph-query
              (aor-types/underlying-objects client)))}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/set-metadata
  [{:keys [client invoke-id key value-str]} uid]
  (let [[task-id agent-id] (common/parse-url-pair invoke-id)
        invoke (aor-types/->AgentInvokeImpl task-id agent-id)]
    (let [parsed-value (j/read-value value-str)]
      (aor/set-metadata! client
                         invoke
                         key
                         (if (= java.lang.Integer (class parsed-value))
                           (long parsed-value)
                           parsed-value))
      {:success true})))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/remove-metadata
  [{:keys [client invoke-id key]} uid]
  (let [[task-id agent-id] (common/parse-url-pair invoke-id)
        invoke (aor-types/->AgentInvokeImpl task-id agent-id)]
    (aor/remove-metadata! client invoke key)
    {:success true}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-node-stats
  [{:keys [client agent-name granularity time-range-hours]} uid]
  (when client
    (let [client-objects (aor-types/underlying-objects client)
          telemetry-pstate (:telemetry-pstate client-objects)

          ;; Default to last 24 hours if not specified
          hours (or time-range-hours 24)
          now (System/currentTimeMillis)
          start-time (- now (* hours 60 60 1000))

          ;; Default to hour granularity
          gran (or granularity po/HOUR-GRANULARITY)

          ;; Query telemetry for node latencies
          telemetry-data (ana/select-telemetry
                          telemetry-pstate
                          agent-name
                          gran
                          [:agent :node-latencies]
                          start-time
                          now
                          [:mean :count :min :max 0.99]
                          nil)]

      ;; Aggregate stats across all time buckets for each node
      {:node-stats
       (reduce
        (fn [acc [bucket-time bucket-data]]
          (reduce
           (fn [acc [node-name stats]]
             (let [existing (get acc node-name)
                   ;; Aggregate across buckets
                   new-count (+ (or (:count existing) 0) (:count stats))
                   new-total-latency (+ (or (:total-latency existing) 0) (* (:mean stats) (:count stats)))
                   new-min (if existing (min (:min existing) (:min stats)) (:min stats))
                   new-max (if existing (max (:max existing) (:max stats)) (:max stats))
                   new-p99 (if existing (max (:p99 existing) (get stats 0.99)) (get stats 0.99))]
               (assoc acc node-name
                      {:count new-count
                       :total-latency new-total-latency
                       :mean (if (pos? new-count) (/ new-total-latency new-count) 0)
                       :min new-min
                       :max new-max
                       :p99 new-p99})))
           acc
           bucket-data))
        {}
        telemetry-data)
       :time-range-hours hours
       :granularity gran})))
