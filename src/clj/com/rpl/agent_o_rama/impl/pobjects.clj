(ns com.rpl.agent-o-rama.impl.pobjects
  (:use [com.rpl.rama])
  (:require
   [com.rpl.agent-o-rama.impl.types])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [com.rpl.agentorama.impl
    AgentDeclaredObjectsTaskGlobal
    AgentNodeExecutorTaskGlobal
    RamaClientsTaskGlobal]
   [com.rpl.agentorama.source
    InfoSource]
   [com.rpl.agent_o_rama.impl.types
    ActionLog
    AgentInvokeStatsImpl
    AgentNodeEmit
    AgentResult
    AggInput
    EvalNumberStats
    ExceptionSummary
    ExperimentInputSelector
    FeedbackImpl
    ForkContext
    NestedOpInfoImpl
    NodeHumanInputRequest
    HistoricalAgentGraphInfo
    Node
    NodeAgg
    NodeAggStart
    StartExperiment
    StreamingChunk]
   [java.util
    UUID]
   [rpl.rama.distributed.stats.number_stats
    NumberStats]))

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

(defn agent-edit-depot-name
  []
  "*_agent-edit-depot")

(defn agent-depot-name
  [name]
  (RamaClientsTaskGlobal/agentDepotName name))

(defn agent-streaming-depot-name
  [name]
  (RamaClientsTaskGlobal/agentStreamingDepotName name))

(defn agent-human-depot-name
  [name]
  (RamaClientsTaskGlobal/agentHumanDepotName name))

(defn agent-config-depot-name
  [name]
  (str "*_agent-config-depot-" name))

(defn agent-gc-tick-depot-name
  [name]
  (str "*_agent-gc-tick-depot-" name))

(defn agent-check-tick-depot-name
  [name]
  (str "*_agent-check-tick-depot-" name))

(defn agent-failures-depot-name
  [name]
  (str "*_agent-failures-depot-" name))

(defn agent-gc-valid-invokes-depot-name
  [name]
  (str "*_agent-gc-valid-invokes-depot-" name))

(defn datasets-depot-name
  []
  "*_agent-datasets-depot")

(defn global-actions-depot-name
  []
  "*_agent-global-actions-depot")

(defn agents-clients-name
  []
  "*_agent-clients")

(defn agent-root-task-global-name
  [agent-name]
  (str "$$_agent-root-" agent-name))

(defn agent-analytics-tick-depot-name
  []
  (str "*_agent-analytics-tick-depot"))

(def FEEDBACK-SCHEMA
  (fixed-keys-schema
   {:actions {String Object}
    :results [FeedbackImpl]}))

(def AGENT-ROOT-PSTATE-SCHEMA
  {UUID
   (fixed-keys-schema
    {:root-invoke-id UUID
     :invoke-args [Object]
     :graph-version Long
     :result AgentResult
     :exception-summaries [ExceptionSummary]
     :ack-val Long
     :start-time-millis Long
     :finish-time-millis Long
     :last-progress-time-millis Long
     :retry-num Long
     :metadata {String Object}
     :source InfoSource
     :stats AgentInvokeStatsImpl
     :feedback FEEDBACK-SCHEMA
     :human-requests (set-schema NodeHumanInputRequest {:subindex? true})
     :fork-of (fixed-keys-schema
               {:parent-agent-id UUID
                :fork-context    ForkContext})
     :forks (set-schema UUID {:subindex? true}) ; agent ids
     :first-token-time-millis Long
     :streaming (map-schema
                 String           ; node name
                 (fixed-keys-schema
                  {:all     (vector-schema StreamingChunk {:subindex? true})
                   :invokes (map-schema
                             UUID ; invoke-id
                             Long ; index
                             {:subindex? true})})
                 {:subindex? true})
    })})

(defn agent-root-count-task-global-name
  [agent-name]
  (str "$$_agent-root-count-" agent-name))

(defn agent-mb-shared-task-global-name
  [name]
  (str "$$_agent-mb-shared-" name))

(def AGENT-MB-SHARED-PSTATE-SCHEMA
  (fixed-keys-schema
   {:valid-invokes   (map-schema java.util.List Long {:subindex? true})
    :pending-retries (set-schema java.util.List {:subindex? true})}
  ))

(defn agent-stream-shared-task-global-name
  [agent-name]
  (str "$$_agent-stream-shared-" agent-name))

(def AGENT-STREAM-SHARED-PSTATE-SCHEMA
  (fixed-keys-schema
   {:history         (map-schema Long HistoricalAgentGraphInfo {:subindex? true})
    :gc-root-invokes (map-schema UUID Object {:subindex? true})
    :active-invokes  (set-schema UUID {:subindex? true})
    :metadata        (map-schema String ; metadata key
                                 (fixed-keys-schema
                                  {:examples #{Object}}) ; example values
                                 {:subindex? true})
   }))

(defn agent-node-task-global-name
  [agent-name]
  (str "$$_agent-node-" agent-name))

(def AGENT-NODE-PSTATE-SCHEMA
  {UUID ; invoke-id
   (fixed-keys-schema
    {:agent-id            UUID
     :agent-task-id       Long
     :node                String
     :nested-ops          [NestedOpInfoImpl]
     :emits               [AgentNodeEmit]
     :result              AgentResult
     :start-time-millis   Long
     :finish-time-millis  Long
     :exceptions          [String] ; throwable strs
     :feedback            FEEDBACK-SCHEMA
     :metadata            {String Object}
     :source              InfoSource

     :agg-invoke-id       UUID

     ;; input to regular node
     :input               [Object]

     ;; start agg node
     :started-agg?        Boolean

     ;; invoke of agg node (to make tracing easier)
     :invoked-agg-invoke-id UUID

     ;; agg state
     :agg-inputs          (vector-schema AggInput {:subindex? true})
     :agg-start-res       Object
     :agg-state           Object
     :agg-ack-val         Long
     :agg-start-invoke-id UUID
     :agg-finished?       Boolean
    })})

(defn action-log-task-global-name
  []
  (str "$$_agent-action-log"))

(def ACTION-LOG-PSTATE-SCHEMA
  {String ; agent-name
   (map-schema
    String ; rule-name
    (map-schema UUID ActionLog {:subindex? true})
    {:subindex? true})})

(defn agent-config-task-global-name
  [agent-name]
  (str "$$_agent-config-" agent-name))

(defn agent-global-config-task-global-name
  []
  (str "$$_agent-global-config"))

(def AGENT-CONFIG-PSTATE-SCHEMA
  java.util.Map)

(defn datasets-task-global-name
  []
  "$$_aor-datasets")

(def DATASETS-PSTATE-SCHEMA
  {UUID ; dataset-id
   (fixed-keys-schema
    {:props       (fixed-keys-schema
                   {;; only set if local dataset
                    :name               String
                    :description        String
                    :input-json-schema  String
                    :output-json-schema String

                    ;; only set if remote dataset
                    :cluster-conductor-host String
                    :cluster-conductor-port Long
                    :module-name        String

                    ;; set for both
                    :created-at         Long
                    :modified-at        Long})
     :snapshots
     (map-schema
      String ; nil for latest
      (map-schema
       UUID ; example ID
       (fixed-keys-schema
        {:input            Object
         :reference-output Object
         :tags             #{String}
         :source           InfoSource
         :created-at       Long
         :modified-at      Long
        })
       {:subindex? true})
      {:subindex? true})

     :experiments
     (map-schema
      UUID
      (fixed-keys-schema
       {:experiment-info       StartExperiment
        :experiment-invoke     AgentInvoke
        :start-time-millis     Long
        :finish-time-millis    Long
        :results               (map-schema
                                Long ; result ID
                                ;; - agent invokes/results are keyed by their index in experiment
                                ;; targets
                                ;; - non-comparative experiment will have single one keyed at 0
                                (fixed-keys-schema
                                 {:example-id      UUID
                                  :agent-initiates {Long (fixed-keys-schema
                                                          {:agent-name   String
                                                           :agent-invoke AgentInvoke})}
                                  :agent-results   {Long (fixed-keys-schema
                                                          {:result             AgentResult
                                                           :start-time-millis  Long
                                                           :finish-time-millis Long
                                                           :input-token-count  Long
                                                           :output-token-count Long
                                                           :total-token-count  Long
                                                          })}
                                  :eval-initiates  {String AgentInvoke}
                                  :evals           {String {String Object}} ; eval-name->eval-key->result
                                  :eval-failures   {String String}
                                 })
                                {:subindex? true})
        :summary-evals         {String {String Object}}
        :summary-eval-failures {String String}
        :eval-number-stats     {String {String EvalNumberStats}}
        :latency-number-stats  EvalNumberStats
        :input-token-number-stats EvalNumberStats
        :output-token-number-stats EvalNumberStats
        :total-token-number-stats EvalNumberStats
       })
      {:subindex? true})
    })})


(defn agent-telemetry-task-global-name
  [agent-name]
  (str "$$_aor-telemetry-" agent-name))


(def MINUTE-GRANULARITY 60)
(def HOUR-GRANULARITY (* 60 MINUTE-GRANULARITY))
(def DAY-GRANULARITY (* 24 HOUR-GRANULARITY))
(def THIRTY-DAY-GRANULARITY (* 30 DAY-GRANULARITY))

(def GRANULARITIES
  [MINUTE-GRANULARITY
   HOUR-GRANULARITY
   DAY-GRANULARITY
   THIRTY-DAY-GRANULARITY])

(def DEFAULT-CATEGORY "_aor/default")

(defn- telemetry-schema
  [leaf-schema]
  {Long  ; granularity as seconds (60 for minute, 3600 for hour, etc.)
   (map-schema
    java.util.List ; metric ID
    (map-schema
     Long  ; bucket
     (fixed-keys-schema
      {:overall leaf-schema
       :by-meta (map-schema
                 String ; metadata key
                 {Object ; metadata value
                  leaf-schema}
                 {:subindex? true})
      })
     {:subindex? true})
    {:subindex? true})})

(def AGENT-TELEMETRY-PSTATE-SCHEMA
  (telemetry-schema {String ; category
                     NumberStats}))

(defn evaluators-task-global-name
  []
  "$$_aor-evaluators")

(def EVALUATORS-PSTATE-SCHEMA
  {String (fixed-keys-schema
           {:builder-name     String
            :builder-params   {String Object}
            :description      String
            :input-json-path  String
            :output-json-path String
            :reference-output-json-path String
           })})

(defn agent-rules-task-global-name
  [agent-name]
  (str "$$_agent-rules-" agent-name))

;; rule-name -> {:definition AddRule}
(def AGENT-RULES-PSTATE-SCHEMA
  java.util.Map)

(defn agent-rule-cursors-task-global-name
  [agent-name]
  (str "$$_agent-rule-cursors-" agent-name))

;; rule-name -> task-id -> UUID
(def AGENT-RULE-CURSORS-PSTATE-SCHEMA
  java.util.Map)

(defn agent-metric-cursors-task-global-name
  [agent-name]
  (str "$$_agent-metric-cursors-" agent-name))

;; metric-name -> UUID
(def AGENT-METRIC-CURSORS-PSTATE-SCHEMA
  java.util.Map)

;; Task global fetch helpers

(defn agent-node-executor-task-global
  ^AgentNodeExecutorTaskGlobal []
  (declared-object-task-global (agent-node-executor-name)))

(defn agent-store-info-task-global
  []
  (declared-object-task-global (agents-store-info-name)))

(defn agent-declared-objects-task-global
  ^AgentDeclaredObjectsTaskGlobal []
  (declared-object-task-global (agent-declared-objects-name)))

(defn agent-edit-depot-task-global
  []
  (this-module-pobject-task-global (agent-edit-depot-name)))

(defn agent-depot-task-global
  [name]
  (this-module-pobject-task-global (agent-depot-name name)))

(defn agent-failures-depot-task-global
  [name]
  (this-module-pobject-task-global (agent-failures-depot-name name)))

(defn agent-gc-valid-invokes-depot-task-global
  [name]
  (this-module-pobject-task-global (agent-gc-valid-invokes-depot-name name)))

(defn datasets-depot-task-global
  [name]
  (this-module-pobject-task-global (datasets-depot-name)))

(defn agents-clients-task-global
  []
  (declared-object-task-global (agents-clients-name)))

(defn agent-graph-task-global
  [name]
  (-> (agent-declared-objects-task-global)
      .getAgentGraphs
      (get name)))

(defn agent-root-task-global
  [name]
  (this-module-pobject-task-global (agent-root-task-global-name name)))

(defn agent-root-count-task-global
  [name]
  (this-module-pobject-task-global (agent-root-count-task-global-name name)))

(defn agent-mb-shared-task-global
  [name]
  (this-module-pobject-task-global (agent-mb-shared-task-global-name name)))

(defn agent-stream-shared-task-global
  [name]
  (this-module-pobject-task-global (agent-stream-shared-task-global-name name)))

(defn agent-node-task-global
  [name]
  (this-module-pobject-task-global (agent-node-task-global-name name)))

(defn agent-config-task-global
  [name]
  (this-module-pobject-task-global (agent-config-task-global-name name)))

(defn agent-rules-task-global
  [name]
  (this-module-pobject-task-global (agent-rules-task-global-name name)))

(defn agent-rule-cursors-task-global
  [name]
  (this-module-pobject-task-global (agent-rule-cursors-task-global-name name)))

(defn agent-metric-cursors-task-global
  [name]
  (this-module-pobject-task-global (agent-metric-cursors-task-global-name name)))

(defn agent-telemetry-task-global
  [name]
  (this-module-pobject-task-global (agent-telemetry-task-global-name name)))

(defn agent-global-config-task-global
  []
  (this-module-pobject-task-global (agent-global-config-task-global-name)))

(defn datasets-task-global
  []
  (this-module-pobject-task-global (datasets-task-global-name)))

(defn evaluators-task-global
  []
  (this-module-pobject-task-global (evaluators-task-global-name)))

(defn action-log-task-global
  []
  (this-module-pobject-task-global (action-log-task-global-name)))

(defn log-throttler
  []
  (AgentNodeExecutorTaskGlobal/getLogThrottler))

(defn agent-names-set
  []
  (-> (agent-declared-objects-task-global)
      .getAgentGraphs
      keys
      set))
