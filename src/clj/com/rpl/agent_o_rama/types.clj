(ns com.rpl.agent-o-rama.types
  (:require [com.rpl.ramaspecter.defrecord-plus :as drp]))

(def NODE-KW :node)
(def AGG-START-NODE-KW :agg-start-node)
(def AGG-NODE-KW :agg-node)

(drp/defrecord+ AgentInvoke
  [args :- [Object]
   time-millis :- Long])

(drp/defrecord+ AgentResult
  [val :- Object])

(drp/defrecord+ AgentNodeEmit
  [invoke-id :- Long
   target-task-id :- Long
   node-name :- String
   args :- [Object]
   ])

(drp/defrecord+ AgentGraphInfo
  [start-node :- String
   graph :- {String clojure.lang.Keyword} ; :node, :agg-node, :agg-start-node
   uuid :- String
   ])

(sp/defrecord+ ContinueExecution
  [invoke-id :- Long])

;; TODO: <<<<>>>>
(drp/defrecord+ AsyncFutureResult
  [])
