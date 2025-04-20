(ns com.rpl.agent-o-rama.types
  (:require [com.rpl.ramaspecter.defrecord-plus :as drp]
            [rpl.schema.core :as s]))

(def NODE-KW :node)
(def AGG-START-NODE-KW :agg-start-node)
(def AGG-NODE-KW :agg-node)

(defrecord Node [node-fn])
(defrecord NodeAggStart [node-fn])
(defrecord NodeAgg [agg-node])

(defn node->type-kw [node]
  (cond (instance? Node node) NODE-KW
        (instance? NodeAggStart node) AGG-START-NODE-KW
        (instance? NodeAgg node) AGG-NODE-KW
        :else (throw (ex-info "Unexpected node type" {:class (class node)}))))

(drp/defrecord+ AgentInvoke
  [args :- [Object]
   time-millis :- Long])

(drp/defrecord+ AgentResult
  [val :- Object])

(drp/defrecord+ InProgressArg
  [val :- Object])

(drp/defrecord+ AgentNode
  [node :- (s/cond-pre Node NodeAggStart NodeAgg)
   output-nodes :- #{String}
   agg-context :- (s/maybe String)])

(drp/defrecord+ AgentGraph
  [node-map :- {String AgentNode}
   start-node :- String
   uuid :- String])

(s/defrecord+ AgentNodeArg
  [val :- (s/maybe Object)
   tokens-used :- Long
   start-time-millis :- Long
   finish-time-millis :- Long])

(s/defrecord+ AsyncArg
  [uuid :- String])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- Long
   target-task-id :- Long
   node-name :- String
   args :- [(s/cond-pre AgentNodeArg AsyncArg)]
   acked? :- Boolean
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

(sp/defrecord+ RetryExecution
  [invoke-id :- Long])

(drp/defrecord+ AsyncFutureResult
  [task-id :- Long
   invoke-id :- Long
   emit-index :- Long
   arg-index :- Long
   result :- Object])

(drp/defrecord+ AsyncFutureStreamingResult
  [agent-task-id :- Long
   agent-id :- Long
   node :- String
   invoke-id :- Long
   emit-index :- Long
   arg-index :- Long
   streaming-index :- Long
   value :- Object])

(drp/defrecord+ ParentInfo
  [parent-task-id :- Long
   parent-invoke-id :- Long
   emit-index :- Long])
