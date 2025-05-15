(ns com.rpl.agent-o-rama.impl.pobjects
  (:use [com.rpl.rama])
  (:import
   [com.rpl.agent_o_rama.impl.types
    AgentNodeEmit
    AgentResult
    AggInput
    AsyncOpInfo
    HistoricalAgentGraphInfo
    Node
    NodeAgg
    NodeAggStart]))


(defn agents-store-info-name
  []
  "*_agents-store-info")

(defn agent-graph-task-global-name
  [agent-name]
  (str "*_agent-graph-" agent-name))

(defn agent-depot-task-global-name
  [agent-name]
  (str "*_agent-depot-" agent-name))

(defn agent-invoke-task-global-name
  [agent-name]
  (str "$$_agent-invoke-" agent-name))

(def AGENT-INVOKE-PSTATE-SCHEMA
  {Long
   (fixed-keys-schema
    {:root-invoke-id Long
     :invoke-args    [Object]
     :graph-version  Long
     ;; TODO: <<<<<>>>>> if no result is ever specified, should error instead
     ;; of hanging
     ;; - will need top-level acking that puts error here if it didn't
     ;; complete
     :result         AgentResult})})

(defn agent-streaming-results-task-global-name
  [agent-name]
  (str "$$_agent-streaming-" agent-name))

(def AGENT-STREAMING-PSTATE-SCHEMA
  {Long ; agent ID
   (map-schema
    String ; node name
    {String ; async invoke name
     (map-schema
      Long ; invoke-id
      (vector-schema Object {:subindex? true})
      {:subindex? true})}
    {:subindex? true})})


(defn agent-node-task-global-name
  [agent-name]
  (str "$$_agent-node-" agent-name))

(def AGENT-NODE-PSTATE-SCHEMA
  {Long ; invoke-id
   (fixed-keys-schema
    {:graph-id           Long
     :graph-task-id      Long
     :node               String
     :async-ops          [AsyncOpInfo]
     :emits              [AgentNodeEmit]
     :result             AgentResult
     :start-time-millis  Long
     :finish-time-millis Long
     :agg-invoke-id      Long

     ;; regular node state
     :input              [Object]

     ;; agg state
     :agg-inputs         (vector-schema AggInput {:subindex? true})
     :agg-start-res      Object
     :agg-state          Object
     :agg-ack-val        Long
     :agg-start-invoke-id Long
     :agg-finished?      Boolean

     ;; TODO: <<<<>>>>
     ;;   - what other stats does langsmith track?
    })})

(defn graph-history-task-global-name
  [agent-name]
  (str "$$_agent-graph-history-" agent-name))

(def GRAPH-HISTORY-PSTATE-SCHEMA
  {Long HistoricalAgentGraphInfo})
