(ns com.rpl.agent-o-rama.impl.types
  (:use [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.serialize]
   [com.rpl.ramaspecter.defrecord-plus :as drp]
   [rpl.schema.core :as s])
  (:import
   [com.rpl.agentorama
    AgentComplete
    AgentInvoke
    AgentRef
    ExampleRun
    HumanInputRequest
    NestedOpType
    NodeInvoke
    RunInfo
    RunType
    ToolInfo]
   [com.rpl.agentorama.analytics
    AgentInvokeStats
    BasicAgentInvokeStats
    Feedback
    NestedOpInfo
    SubagentInvokeStats
    OpStats]
   [com.rpl.agentorama.source
    ActionSource
    AgentRunSource
    AiSource
    ApiSource
    BulkUploadSource
    EvalSource
    ExperimentSource
    HumanSource
    InfoSource]
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


(defmacro defaorrecord
  [name & args]
  (let [s (with-meta name {:features {:nippy-8-byte-hash false}})]
    `(drp/defrecord+ ~s ~@args)))

(def ^:dynamic OPERATION-SOURCE nil)
(def ^:dynamic FORCED-AGENT-INVOKE-ID nil)
(def ^:dynamic FORCED-AGENT-TASK-ID nil)

(def AGENT-TOPOLOGY-NAME "_agent-topology")
(def AGENT-MB-TOPOLOGY-NAME "_agent-mb-topology")
(def AGENT-ANALYTICS-MB-TOPOLOGY-NAME "_agent-analytics-topology")
(def EVALUATOR-AGENT-NAME "_aor-evaluator")

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

(def NESTED-OP-TYPE-CLJ
  {:store-read  NestedOpType/STORE_READ
   :store-write NestedOpType/STORE_WRITE
   :db-read     NestedOpType/DB_READ
   :db-write    NestedOpType/DB_WRITE
   :model-call  NestedOpType/MODEL_CALL
   :tool-call   NestedOpType/TOOL_CALL
   :agent-call  NestedOpType/AGENT_CALL
   :human-input NestedOpType/HUMAN_INPUT
   :other       NestedOpType/OTHER
  })

(def NESTED-OP-TYPE-JAVA
  (into {} (for [[k v] NESTED-OP-TYPE-CLJ] [v k])))

(defn nested-op-type->clj
  [v]
  (if-let [res (get NESTED-OP-TYPE-JAVA v)]
    res
    (throw (h/ex-info "Unknown nested op type" {:val v :type (class v)}))))

(defn nested-op-type->java
  [v]
  (if-let [res (get NESTED-OP-TYPE-CLJ v)]
    res
    (throw (h/ex-info "Unknown nested op type" {:val v :type (class v)}))))

(defprotocol EvalTarget)

(defaorrecord AgentInvokeImpl
  [task-id :- Long
   agent-invoke-id :- UUID]
  AgentInvoke
  (getTaskId [this] task-id)
  (getAgentInvokeId [this] agent-invoke-id)
  EvalTarget)

(defaorrecord NodeInvokeImpl
  [task-id :- Long
   node-invoke-id :- UUID]
  NodeInvoke
  (getTaskId [this] task-id)
  (getNodeInvokeId [this] node-invoke-id)
  EvalTarget)

;; Sources

(defn source-string
  [^InfoSource i]
  (.getSourceString i))

(defaorrecord HumanSourceImpl
  [name :- String]
  HumanSource
  (getName [this] name)
  (getSourceString [this] (str "human[" name "]")))

(defaorrecord AiSourceImpl
  []
  AiSource
  (getSourceString [this] "ai"))

(defaorrecord ApiSourceImpl
  []
  ApiSource
  (getSourceString [this] "api"))

(defaorrecord BulkUploadSourceImpl
  []
  BulkUploadSource
  (getSourceString [this] "bulkUpload"))

(defaorrecord ExperimentSourceImpl
  [dataset-id :- UUID
   experiment-id :- UUID]
  ExperimentSource
  (getDatasetId [this] dataset-id)
  (getExperimentId [this] experiment-id)
  (getSourceString [this] "experiment"))

(defaorrecord AgentRunSourceImpl
  [module-name :- String
   agent-name :- String
   agent-invoke :- AgentInvokeImpl]
  AgentRunSource
  (getModuleName [this] module-name)
  (getAgentName [this] agent-name)
  (getAgentInvoke [this] agent-invoke)
  (getSourceString [this] (str "agent[" module-name "/" agent-name "]")))

(defaorrecord EvalSourceImpl
  [eval-name :- String
   agent-invoke :- AgentInvokeImpl
   source :- (s/maybe InfoSource)]
  EvalSource
  (getEvalName [this] eval-name)
  (getSourceString [this] (str "eval[" eval-name "]")))

(defaorrecord ActionSourceImpl
  [agent-name :- String
   rule-name :- String]
  ActionSource
  (getRuleName [this] rule-name)
  (getSourceString [this] (str "action[" agent-name "/" rule-name "]")))

;; Core types

(defn valid-metadata-value?
  [v]
  (contains? #{Long Double Boolean String} (class v)))

(def METADATA-SCHEMA (s/maybe {String (s/pred valid-metadata-value?)}))

(defaorrecord AgentInitiate
  [args :- [s/Any]
   forced-agent-task-id :- (s/maybe Long)
   forced-agent-invoke-id :- (s/maybe UUID)
   metadata :- METADATA-SCHEMA
   source :- (s/maybe InfoSource)
  ])

(defaorrecord EditMetadata
  [agent-name :- String
   agent-task-id :- Long
   agent-invoke-id :- UUID
   key :- String
   value :- (s/maybe (s/pred valid-metadata-value?))])

(defaorrecord AgentExecutionContext
  [metadata :- METADATA-SCHEMA
   source :- (s/maybe InfoSource)])

(defaorrecord AgentResult
  [val :- s/Any
   failure? :- Boolean])

(defaorrecord AgentCompleteImpl
  [result :- s/Any]
  AgentComplete
  (getResult [this] val))


(defaorrecord AgentNode
  [node :- (s/cond-pre Node NodeAggStart NodeAgg)
   output-nodes :- #{String}
   agg-context :- (s/maybe String)])

(defaorrecord AgentGraph
  [node-map :- NippyMap ; {String AgentNode}
   start-node :- String
   update-mode :- (s/enum :continue :restart :drop)
   uuid :- String]
  TaskGlobalObject
  (prepareForTask [this task-id context])
  (close [this]))

(defaorrecord StoreInfo
  [store-info :- {String clojure.lang.Keyword}
   ;; module-name -> pstate-name -> store-type
   mirror-store-info :- {String {String clojure.lang.Keyword}}]
  TaskGlobalObject
  (prepareForTask [this task-id context])
  (close [this]))

(defaorrecord AggInput
  [invoke-id :- UUID
   args :- [s/Any]])

(defaorrecord NestedOpInfoImpl
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
   info :- (s/maybe {String s/Any})]
  NestedOpInfo
  (getStartTimeMillis [this] start-time-millis)
  (getFinishTimeMillis [this] start-time-millis)
  (getType [this] (nested-op-type->java type))
  (getInfo [this] info))

(defaorrecord AgentNodeEmit
  [invoke-id :- UUID
   fork-invoke-id :- (s/maybe UUID)
   target-task-id :- Long
   node-name :- String
   args :- [s/Any]
  ])

(defaorrecord ForkContext
  [invoke-id->new-args :- {UUID [s/Any]}
   affected-aggs :- (s/maybe #{UUID}) ; agg-start-node invoke-ids
  ])

(defaorrecord NodeComplete
  [task-id :- Long
   invoke-id :- UUID
   retry-num :- Long
   node-fn-res :- s/Any
   emits :- [AgentNodeEmit]
   result :- (s/maybe AgentResult)
   nested-ops :- [NestedOpInfoImpl]
   finish-time-millis :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(defaorrecord RetryNodeComplete
  [invoke-id :- UUID
   retry-num :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(defaorrecord NodeFailure
  [task-id :- Long
   invoke-id :- UUID
   retry-num :- Long
   throwable-str :- String
   nested-ops :- [NestedOpInfoImpl]
  ])

(defaorrecord ExceptionSummary
  [throwable-str :- String
   node :- String
   invoke-id :- UUID])

(defaorrecord AgentFailure
  [agent-task-id :- Long
   agent-id :- UUID
   retry-num :- Long])

(defaorrecord RetryAgentInvoke
  [agent-task-id :- Long
   agent-id :- UUID
   expected-retry-num :- Long])

(defaorrecord ForkAgentInvoke
  [agent-task-id :- Long
   agent-id :- UUID
   invoke-id->new-args :- {UUID [s/Any]}])

(defaorrecord HistoricalAgentNodeInfo
  [node-type :- clojure.lang.Keyword ; :node, :agg-node, :agg-start-node
   output-nodes :- #{String}
   agg-context :- (s/maybe String)
  ])

(defaorrecord HistoricalAgentGraphInfo
  [node-map :- {String HistoricalAgentNodeInfo}
   start-node :- String
   uuid :- String
  ])

(defaorrecord NodeStreamingResult
  [agent-task-id :- Long
   agent-id :- UUID
   node :- String
   invoke-id :- UUID
   retry-num :- Long
   streaming-index :- Long
   value :- Object])

(defaorrecord StreamingChunk
  [invoke-id :- UUID
   index :- Long
   chunk :- Object])

(defaorrecord NodeHumanInputRequest
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

(defaorrecord HumanInput
  [request :- NodeHumanInputRequest
   response :- String])

(defaorrecord NodeOp
  [invoke-id :- UUID
   fork-invoke-id :- (s/maybe UUID)
   fork-context :- (s/maybe ForkContext)
   next-node :- String
   args :- [s/Any]
   agg-invoke-id :- (s/maybe UUID)])

(defaorrecord AggAckOp
  [agg-invoke-id :- UUID
   ack-val :- Long])

(defaorrecord PStateWriteAgentSource
  [agent-name :- String
   agent-task-id :- Long
   agent-id :- UUID
   retry-num :- Long])

(defaorrecord PStateWrite
  [agent-source :- (s/maybe PStateWriteAgentSource)
   pstate-name :- String
   path :- s/Any
   key :- s/Any])

(defaorrecord ToolInfoImpl
  [tool-specification :- ToolSpecification
   tool-fn :- clojure.lang.IFn
   include-context? :- Boolean]
  ToolInfo
  (getToolSpecification [this] tool-specification))


;; Datasets

(defaorrecord CreateDataset
  [dataset-id :- UUID
   name :- String
   description :- (s/maybe String)
   input-json-schema :- (s/maybe String)
   output-json-schema :- (s/maybe String)
  ])

(defaorrecord AddRemoteDataset
  [dataset-id :- UUID
   cluster-conductor-host :- (s/maybe String)
   cluster-conductor-port :- (s/maybe Long)
   module-name :- String
  ])

(defaorrecord UpdateDatasetProperty
  [dataset-id :- UUID
   key :- clojure.lang.Keyword
   value :- Object])

(defaorrecord DestroyDataset
  [dataset-id :- UUID])

(defaorrecord AddDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   input :- Object
   reference-output :- (s/maybe Object)
   tags :- (s/maybe #{String})
   source :- (s/maybe InfoSource)
  ])

(defaorrecord UpdateDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   key :- clojure.lang.Keyword
   value :- Object])

(defaorrecord RemoveDatasetExample
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID])

(defaorrecord AddDatasetExampleTag
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   tag :- String])

(defaorrecord RemoveDatasetExampleTag
  [dataset-id :- UUID
   snapshot-name :- (s/maybe String)
   example-id :- UUID
   tag :- String])

(defaorrecord DatasetSnapshot
  [dataset-id :- UUID
   from-snapshot-name :- (s/maybe String)
   to-snapshot-name :- String])

(defaorrecord RemoveDatasetSnapshot
  [dataset-id :- UUID
   snapshot-name :- String])

;; Evaluators

(definterface EvaluatorEvent)

(defaorrecord AddEvaluator
  [name :- String
   builder-name :- String
   params :- {String (s/maybe Object)}
   description :- String
   input-json-path :- (s/maybe String)
   output-json-path :- (s/maybe String)
   reference-output-json-path :- (s/maybe String)
  ]
  EvaluatorEvent)

(defaorrecord RemoveEvaluator
  [name :- String]
  EvaluatorEvent)

(defaorrecord ExampleRunImpl
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

(defaorrecord TagSelector
  [tag :- String]
  ExperimentInputSelector)

(defaorrecord ExampleIdsSelector
  [example-ids :- [UUID]]
  ExperimentInputSelector)


(defaorrecord EvaluatorSelector
  [name :- String
   remote? :- Boolean])


(defprotocol TargetSpec)

(defaorrecord AgentTarget
  [agent-name :- String]
  TargetSpec)

(defaorrecord NodeTarget
  [agent-name :- String
   node :- String]
  TargetSpec)


(defaorrecord ExperimentTarget
  [target-spec :- (s/protocol TargetSpec)
   metadata :- METADATA-SCHEMA
   ;; these are list of JSON path templates
   input->args :- [String]])

(defprotocol ExperimentSpec
  (experiment-targets [this]))

(defaorrecord RegularExperiment
  [target :- ExperimentTarget]
  ExperimentSpec
  (experiment-targets [this] [target]))

(defaorrecord ComparativeExperiment
  [targets :- [ExperimentTarget]]
  ExperimentSpec
  (experiment-targets [this] targets))


;; since this is stored in a PState
(defaorrecord StartExperiment
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

(defaorrecord UpdateExperimentName
  [id :- UUID
   dataset-id :- UUID
   name :- String]
  ExperimentEvent)

(defaorrecord DeleteExperiment
  [id :- UUID
   dataset-id :- UUID]
  ExperimentEvent)

(defaorrecord ExperimentNodeInvoke
  [agent-name :- String
   node :- String
   args :- [Object]])

(defaorrecord EvalInfo
  [agent-name :- String
   target :- (s/protocol EvalTarget)])

(defaorrecord EvalInvoke
  [input :- (s/maybe Object)
   reference-output :- (s/maybe Object)
   outputs :- [Object]
   eval-name :- String
   builder-name :- String
   builder-params :- {String (s/maybe Object)}
   eval-type :- (s/enum :regular :comparative)
   eval-infos :- [EvalInfo]
   source :- (s/maybe InfoSource)
  ])

;; used in PState
(defaorrecord EvalNumberStats
  [total :- Number
   count :- Long
   min :- Number
   max :- Number
   percentiles :- {Double Number}])

;; Analytics

(defaorrecord OpStatsImpl
  [count :- Long
   total-time-millis :- Long]
  OpStats
  (getCount [this] (int count))
  (getTotalTimeMillis [this] total-time-millis))

(defaorrecord BasicAgentInvokeStatsImpl
  [nested-op-stats :- {clojure.lang.Keyword OpStatsImpl}
   input-token-count :- Long
   output-token-count :- Long
   total-token-count :- Long
   node-stats :- {String OpStatsImpl}]
  BasicAgentInvokeStats
  (getNestedOpStats [this]
    (transform MAP-KEYS nested-op-type->java nested-op-stats))
  (getInputTokenCount [this] (int input-token-count))
  (getOutputTokenCount [this] (int output-token-count))
  (getTotalTokenCount [this] (int total-token-count))
  (getNodeStats [this] node-stats))

(defaorrecord AgentRefImpl
  [module-name :- String
   agent-name :- String]
  AgentRef
  (getModuleName [this] module-name)
  (getAgentName [this] agent-name))

(defaorrecord SubagentInvokeStatsImpl
  [count :- Long
   basic-stats :- BasicAgentInvokeStatsImpl]
  SubagentInvokeStats
  (getCount [this] (int count))
  (getBasicStats [this] basic-stats))

(defaorrecord AgentInvokeStatsImpl
  [subagent-stats :- {AgentRefImpl SubagentInvokeStatsImpl}
   basic-stats :- BasicAgentInvokeStatsImpl]
  AgentInvokeStats
  (getSubagentStats [this] subagent-stats)
  (getBasicStats [this] basic-stats))

(defaorrecord FeedbackImpl
  [scores :- {String (s/maybe Object)}
   source :- InfoSource
   created-at :- Long
   modified-at :- Long]
  Feedback
  (getScores [this] scores)
  (getSource [this] source)
  (getCreatedAt [this] created-at)
  (getModifiedAt [this] modified-at))

(defaorrecord RunInfoImpl
  [rule-name :- String
   action-name :- String
   agent-name :- String
   node-name :- (s/maybe String)
   agent-invoke :- AgentInvokeImpl
   node-invoke :- (s/maybe NodeInvokeImpl)
   type :- (s/enum :agent :node)
   start-time-millis :- Long
   latency-millis :- (s/maybe Long)
   feedback :- [FeedbackImpl]
   agent-stats :- (s/maybe AgentInvokeStatsImpl)
   nested-ops :- (s/maybe [NestedOpInfoImpl])]
  RunInfo
  (getRuleName [this] rule-name)
  (getActionName [this] action-name)
  (getAgentName [this] agent-name)
  (getNodeName [this] node-name)
  (getAgentInvoke [this] agent-invoke)
  (getNodeInvoke [this] node-invoke)
  (getRunType [this]
    (cond
      (= type :agent)
      RunType/AGENT

      (= type :node)
      RunType/NODE

      :else
      (throw (h/ex-info "Unexpected run type" {:type type}))))
  (getStartTimeMillis [this] start-time-millis)
  (getLatencyMillis [this] latency-millis)
  (getFeedback [this] feedback)
  (getAgentStats [this] agent-stats)
  (getNodeNestedOps [this] nested-ops))

;; Rules

(defprotocol RuleFilter
  (dependency-rule-names [this])
  (rule-filter-matches? [this info]))

(defaorrecord ComparatorSpec
  [comparator :- (s/enum := :not= :< :> :<= :>=)
   value :- Object])

(defn comparator-spec-matches?
  [{:keys [comparator value]} v]
  (condp = comparator
    :=
    (= v value)

    :not=
    (not= v value)

    :<
    (< v value)

    :>
    (> v value)

    :<=
    (<= v value)

    :>=
    (>= v value)

    (throw (h/ex-info "Unknown comparator" {:comparator comparator}))))

(defaorrecord FeedbackFilter
  [rule-name :- String
   feedback-key :- String
   comparator-spec :- ComparatorSpec])

(defaorrecord LatencyFilter
  [comparator-spec :- ComparatorSpec])

;; if there were any exceptions, even if it succeeded
(defaorrecord ErrorFilter
  [])

(defaorrecord InputMatchFilter
  [json-path :- String
   regex :- java.util.regex.Pattern])

(defaorrecord OutputMatchFilter
  [json-path :- String
   regex :- java.util.regex.Pattern])

(defaorrecord TokenCountFilter
  [type :- (s/enum :input :output :total)
   comparator-spec :- ComparatorSpec])

(defaorrecord AndFilter
  [filters :- [(s/protocol RuleFilter)]])

(defaorrecord OrFilter
  [filters :- [(s/protocol RuleFilter)]])

(defaorrecord NotFilter
  [filter :- (s/protocol RuleFilter)])

(definterface RuleEvent)

(defaorrecord AddRule
  [name :- String
   id :- UUID
   agent-name :- String
   node-name :- (s/maybe String)
   action-name :- String
   action-params :- {String String}
   filter :- (s/protocol RuleFilter)
   sampling-rate :- Double
   start-time-millis :- Long
   status-filter :- (s/enum :all :success :fail)]
  RuleEvent)

(defaorrecord DeleteRule
  [agent-name :- String
   name :- String]
  RuleEvent)

(defaorrecord ActionLog
  [start-time-millis :- Long
   finish-time-millis :- Long
   agent-invoke :- AgentInvokeImpl
   node-invoke :- (s/maybe NodeInvokeImpl)
   success? :- Boolean
   info-map :- {String (s/maybe Object)}])

;; Misc

;; used for PState writes
(defaorrecord DirectTaskId
  [task-id :- Long])

;; Internal protocols

(defprotocol UnderlyingObjects
  (underlying-objects [this]))

(defprotocol AgentTopologyInternal
  (declare-agent-object-builder-internal [this name afn options])
  (declare-evaluator-builder-internal [this type name description builder-fn
                                       options])
  (declare-action-builder-internal [this name description builder-fn options]))


(defn convert-java-builder-fn
  [builder-jfn]
  (let [builder-fn (h/convert-jfn builder-jfn)]
    (fn [params]
      (h/convert-jfn
       (builder-fn params)))))

(defn declare-java-evaluator-builder
  [this type name description builder-jfn options]
  (declare-evaluator-builder-internal this
                                      type
                                      name
                                      description
                                      (convert-java-builder-fn builder-jfn)
                                      (if options @options)))

(defprotocol AgentClientInternal
  (set-metadata-internal! [this agent-invoke key value])
  (invoke-with-context-async-internal [this context args])
  (initiate-with-context-async-internal [this context args])
  (stream-internal [this agent-invoke node callback-fn])
  (stream-specific-internal [this agent-invoke node node-invoke-id callback-fn])
  (stream-all-internal [this agent-invoke node callback-fn])
  (subagent-next-step-async [this agent-invoke]))

(defprotocol AgentManagerInternal
  (add-remote-dataset-internal [this dataset-id cluster-conductor-host cluster-conductor-port
                                module-name]))

;; Configs

(defaorrecord ChangeConfig
  [key :- String
   val :- Object])

;; agent configs
(def ALL-CONFIGS {})
;; global configs
(def ALL-GLOBAL-CONFIGS {})

(defmacro defconfig*
  [global name schema-fn doc config-default]
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
       (alter-var-root (var ~global) assoc ~cname ~csym)
     )))

(defmacro defconfig
  [name schema-fn doc config-default]
  `(defconfig* ALL-CONFIGS ~name ~schema-fn ~doc ~config-default))

(defmacro defglobalconfig
  [name schema-fn doc config-default]
  `(defconfig* ALL-GLOBAL-CONFIGS ~name ~schema-fn ~doc ~config-default))

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


(defglobalconfig
 MAX-LIMITED-ACTIONS-CONCURRENCY
 positive-long?
 "Maximum number of limited actions (e.g. online evaluations) that can be executing at once across all agents and all tasks"
 10)

(defglobalconfig
 ACTIONS-PROCESSING-ITERATION-TIME-MILLIS
 positive-long?
 "Maximum amount of time to spend processing actions per iteration"
 20000)
