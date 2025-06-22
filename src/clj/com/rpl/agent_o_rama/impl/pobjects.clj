(ns com.rpl.agent-o-rama.impl.pobjects
  (:use [com.rpl.rama])
  (:import
   [com.rpl.agentorama
    StreamingChunk]
   [com.rpl.agentorama.impl
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AgentNodeEmit
    AgentResult
    AggInput
    NestedOpInfo
    HistoricalAgentGraphInfo
    Node
    NodeAgg
    NodeAggStart]))


(defn agents-store-info-name
  []
  "*_agents-store-info")

(defn agents-virtual-threads-name
  []
  "*_agents-virtual-threads")

(defn agent-pstate-write-depot-name
  []
  RamaClientsTaskGlobal/AGENT_PSTATE_WRITE_DEPOT)

(defn agent-depot-name
  [name]
  (RamaClientsTaskGlobal/agentDepotName name))

(defn agent-streaming-depot-name
  [name]
  (RamaClientsTaskGlobal/agentStreamingDepotName name))

(defn agents-clients-name
  []
  "*_agents-clients")

(defn agent-graph-task-global-name
  [agent-name]
  (str "*_agent-graph-" agent-name))

(defn agent-invoke-task-global-name
  [agent-name]
  (str "$$_agent-invoke-" agent-name))

(def AGENT-INVOKE-PSTATE-SCHEMA
  {Long
   (fixed-keys-schema
    {:root-invoke-id Long
     :invoke-args    [Object]
     :graph-version  Long
     :result         AgentResult
     :ack-val        Long
     :retry-num      Long})})

(defn agent-valid-invokes-task-global-name
  [agent-name]
  (str "$$agent-valid-invokes-" agent-name))

(def AGENT-VALID-INVOKES-PSTATE-SCHEMA
  ;; root-invoke-id -> valid retry-num
  {Long Long})

(defn agent-streaming-results-task-global-name
  [agent-name]
  (str "$$_agent-streaming-" agent-name))

(def AGENT-STREAMING-PSTATE-SCHEMA
  {Long ; agent ID
   (map-schema
    String           ; node name
    (fixed-keys-schema
     {:all     (vector-schema StreamingChunk {:subindex? true})
      :invokes (map-schema
                Long ; invoke-id
                Long ; index
                {:subindex? true})})
    {:subindex? true})})


(defn agent-node-task-global-name
  [agent-name]
  (str "$$_agent-node-" agent-name))

(def AGENT-NODE-PSTATE-SCHEMA
  {Long ; invoke-id
   (fixed-keys-schema
    {:graph-id            Long
     :graph-task-id       Long
     :node                String
     :nested-ops          [NestedOpInfo]
     :emits               [AgentNodeEmit]
     :result              AgentResult
     :start-time-millis   Long
     :finish-time-millis  Long
     :agg-invoke-id       Long

     ;; regular node state
     :input               [Object]

     ;; start agg node
     :started-agg?        Boolean

     ;; invoke of agg node (to make tracing easier)
     :invoked-agg-invoke-id Long

     ;; agg state
     :agg-inputs          (vector-schema AggInput {:subindex? true})
     :agg-start-res       Object
     :agg-state           Object
     :agg-ack-val         Long
     :agg-start-invoke-id Long
     :agg-finished?       Boolean

     ;; TODO: <<<<>>>>
     ;;   - what other stats does langsmith track?
    })})

(defn graph-history-task-global-name
  [agent-name]
  (str "$$_agent-graph-history-" agent-name))

(def GRAPH-HISTORY-PSTATE-SCHEMA
  {Long HistoricalAgentGraphInfo})
