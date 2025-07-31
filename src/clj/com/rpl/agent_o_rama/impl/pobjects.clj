(ns com.rpl.agent-o-rama.impl.pobjects
  (:use [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama.impl.types])
  (:import
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    AgentNodeEmit
    AgentResult
    AggInput
    ForkContext
    NestedOpInfo
    HistoricalAgentGraphInfo
    Node
    NodeAgg
    NodeAggStart
    StreamingChunk]))

(defn agents-store-info-name
  []
  "*_agents-store-info")

(defn agent-node-executor-name
  []
  "*_agent-node-executor")

(defn agent-declared-objects-name
  []
  "*_agent-declared-objects")

(defn agent-pstate-write-depot-name
  []
  RamaClientsTaskGlobal/AGENT_PSTATE_WRITE_DEPOT)

(defn agent-depot-name
  [name]
  (RamaClientsTaskGlobal/agentDepotName name))

(defn agent-streaming-depot-name
  [name]
  (RamaClientsTaskGlobal/agentStreamingDepotName name))

(defn agent-config-depot-name
  [name]
  (str "*_agent-config-depot-" name))

(defn agent-check-tick-depot-name
  [name]
  (str "*_agent-check-tick-depot-" name))

(defn agent-failures-depot-name
  [name]
  (str "*_agent-failures-depot-" name))

(defn agents-clients-name
  []
  "*_agents-clients")

(defn agent-graph-task-global-name
  [agent-name]
  (str "*_agent-graph-" agent-name))

(defn agent-id-gen-task-global-name
  [name]
  (str "$$_agent-id-gen-" name))

(defn agent-root-task-global-name
  [agent-name]
  (str "$$_agent-root-" agent-name))

(def AGENT-INVOKE-PSTATE-SCHEMA
  {Long
   (fixed-keys-schema
    {:root-invoke-id     Long
     :invoke-args        [Object]
     :graph-version      Long
     :result             AgentResult
     :ack-val            Long
     :start-time-millis  Long
     :finish-time-millis Long
     :last-progress-time-millis Long
     :retry-num          Long
     :fork-of            (fixed-keys-schema
                          {:parent-agent-id Long
                           :fork-context    ForkContext})
     :forks              (set-schema Long {:subindex? true}) ; agent ids
    })})

(defn agent-active-invokes-task-global-name
  [agent-name]
  (str "$$_agent-active-invokes-" agent-name))

(def AGENT-ACTIVE-INVOKES-PSTATE-SCHEMA
  {Long Boolean})

(defn agent-valid-invokes-task-global-name
  [agent-name]
  (str "$$_agent-valid-invokes-" agent-name))

(def AGENT-VALID-INVOKES-PSTATE-SCHEMA
  ;; [agent-task-id agent-id] -> valid retry-num
  {java.util.List Long})

(defn agent-gc-invokes-task-global-name
  [agent-name]
  (str "$$_agent-gc-invokes-" agent-name))

(def AGENT-GC-ROOT-INVOKES-PSTATE-SCHEMA
  {Long Object})

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
    {:agent-id            Long
     :agent-task-id       Long
     :node                String
     :nested-ops          [NestedOpInfo]
     :emits               [AgentNodeEmit]
     :result              AgentResult
     :start-time-millis   Long
     :finish-time-millis  Long

     :agg-invoke-id       Long

     ;; input to regular node
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
    })})

(defn graph-history-task-global-name
  [agent-name]
  (str "$$_agent-graph-history-" agent-name))

(def GRAPH-HISTORY-PSTATE-SCHEMA
  {Long HistoricalAgentGraphInfo})

(defn pending-retries-task-global-name
  [agent-name]
  (str "$$_agent-pending-retries-" agent-name))

(def PENDING-RETRIES-PSTATE-SCHEMA
  ;; [agent-task-id agent-id retry-num]
  {java.util.List Object})

(defn agent-config-task-global-name
  [agent-name]
  (str "$$_agent-config-" agent-name))

(def AGENT-CONFIG-PSTATE-SCHEMA
  java.util.Map)

;; Task global fetch helpers

(defn agent-node-executor-task-global
  []
  (declared-object-task-global (agent-node-executor-name)))

(defn agent-store-info-task-global
  []
  (declared-object-task-global (agents-store-info-name)))

(defn agent-declared-objects-task-global
  ^AgentDeclaredObjectsTaskGlobal []
  (declared-object-task-global (agent-declared-objects-name)))

(defn agent-depot-task-global
  [name]
  (this-module-pobject-task-global (agent-depot-name name)))

(defn agent-failures-depot-task-global
  [name]
  (this-module-pobject-task-global (agent-failures-depot-name name)))

(defn agents-clients-task-global
  []
  (declared-object-task-global (agents-clients-name)))

(defn agent-graph-task-global
  [name]
  (declared-object-task-global (agent-graph-task-global-name name)))

(defn agent-id-gen-task-global
  [name]
  (this-module-pobject-task-global (agent-id-gen-task-global-name name)))

(defn agent-root-task-global
  [name]
  (this-module-pobject-task-global (agent-root-task-global-name name)))

(defn agent-active-invokes-task-global
  [name]
  (this-module-pobject-task-global (agent-active-invokes-task-global-name
                                    name)))

(defn agent-valid-invokes-task-global
  [name]
  (this-module-pobject-task-global (agent-valid-invokes-task-global-name name)))

(defn agent-gc-invokes-task-global
  [name]
  (this-module-pobject-task-global (agent-gc-invokes-task-global-name name)))

(defn agent-streaming-results-task-global
  [name]
  (this-module-pobject-task-global (agent-streaming-results-task-global-name
                                    name)))

(defn agent-node-task-global
  [name]
  (this-module-pobject-task-global (agent-node-task-global-name name)))

(defn graph-history-task-global
  [name]
  (this-module-pobject-task-global (graph-history-task-global-name name)))

(defn agent-config-task-global
  [name]
  (this-module-pobject-task-global (agent-config-task-global-name name)))
