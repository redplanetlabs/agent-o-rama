(ns com.rpl.agent-o-rama.impl.ui.handlers.invocations
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
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
