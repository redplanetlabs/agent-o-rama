(ns com.rpl.agent-o-rama.types
  (:require [com.rpl.ramaspecter.defrecord-plus :as drp]
            [rpl.schema.core :as s])
  (:import [com.rpl.agentorama AsyncResult]
           [java.util.concurrent CompletableFuture]))

(def NODE-KW :node)
(def AGG-START-NODE-KW :agg-start-node)
(def AGG-NODE-KW :agg-node)

(defrecord Node [node-fn])
(defrecord NodeAggStart [node-fn agg-node-name])
(defrecord NodeAgg [init-fn update-fn node-fn])

(defn node->type-kw [node]
  (cond (instance? Node node) NODE-KW
        (instance? NodeAggStart node) AGG-START-NODE-KW
        (instance? NodeAgg node) AGG-NODE-KW
        :else (throw (ex-info "Unexpected node type" {:class (class node)}))))

;; TODO: <<<<>>>> use flexible serialization for these to ease updating the library? or just some of them?

(drp/defrecord+ AgentInvoke
  [args :- [Object]
   time-millis :- Long])

(drp/defrecord+ AgentResult
  [val :- (s/maybe Object)])

(drp/defrecord+ AgentNode
  [node :- (s/cond-pre Node NodeAggStart NodeAgg)
   output-nodes :- #{String}
   agg-context :- (s/maybe String)])

(drp/defrecord+ AgentGraph
  [node-map :- {String AgentNode}
   start-node :- String
   uuid :- String])

(drp/defrecord+ AgentNodeArg
  [val :- (s/maybe Object)
   async-op-index :- (s/maybe Long)])

(drp/defrecord+ AggInput
  [invoke-id :- Long
   args :- [Object]])

(drp/defrecord+ AsyncOpInfo
  [start-time-millis :- (s/maybe Long)
   finish-time-millis :- (s/maybe Long)
   ;; info for models contains token stats, input prompt, output, etc.
   info :- (s/maybe {String Object})])

(drp/defrecord+ AsyncResultOutOfBand
  [async-op-index :- Long]
  AsyncResult)

(drp/defrecord+ AsyncResultPStateQuery
  [async-op-index :- Long]
  AsyncResult)

(drp/defrecord+ AsyncPStateQuery
  [module-name :- String
   pstate-name :- String
   path :- Object
   async-op-index :- Long])

(drp/defrecord+ AsyncPStateTransform
  [pstate-name :- String
   path :- Object
   async-op-index :- Long])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- Long
   target-task-id :- Long
   node-name :- String
   args :- [(s/cond-pre AgentNodeArg AsyncResult CompletableFuture)]
   ])

(drp/defrecord+ HistoricalAgentNodeInfo
  [node-type :- {String clojure.lang.Keyword} ; :node, :agg-node, :agg-start-node
   output-nodes :- #{String}
   agg-context :- (s/maybe String)
   ])

(drp/defrecord+ HistoricalAgentGraphInfo
  [node-map :- {String HistoricalAgentNodeInfo} ; :node, :agg-node, :agg-start-node
   start-node :- String
   uuid :- String
   ])

(drp/defrecord+ AsyncFutureResult
  [task-id :- Long
   invoke-id :- Long
   async-op-index :- Long
   result :- Object
   start-time-millis :- Long
   finish-time-millis :- Long
   info :- {String Object}])

(drp/defrecord+ AsyncFutureStreamingResult
  [agent-task-id :- Long
   agent-id :- Long
   node :- String
   invoke-id :- Long
   id :- String
   streaming-index :- Long
   value :- Object])

(drp/defrecord+ NodeOp
  [invoke-id :- Long
   next-node :- String
   args :- [Object]
   agg-invoke-id :- Long])

(drp/defrecord+ AggAckOp
  [agg-invoke-id :- Long
   ack-val :- Long])
