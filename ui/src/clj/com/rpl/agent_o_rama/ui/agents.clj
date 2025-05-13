(ns com.rpl.agent-o-rama.ui.agents)

(defn index [{:keys [parameters]}]
  {:status
   200
   
   :body
   [{:module-id "ModuleA" :agent-id "research"}
    {:module-id "ModuleA" :agent-id "support"}
    {:module-id "ModuleB" :agent-id "research"}]})

(defn get [{{:keys [module-id agent-id]} :path-params}]
  {:status
   200
   
   :body
   {:invokes ;;agent-invoke-pstate-<agent-id> 
    [
     ;; probably want to join/lookup more data about each root invoke from the
     ;; $$_agent_node-<agent-id>[root-invoke-id] pstate
     {:root-invoke-id 121
      :invoke-args ["CUSTOMER-123"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 122
      :invoke-args ["CUSTOMER-66"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 123
      :invoke-args ["CUSTOMER-456"]
      :graph-version 0
      :result {:success true} }
     {:root-invoke-id 124
      :invoke-args ["CUSTOMER-222"]
      :graph-version 0
      :result {:success true} }]}})

(defn invoke [{{:keys [module-id agent-id invoke-id]} :path-params}]
  {:status
   200
   
   :body
   {:nodes ;;$$_agent_node-<agent-id>, keyed by invoke-id 
    [
     {:graph-id 3 ;; ??
      :graph-task-id 3 ;; ??
      :node "start"
      :async-ops []
      :emits ["AgentNodeEmit"]
      :result "AgentResult"
      :start-time-millis  1747079542466
      :finish-time-millis 1747079543000
      :agg-invoke-id 3

      ;; regular node state
      :input ["CUSTOMER-356" "input 2"]

      ;; agg state
      :agg-inputs ["AGGINPUT1" "AGGINPUT2"]
      :agg-start-res "?"
      :agg-state "?"
      :agg-ack-val "?"
      :agg-start-invoke-id 3
      :agg-finished? false

      ;; TODO: <<<<>>>>
      ;;   - what other stats does langsmith track?
      }
     {:graph-id 3 ;; ??
      :graph-task-id 3 ;; ??
      :node "start"
      :async-ops []
      :emits ["AgentNodeEmit"]
      :result "AgentResult"
      :start-time-millis  1747079542466
      :finish-time-millis 1747079543000
      :agg-invoke-id 3

      ;; regular node state
      :input ["CUSTOMER-356" "input 2"]

      ;; agg state
      :agg-inputs ["AGGINPUT1" "AGGINPUT2"]
      :agg-start-res "?"
      :agg-state "?"
      :agg-ack-val "?"
      :agg-start-invoke-id 3
      :agg-finished? false

      ;; TODO: <<<<>>>>
      ;;   - what other stats does langsmith track?
      }]}})
