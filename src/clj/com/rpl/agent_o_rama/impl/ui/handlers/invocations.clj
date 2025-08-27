(ns com.rpl.agent-o-rama.impl.ui.handlers.invocations
  (:use [com.rpl.rama] [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common]
   [jsonista.core :as j])
  (:import [com.rpl.agentorama AgentInvoke]))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-page
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name pagination]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)
           pages (if (empty? pagination) nil pagination)]
       (foreign-invoke-query
        (:invokes-page-query (common/objects decoded-module-id decoded-agent-name))
        10 pages)))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/run-agent
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name args]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)]
       (when-not (vector? args)
         (throw (ex-info "must be a json list of args" {:bad-args args})))
       (let [^AgentInvoke inv (apply aor/agent-initiate (common/get-client decoded-module-id decoded-agent-name) args)]
         {:task-id (.getTaskId inv)
          :invoke-id (.getAgentInvokeId inv)})))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-graph-page
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name invoke-id leaves initial?]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)
           client-objects (common/objects decoded-module-id decoded-agent-name)
           tracing-query (:tracing-query client-objects)
           root-pstate (:root-pstate client-objects)
           history-pstate (:graph-history-pstate client-objects)
           [agent-task-id agent-id] (common/parse-url-pair invoke-id)
           is-initial-load? (boolean initial?)
           summary-info (merge
                         {:forks (foreign-select-one [(keypath agent-id) :forks (sorted-set-range-to-end 100)]
                                                     root-pstate
                                                     {:pkey agent-task-id})}
                         (foreign-select-one [(keypath agent-id) (submap [:result :start-time-millis :finish-time-millis :graph-version :retry-num :fork-of :exception-summaries])]
                                             root-pstate
                                             {:pkey agent-task-id}))
           root-invoke-id (when is-initial-load? (foreign-select-one [(keypath agent-id) :root-invoke-id] root-pstate {:pkey agent-task-id}))
           historical-graph (when is-initial-load? (when-let [graph-version (:graph-version summary-info)] (foreign-select-one [(keypath graph-version)] history-pstate {:pkey agent-task-id})))
           start-pairs (if is-initial-load? [[agent-task-id root-invoke-id]] leaves)
           page-limit (if is-initial-load? 1000 100)
           dynamic-trace (when (seq start-pairs) (foreign-invoke-query tracing-query agent-task-id start-pairs page-limit))
           cleaned-nodes (when-let [m (:invokes-map dynamic-trace)] (-> m common/remove-implicit-nodes))
           next-leaves (:next-task-invoke-pairs dynamic-trace)
           root-status (foreign-select-one [(keypath agent-id) (submap [:result :finish-time-millis])] root-pstate {:pkey agent-task-id})
           agent-is-complete? (boolean (or (:finish-time-millis root-status) (:result root-status)))]
       (cond-> {:is-complete agent-is-complete?}
         (seq cleaned-nodes) (assoc :nodes cleaned-nodes)
         (seq next-leaves) (assoc :next-leaves next-leaves)
         true (assoc :summary summary-info :task-id agent-task-id :agent-id agent-id)
         is-initial-load? (assoc :root-invoke-id root-invoke-id :historical-graph historical-graph))))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/execute-fork
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name invoke-id changed-nodes]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)
           [task-id agent-invoke-id] (common/parse-url-pair invoke-id)
           json-parsed-nodes (transform [MAP-VALS] #(j/read-value %) changed-nodes)
           rehydrated-nodes (common/from-ui-serializable json-parsed-nodes)
           ^AgentInvoke result (aor/agent-initiate-fork
                                (common/get-client decoded-module-id decoded-agent-name)
                                (aor-types/->AgentInvokeImpl task-id agent-invoke-id)
                                rehydrated-nodes)]
       {:agent-invoke-id (:agentInvokeId (bean result))
        :task-id (:taskId (bean result))}))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/provide-human-input
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name request response]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)
           {:keys [agent-task-id agent-id node node-task-id invoke-id uuid prompt]} request
           req (aor-types/->NodeHumanInputRequest agent-task-id agent-id node node-task-id invoke-id prompt uuid)]
       (aor/provide-human-input (common/get-client decoded-module-id decoded-agent-name) req response)
       {:ok true}))
   ev-msg))

(defmethod com.rpl.agent-o-rama.impl.ui.sente/-event-msg-handler :invocations/get-graph
  [ev-msg]
  (common/handle-api-call
   (fn [{:keys [module-id agent-name]} uid]
     (let [decoded-module-id (common/url-decode module-id)
           decoded-agent-name (common/url-decode agent-name)]
       {:graph (foreign-invoke-query
                (:current-graph-query
                 (common/objects decoded-module-id decoded-agent-name)))}))
   ev-msg))
