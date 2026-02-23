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
  [{:keys [client pagination filters]} uid]
  (let [page-size 10
        scan-page-size 100
        pages (if (empty? pagination) nil pagination)]
    (when client ; this can be nil on restarts of the backend
      (foreign-invoke-query
       (:invokes-page-query (aor-types/underlying-objects client))
       page-size scan-page-size pages filters))))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-filter-options
  [{:keys [client manager agent-name]} uid]
  (let [graph-nodes (let [graph-res (foreign-invoke-query
                                     (:current-graph-query (aor-types/underlying-objects client)))]
                      (-> graph-res
                          :node-map
                          keys
                          sort
                          vec))
        human-metrics (let [search-human-metrics-query (:search-human-metrics-query
                                                        (aor-types/underlying-objects manager))
                            metric-res (foreign-invoke-query search-human-metrics-query {} 1000 nil)]
                        (->> (:items metric-res)
                             (map :name)
                             sort
                             vec))]
    {:nodes graph-nodes
     :feedback-metrics human-metrics}))

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
  (if-not client
    (throw (ex-info "No client available - module or agent may not be loaded" {:invoke-pair invoke-pair}))
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
                             ;; Add source-string to feedback results
                             (transform [:feedback :results ALL]
                                        (fn [feedback-result]
                                          (let [feedback-map (into {} feedback-result)
                                                source (:source feedback-map)]
                                            (if source
                                              (assoc feedback-map :source-string (aor-types/source-string source))
                                              feedback-map))))
                             ;; Convert feedback score keys to strings
                             (transform [:feedback :results ALL :scores MAP-KEYS] name)
                             ;; Convert feedback action keys to strings
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
                               ;; Convert feedback results to maps and add source-string
                               (transform
                                [MAP-VALS :feedback :results ALL]
                                (fn [feedback-result]
                                  (let [feedback-map (into {} feedback-result)
                                        source (:source feedback-map)]
                                    (if source
                                      (assoc feedback-map :source-string (aor-types/source-string source))
                                      feedback-map))))
                               ;; Convert score keys to strings for JSON
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
  (if client
    {:graph (foreign-invoke-query
             (:current-graph-query
              (aor-types/underlying-objects client)))}
    {:graph nil}))

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
  [{:keys [client agent-name granularity]} uid]
  (when client
    (let [client-objects (aor-types/underlying-objects client)
          telemetry-pstate (:telemetry-pstate client-objects)

          ;; Default to hour granularity if not specified
          gran-seconds (or granularity po/HOUR-GRANULARITY)

          ;; Calculate time window: look back 3 buckets from now
          now-millis (System/currentTimeMillis)
          bucket-size-millis (* gran-seconds 1000)
          lookback-millis (* 3 bucket-size-millis)
          start-time-millis (- now-millis lookback-millis)
          ;; sorted-map-range is exclusive of end, so add one bucket to include current bucket
          end-time-millis (+ now-millis bucket-size-millis)

          ;; Query telemetry for node latencies with all percentiles
          telemetry-data (ana/select-telemetry
                          telemetry-pstate
                          agent-name
                          gran-seconds
                          [:agent :node-latencies]
                          start-time-millis
                          end-time-millis
                          [:mean :count :min :max 0.25 0.5 0.75 0.9 0.99]
                          nil)]

      ;; Aggregate last 2 buckets to avoid data gaps at bucket transitions
      ;; E.g., at 10:01, hour bucket has only 1 min of data; previous bucket has full hour
      (let [current-bucket (quot now-millis bucket-size-millis)
            prev-bucket (dec current-bucket)

            ;; Get data for the 2 most recent bucket numbers (not just last 2 with data)
            current-bucket-data (get telemetry-data current-bucket)
            prev-bucket-data (get telemetry-data prev-bucket)

            ;; Merge stats from 2 buckets for each node
            merge-node-stats
            (fn [curr-stats prev-stats]
              (cond
                ;; Both buckets have data for this node - aggregate
                (and curr-stats prev-stats)
                (let [total-count (+ (:count curr-stats) (:count prev-stats))
                      total-latency (+ (* (:mean curr-stats) (:count curr-stats))
                                       (* (:mean prev-stats) (:count prev-stats)))]
                  {:count total-count
                   :mean (/ total-latency total-count)
                   :min (min (:min curr-stats) (:min prev-stats))
                   :max (max (:max curr-stats) (:max prev-stats))
                   0.5 (max (get curr-stats 0.5 0) (get prev-stats 0.5 0))
                   0.9 (max (get curr-stats 0.9 0) (get prev-stats 0.9 0))
                   0.99 (max (get curr-stats 0.99 0) (get prev-stats 0.99 0))})

                ;; Only one bucket has data - use it
                curr-stats curr-stats
                prev-stats prev-stats
                :else nil))

            ;; Get all unique node names
            all-nodes (set (concat (keys current-bucket-data) (keys prev-bucket-data)))

            ;; Merge stats for each node
            aggregated-stats
            (into {}
                  (keep (fn [node-name]
                          (when-let [merged (merge-node-stats
                                             (get current-bucket-data node-name)
                                             (get prev-bucket-data node-name))]
                            [node-name merged]))
                        all-nodes))]

        {:node-stats aggregated-stats
         :granularity gran-seconds}))))
