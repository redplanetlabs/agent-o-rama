(ns com.rpl.agent-o-rama.impl.types
  (:require
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

(def NODE-KW :node)
(def AGG-START-NODE-KW :agg-start-node)
(def AGG-NODE-KW :agg-node)

(defrecord Node [node-fn])
(defrecord NodeAggStart [node-fn agg-node-name])
(defrecord NodeAgg [init-fn update-fn node-fn])

(defn node->type-kw
  [node]
  (cond (instance? Node node) NODE-KW
        (instance? NodeAggStart node) AGG-START-NODE-KW
        (instance? NodeAgg node) AGG-NODE-KW
        :else (throw (h/ex-info "Unexpected node type" {:class (class node)}))))

;; TODO: <<<<>>>> use flexible serialization for these to ease updating the
;; library? or just some of them?

(drp/defrecord+ AgentInvoke
  [args :- [(s/maybe Object)]
   time-millis :- Long])

(drp/defrecord+ AgentResult
  [val :- (s/maybe Object)
   failure? :- Boolean])


(drp/defrecord+ AgentNode
  [node :- (s/cond-pre Node NodeAggStart NodeAgg)
   output-nodes :- #{String}
   agg-context :- (s/maybe String)])

(drp/defrecord+ AgentGraph
  [node-map :- NippyMap ; {String AgentNode}
   start-node :- String
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
   args :- [(s/maybe Object)]])

(drp/defrecord+ NestedOpInfo
  [start-time-millis :- Long
   finish-time-millis :- Long
   ;; info for models contains token stats, input prompt, output, etc.
   info :- (s/maybe {String Object})])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- Long
   target-task-id :- Long
   node-name :- String
   args :- [(s/maybe Object)]
  ])

(drp/defrecord+ NodeComplete
  [task-id :- Long
   invoke-id :- Long
   node-fn-res :- (s/maybe Object)
   emits :- [AgentNodeEmit]
   result :- (s/maybe AgentResult)
   nested-ops :- [NestedOpInfo]
   finish-time-millis :- Long
  ])

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
   streaming-index :- Long
   value :- Object])

(drp/defrecord+ NodeOp
  [invoke-id :- Long
   next-node :- String
   args :- [(s/maybe Object)]
   agg-invoke-id :- (s/maybe Long)])

(drp/defrecord+ AggAckOp
  [agg-invoke-id :- Long
   ack-val :- Long])

(drp/defrecord+ PStateWrite
  [pstate-name :- String
   path :- s/Any
   key :- s/Any])
