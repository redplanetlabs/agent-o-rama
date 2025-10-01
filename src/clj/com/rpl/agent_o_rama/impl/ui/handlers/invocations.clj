(ns com.rpl.agent-o-rama.impl.ui.handlers.invocations
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.analytics :as analytics]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [jsonista.core :as j])
  (:import [com.rpl.agentorama AgentInvoke]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-page
  [{:keys [client pagination]} uid]
  (let [pages (if (empty? pagination) nil pagination)]
    (foreign-invoke-query
     (:invokes-page-query (aor-types/underlying-objects client))
     10 pages)))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/run-agent
  [{:keys [client args]} uid]
  (when-not (vector? args)
    (throw (ex-info "must be a json list of args" {:bad-args args})))
  (let [^AgentInvoke inv (apply aor/agent-initiate client args)]
    {:task-id (.getTaskId inv)
     :invoke-id (.getAgentInvokeId inv)}))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-graph-page
  [{:keys [client invoke-pair leaves initial?]} _uid] ; Renamed uid to _uid as it's unused
  (let [;; Correctly get all underlying objects from the agent-specific client
        client-objects (aor-types/underlying-objects client)
        tracing-query (:tracing-query client-objects)
        root-pstate (:root-pstate client-objects) ; <-- FIX: Use client-objects
        history-pstate (:graph-history-pstate client-objects) ; <-- FIX: Use client-objects

        [agent-task-id agent-id] invoke-pair
        is-initial-load? (boolean initial?)

        ;; This query is now safe because root-pstate is correctly sourced
        summary-info-raw (foreign-select-one
                          [(keypath agent-id)
                           (submap [:result :start-time-millis :finish-time-millis :graph-version :retry-num :fork-of :exception-summaries :invoke-args :stats :feedback])]
                          root-pstate
                          {:pkey agent-task-id})

        ;; Add aggregated stats to the stats object
        summary-info (merge
                      {:forks (foreign-select-one [(keypath agent-id) :forks (sorted-set-range-to-end 100)]
                                                  root-pstate
                                                  {:pkey agent-task-id})}
                      summary-info-raw
                      (when-let [stats (:stats summary-info-raw)]
                        {:stats (merge {:aggregated-stats (analytics/aggregated-basic-stats stats)} stats)}))

        root-invoke-id (when is-initial-load?
                         (foreign-select-one [(keypath agent-id) :root-invoke-id] root-pstate {:pkey agent-task-id}))

        historical-graph (when is-initial-load?
                           (when-let [graph-version (:graph-version summary-info)]
                             (foreign-select-one [(keypath graph-version)] history-pstate {:pkey agent-task-id})))

        start-pairs (if is-initial-load?
                      [[agent-task-id root-invoke-id]]
                      leaves)

        page-limit (if is-initial-load? 1000 100)

        dynamic-trace (when (seq start-pairs)
                        (foreign-invoke-query tracing-query agent-task-id start-pairs page-limit))

        cleaned-nodes (when-let [m (:invokes-map dynamic-trace)]
                        (-> m common/remove-implicit-nodes))

        next-leaves (:next-task-invoke-pairs dynamic-trace)

        ;; Determine completion from the summary data we already fetched
        ;; to avoid race condition between two separate queries
        agent-is-complete? (boolean (or (:finish-time-millis summary-info) (:result summary-info)))]

    (cond-> {:is-complete agent-is-complete?}
      (seq cleaned-nodes) (assoc :nodes cleaned-nodes)
      (seq next-leaves) (assoc :next-leaves next-leaves)
      true (assoc :summary summary-info :task-id agent-task-id :agent-id agent-id)
      is-initial-load? (assoc :root-invoke-id root-invoke-id :historical-graph historical-graph))))

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
  {:graph (foreign-invoke-query
           (:current-graph-query
            (aor-types/underlying-objects client)))})
