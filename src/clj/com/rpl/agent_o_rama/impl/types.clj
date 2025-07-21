(ns com.rpl.agent-o-rama.impl.types
  (:use [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.ramaspecter.defrecord-plus :as drp]
   [rpl.schema.core :as s])
  (:import
   [com.rpl.agentorama.impl
    NippyMap]
   [com.rpl.rama.integration
    TaskGlobalObject]
   [java.util.concurrent
    CompletableFuture]))

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

(drp/defrecord+ AgentInvoke
  [args :- [s/Any]
   time-millis :- Long
  ])

(drp/defrecord+ AgentResult
  [val :- s/Any
   failure? :- Boolean])


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
  [invoke-id :- Long
   args :- [s/Any]])

(drp/defrecord+ NestedOpInfo
  [start-time-millis :- Long
   finish-time-millis :- Long
   ;; info for models contains token stats, input prompt, output, etc.
   info :- (s/maybe {String s/Any})])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- Long
   fork-invoke-id :- (s/maybe Long)
   target-task-id :- Long
   node-name :- String
   args :- [s/Any]
  ])

(drp/defrecord+ ForkContext
  [invoke-id->new-args :- {Long [s/Any]}
   affected-aggs :- (s/maybe #{Long}) ; agg-start-node invoke-ids
  ])

(drp/defrecord+ NodeComplete
  [task-id :- Long
   invoke-id :- Long
   retry-num :- Long
   node-fn-res :- s/Any
   emits :- [AgentNodeEmit]
   result :- (s/maybe AgentResult)
   nested-ops :- [NestedOpInfo]
   finish-time-millis :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(drp/defrecord+ RetryNodeComplete
  [invoke-id :- Long
   retry-num :- Long
   fork-context :- (s/maybe ForkContext)
  ])

(drp/defrecord+ NodeFailure
  [task-id :- Long
   invoke-id :- Long
   retry-num :- Long
  ])

(drp/defrecord+ AgentFailure
  [agent-task-id :- Long
   agent-id :- Long
   retry-num :- Long])

(drp/defrecord+ RetryAgentInvoke
  [agent-task-id :- Long
   agent-id :- Long
   expected-retry-num :- Long])

(drp/defrecord+ ForkAgentInvoke
  [agent-task-id :- Long
   agent-id :- Long
   invoke-id->new-args :- {Long [s/Any]}])

(drp/defrecord+ HistoricalAgentNodeInfo
  [node-type :- clojure.lang.Keyword ; :node, :agg-node, :agg-start-node
   output-nodes :- #{String}
   agg-context :- (s/maybe String)
  ])

(drp/defrecord+ HistoricalAgentGraphInfo
  [node-map :- {String HistoricalAgentNodeInfo} ; :node, :agg-node,
                                                ; :agg-start-node
   start-node :- String
   uuid :- String
  ])

(drp/defrecord+ NodeStreamingResult
  [agent-task-id :- Long
   agent-id :- Long
   node :- String
   invoke-id :- Long
   retry-num :- Long
   streaming-index :- Long
   value :- Object])

(drp/defrecord+ StreamingChunk
  [invoke-id :- Long
   index :- Long
   chunk :- Object])

(drp/defrecord+ NodeOp
  [invoke-id :- Long
   fork-invoke-id :- (s/maybe Long)
   fork-context :- (s/maybe ForkContext)
   next-node :- String
   args :- [s/Any]
   agg-invoke-id :- (s/maybe Long)])

(drp/defrecord+ AggAckOp
  [agg-invoke-id :- Long
   ack-val :- Long])

(drp/defrecord+ PStateWrite
  [agent-name :- String
   agent-task-id :- Long
   agent-id :- Long
   retry-num :- Long
   pstate-name :- String
   path :- s/Any
   key :- s/Any])

(defprotocol AgentClientInternal
  (stream-internal [this agent-invoke node callback-fn])
  (stream-all-internal [this agent-invoke node callback-fn])
  (underlying-objects [this]))

(drp/defrecord+ ChangeConfig
  [key :- String
   val :- Object])

(def ALL-CONFIGS {})

(defmacro defconfig
  [name schema-fn config-default]
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
       (def ~csym
         {:name      ~cname
          :schema-fn ~schema-fn
          :default   ~config-default})
       (alter-var-root (var ALL-CONFIGS) assoc ~cname ~csym)
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


(defconfig MAX-RETRIES
           natural-long?
           3)

(defconfig STALL-CHECKER-THRESHOLD-MILLIS
           positive-long?
           10000)
