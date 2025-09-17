(ns com.rpl.agent-o-rama.impl.types
  (:use [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.serialize]
   [com.rpl.ramaspecter.defrecord-plus :as drp]
   [rpl.schema.core :as s])
  (:import
   [com.rpl.agentorama
    AgentComplete
    AgentInvoke
    ExampleRun
    HumanInputRequest
    ToolInfo]
   [com.rpl.agentorama.impl
    NippyMap]
   [com.rpl.rama.integration
    TaskGlobalObject]
   [dev.langchain4j.agent.tool
    ToolSpecification]
   [java.util
    UUID]
   [java.util.concurrent
    CompletableFuture]))


(def ^:dynamic OPERATION-SOURCE nil)

(def AGENTS-TOPOLOGY-NAME "_agents-topology")
(def AGENTS-MB-TOPOLOGY-NAME "_agents-mb-topology")

(def NODE-KW :node)
(def AGG-START-NODE-KW :agg-start-node)
(def AGG-NODE-KW :agg-node)

(defrecord Node [node-fn])
(defrecord NodeAggStart [node-fn agg-node-name])
(defrecord NodeAgg [init-fn update-fn node-fn])

(defn NodeAggStart? [o] (instance? NodeAggStart o))
(defn NodeAgg? [o] (instance? NodeAgg o))

(defn node->type-kw
  [node]
  (cond (instance? Node node) NODE-KW
        (instance? NodeAggStart node) AGG-START-NODE-KW
        (instance? NodeAgg node) AGG-NODE-KW
        :else (throw (h/ex-info "Unexpected node type" {:class (class node)}))))

;; TODO: use flexible serialization for these to ease updating the
;; library? or just some of them?


(defprotocol EvalTarget)

(drp/defrecord+ AgentInvokeImpl
  [task-id :- Long
   agent-invoke-id :- UUID]
  AgentInvoke
  (getTaskId [this] task-id)
  (getAgentInvokeId [this] agent-invoke-id)
  EvalTarget)

(drp/defrecord+ EvalNodeTarget
  [task-id :- Long
   invoke-id :- UUID]
  EvalTarget)

;; Sources

(definterface InfoSource
  (source_string []))

(defn source-string
  [^InfoSource i]
  (.source_string i))

(drp/defrecord+ HumanSource
  [name :- String]
  InfoSource
  (source_string [this] (str "human[" name "]")))

(drp/defrecord+ AiSource
  []
  InfoSource
  (source_string [this] "ai"))

(drp/defrecord+ ApiSource
  []
  InfoSource
  (source_string [this] "api"))

(drp/defrecord+ BulkUploadSource
  []
  InfoSource
  (source_string [this] "bulkUpload"))

(drp/defrecord+ ExperimentSource
  [dataset-id :- UUID
   experiment-id :- UUID]
  InfoSource
  (source_string [this] "experiment"))

(drp/defrecord+ AgentRunSource
  [module-name :- String
   agent-name :- String
   agent-invoke :- AgentInvokeImpl]
  InfoSource
  (source_string [this] (str "agent[" module-name "/" agent-name "]")))

;; Core types

(drp/defrecord+ AgentInitiate
  [args :- [s/Any]
   forced-agent-invoke-id :- (s/maybe UUID)
   source :- (s/maybe InfoSource)
  ])

(drp/defrecord+ AgentResult
  [val :- s/Any
   failure? :- Boolean])

(drp/defrecord+ AgentCompleteImpl
  [result :- s/Any]
  AgentComplete
  (getResult [this] val))


(drp/defrecord+ AgentNode
  [node :- (s/cond-pre Node NodeAggStart NodeAgg)
   output-nodes :- #{String}
   agg-context :- (s/maybe String)])

(drp/defrecord+ AgentGraph
  [node-map :- NippyMap ; {String AgentNode}
   start-node :- String
   update-mode :- (s/enum :continue :restart :drop)
   uuid :- String]
  TaskGlobalObject
  (prepareForTask [this task-id context])
  (close [this]))

(drp/defrecord+ StoreInfo
  [store-info :- {String clojure.lang.Keyword}
   ;; module-name -> pstate-name -> store-type
   mirror-store-info :- {String {String clojure.lang.Keyword}}]
  TaskGlobalObject
  (prepareForTask [this task-id context])
  (close [this]))

(drp/defrecord+ AggInput
  [invoke-id :- UUID
   args :- [s/Any]])

(drp/defrecord+ NestedOpInfo
  [start-time-millis :- Long
   finish-time-millis :- Long
   type :-
   (s/enum :store-write
           :store-read
           :db-write
           :db-read
           :model-call
           :tool-call
           :agent-call
           :human-input
           :other)
   ;; info for models contains token stats, input prompt, output, etc.
   info :- (s/maybe {String s/Any})])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- UUID
   fork-invoke-id :- (s/maybe UUID)
   target-task-id :- Long
   node-name :- String
   args :- [s/Any]
  ])

(drp/defrecord+ ForkContext
  [invoke-id->new-args :- {UUID [s/Any]}
   affected-aggs :- (s/maybe #{UUID}) ; agg-start-node invoke-ids
  ])

(drp/defrecord+ NodeComplete
  [task-id :- Long
   invoke-id :- UUID
   retry-num :- Long
   node-fn-res :- s/Any
   emits :- [AgentNodeEmit]
   result :- (s/maybe AgentResult)
   nested-ops :- [NestedOpInfo]
   finish-time-millis :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(drp/defrecord+ RetryNodeComplete
  [invoke-id :- UUID
   retry-num :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(drp/defrecord+ NodeFailure
  [task-id :- Long
   invoke-id :- UUID
   retry-num :- Long
   throwable-str :- String
   nested-ops :- [NestedOpInfo]
  ])

(drp/defrecord+ ExceptionSummary
  [throwable-str :- String
   node :- String
   invoke-id :- UUID])

(drp/defrecord+ AgentFailure
  [agent-task-id :- Long
   agent-id :- UUID
   retry-num :- Long])

(drp/defrecord+ RetryAgentInvoke
  [agent-task-id :- Long
   agent-id :- UUID
   expected-retry-num :- Long])

(drp/defrecord+ ForkAgentInvoke
  [agent-task-id :- Long
   agent-id :- UUID
   invoke-id->new-args :- {UUID [s/Any]}])

(drp/defrecord+ HistoricalAgentNodeInfo
  [node-type :- clojure.lang.Keyword ; :node, :agg-node, :agg-start-node
   output-nodes :- #{String}
   agg-context :- (s/maybe String)
  ])

(drp/defrecord+ HistoricalAgentGraphInfo
  [node-map :- {String HistoricalAgentNodeInfo}
   start-node :- String
   uuid :- String
  ])

(drp/defrecord+ NodeStreamingResult
  [agent-task-id :- Long
   agent-id :- UUID
   node :- String
   invoke-id :- UUID
   retry-num :- Long
   streaming-index :- Long
   value :- Object])

(drp/defrecord+ StreamingChunk
  [invoke-id :- UUID
   index :- Long
   chunk :- Object])

(drp/defrecord+ NodeHumanInputRequest
  [agent-task-id :- Long
   agent-id :- UUID
   node :- String
   node-task-id :- Long
   invoke-id :- UUID
   prompt :- String
   uuid :- String]
  HumanInputRequest
  (getNode [this] node)
  (getNodeInvokeId [this] invoke-id)
  (getPrompt [this] prompt))

(drp/defrecord+ HumanInput
  [request :- NodeHumanInputRequest
   response :- String])

(drp/defrecord+ NodeOp
  [invoke-id :- UUID
   fork-invoke-id :- (s/maybe UUID)
   fork-context :- (s/maybe ForkContext)
   next-node :- String
   args :- [s/Any]
   agg-invoke-id :- (s/maybe UUID)])

(drp/defrecord+ AggAckOp
  [agg-invoke-id :- UUID
   ack-val :- Long])

(drp/defrecord+ PStateWrite
  [agent-name :- String
   agent-task-id :- Long
   agent-id :- UUID
   retry-num :- Long
   pstate-name :- String
   path :- s/Any
   key :- s/Any])

(drp/defrecord+ ToolInfoImpl
  [tool-specification :- ToolSpecification
   tool-fn :- clojure.lang.IFn
   include-context? :- Boolean]
  ToolInfo
  (getToolSpecification [this] tool-specification))


;; Datasets

(drp/defrecord+ CreateDataset
  [dataset-id :- UUID
   name :- String
   description :- (s/maybe String)
   input-json-schema :- (s/maybe String)
   output-json-schema :- (s/maybe String)
  ])

(drp/defrecord+ AddRemoteDataset
  [dataset-id :- UUID
   cluster-conductor-host :- (s/maybe String)
   cluster-conductor-port :- (s/maybe Long)
   module-name :- String
  ])

(drp/defrecord+ UpdateDatasetProperty
  [dataset-id :- UUID
   key :- clojure.lang.Keyword
   value :- Object])

(drp/defrecord+ DestroyDataset
  [dataset-id :- UUID])

(drp/defrecord+ AddDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   input :- Object
   reference-output :- (s/maybe Object)
   tags :- (s/maybe #{String})
   source :- (s/maybe InfoSource)
  ])

(drp/defrecord+ UpdateDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   key :- clojure.lang.Keyword
   value :- Object])

(drp/defrecord+ RemoveDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID])

(drp/defrecord+ AddDatasetExampleTag
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   tag :- String])

(drp/defrecord+ RemoveDatasetExampleTag
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   tag :- String])

(drp/defrecord+ DatasetSnapshot
  [dataset-id :- UUID
   from-snapshot-name :- (s/maybe String)
   to-snapshot-name :- String])

(drp/defrecord+ RemoveDatasetSnapshot
  [dataset-id :- UUID
   snapshot-name :- String])

;; Evaluators

(definterface EvaluatorEvent)

(drp/defrecord+ AddEvaluator
  [name :- String
   builder-name :- String
   params :- {String Object}
   description :- String
   input-json-path :- (s/maybe String)
   output-json-path :- (s/maybe String)
   reference-output-json-path :- (s/maybe String)
  ]
  EvaluatorEvent)

(drp/defrecord+ RemoveEvaluator
  [name :- String]
  EvaluatorEvent)

(drp/defrecord+ ExampleRunImpl
  [input :- Object
   reference-output :- Object
   output :- Object]
  ExampleRun
  (getInput [this] input)
  (getReferenceOutput [this] reference-output)
  (getOutput [this] output))

;; Experiments

(definterface ExperimentEvent)

(defprotocol ExperimentInputSelector)

(drp/defrecord+ TagSelector
  [tag :- String]
  ExperimentInputSelector)

(drp/defrecord+ ExampleIdsSelector
  [example-ids :- [UUID]]
  ExperimentInputSelector)


(drp/defrecord+ EvaluatorSelector
  [name :- String
   remote? :- Boolean])


(defprotocol TargetSpec)

(drp/defrecord+ AgentTarget
  [agent-name :- String]
  TargetSpec)

(drp/defrecord+ NodeTarget
  [agent-name :- String
   node :- String]
  TargetSpec)


(drp/defrecord+ ExperimentTarget
  [target-spec :- (s/protocol TargetSpec)
   input->args :- [String]])

(defprotocol ExperimentSpec
  (experiment-targets [this]))

(drp/defrecord+ RegularExperiment
  [target :- ExperimentTarget]
  ExperimentSpec
  (experiment-targets [this] [target]))

(drp/defrecord+ ComparativeExperiment
  [targets :- [ExperimentTarget]]
  ExperimentSpec
  (experiment-targets [this] targets))


;; since this is stored in a PState
(drp/defrecord+ ^{:features {:nippy-8-byte-hash false}} StartExperiment
  [id :- UUID
   name :- String

   dataset-id :- UUID
   snapshot :- (s/maybe String)
   selector :- (s/maybe (s/protocol ExperimentInputSelector))
   evaluators :- [EvaluatorSelector]

   spec :- (s/protocol ExperimentSpec)

   num-repetitions :- Long
   concurrency :- Long
  ]
  ExperimentEvent)


(drp/defrecord+ UpdateExperimentName
  [id :- UUID
   dataset-id :- UUID
   name :- String]
  ExperimentEvent)

(drp/defrecord+ DeleteExperiment
  [id :- UUID
   dataset-id :- UUID]
  ExperimentEvent)

(drp/defrecord+ ExperimentNodeInvoke
  [agent-name :- String
   node :- String
   args :- [Object]])

(drp/defrecord+ EvalInfo
  [agent-name :- String
   target :- (s/protocol EvalTarget)])

(drp/defrecord+ EvalInvoke
  [input :- (s/maybe Object)
   reference-output :- (s/maybe Object)
   outputs :- [Object]
   eval-name :- String
   builder-name :- String
   builder-params :- {String Object}
   eval-type :- (s/enum :regular :comparative)
   eval-infos :- [EvalInfo]
   source :- InfoSource
  ])

;; used in PState
(drp/defrecord+ EvalNumberStats
  [total :- Number
   count :- Long
   min :- Number
   max :- Number
   percentiles :- {Double Number}])

;; Analytics

(drp/defrecord+ OpStats
  [count :- Long
   total-time-millis :- Long
  ])

(drp/defrecord+ BasicAgentInvokeStats
  [nested-op-stats :- {clojure.lang.Keyword OpStats}
   input-token-count :- Long
   output-token-count :- Long
   total-token-count :- Long
   node-stats :- {String OpStats}
  ])

(drp/defrecord+ AgentRef
  [module-name :- String
   agent-name :- String])

(drp/defrecord+ SubagentInvokeStats
  [count :- Long
   basic-stats :- BasicAgentInvokeStats])

(drp/defrecord+ AgentInvokeStats
  [subagent-stats :- {AgentRef SubagentInvokeStats}
   basic-stats :- BasicAgentInvokeStats])

;; Misc

;; used for PState writes
(drp/defrecord+ DirectTaskId
  [task-id :- Long])

;; Internal protocols

(defprotocol UnderlyingObjects
  (underlying-objects [this]))

(defprotocol AgentsTopologyInternal
  (declare-agent-object-builder-internal [this name afn options])
  (declare-evaluator-builder-internal [this type name description builder-fn
                                       options]))


(defn declare-java-evaluator-builer
  [this type name description builder-fn options]
  (let [builder-fn (h/convert-jfn builder-fn)]
    (declare-evaluator-builder-internal this
                                        type
                                        name
                                        description
                                        (fn [params]
                                          (h/convert-jfn
                                           (builder-fn params)))
                                        (if options @options))))

(defprotocol AgentClientInternal
  (stream-internal [this agent-invoke node callback-fn])
  (stream-specific-internal [this agent-invoke node node-invoke-id callback-fn])
  (stream-all-internal [this agent-invoke node callback-fn])
  (subagent-next-step-async [this agent-invoke]))

(defprotocol AgentManagerInternal
  (add-remote-dataset-internal [this dataset-id cluster-conductor-host cluster-conductor-port
                                module-name]))

;; Configs

(drp/defrecord+ ChangeConfig
  [key :- String
   val :- Object])

(def ALL-CONFIGS {})

(defmacro defconfig
  [name schema-fn doc config-default]
  (let [cname      (-> name
                       str
                       str/lower-case
                       (str/replace "-" "."))
        csym       (setval [NAME END] "-CONFIG" name)
        change-sym (->> name
                        str
                        str/lower-case
                        (str "change-")
                        symbol)]
    `(do
       (defn ~change-sym
         [value#]
         (let [schema-fn# ~schema-fn]
           (when-not (schema-fn# value#)
             (throw
              (h/ex-info
               "Invalid config"
               {:name ~cname :value value# :value-type (class value#)})))
           (->ChangeConfig ~cname value#)
         ))
       (def ~csym
         {:name      ~cname
          :schema-fn ~schema-fn
          :doc       ~doc
          :default   ~config-default
          :change-fn ~change-sym})
       (alter-var-root (var ALL-CONFIGS) assoc ~cname ~csym)
     )))

(defn get-config
  [m config]
  (get m (:name config) (:default config)))

(defn natural-long?
  [v]
  (and (instance? Long v) (>= v 0)))

(defn positive-long?
  [v]
  (and (instance? Long v) (> v 0)))

(defconfig
  MAX-RETRIES
  natural-long?
  "Maximum number of times an agent should retry after failing"
  2)

(defconfig
  STALL-CHECKER-THRESHOLD-MILLIS
  positive-long?
  "Max delay after not seeing expected action to consider agent stalled and retry it"
  10000)

(defconfig
  ACQUIRE-OBJECT-TIMEOUT-MILLIS
  positive-long?
  "Timeout to acquire an agent object within a node"
  30000)

(defconfig
  MAX-TRACES-PER-TASK
  positive-long?
  "Maximum number of agent traces to keep per task"
  5000)
