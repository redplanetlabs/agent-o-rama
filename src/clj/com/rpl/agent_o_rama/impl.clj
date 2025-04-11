(ns com.rpl.agent-o-rama.impl
  (:require [com.rpl.agent-o-rama.helpers :as h])
  (:import [com.rpl.agentorama AggNode]))

(defprotocol AggNodeInternal
  (internal-add-handler! [this name afn])
  (internal-add-any-handler! [this afn])
  (internal-add-complete-handler! [this afn])
  (agg-node-state [this]))

(defmacro reify-AggNode [& body]
  `(reify ~'AggNode
    ~@(for [i (range 1 h/MAX-ARITY)]
        (let [name-sym (type-hinted String 'name#)
              ;; TODO:<<<<>>>> shoudl be RamaFunctions..
              jfn-sym (type-hinted (h/rama-void-function-class (inc i)) 'jfn#)]
          `(~'on [this# ~name-sym ~jfn-sym]
            (internal-add-node!
              this#
              ~name-sym
              (->Node
                (normalize-output-nodes outputNodesSpec#)
                (h/convert-void-jfn jfn#)))
            )))
    ))

(defn mk-agg-node []
  ;; TODO: <<<<<>>>>>
  )
