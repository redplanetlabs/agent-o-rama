(ns com.rpl.agent-o-rama.impl
  (:require [clojure.set :as set]
            [com.rpl.agent-o-rama.helpers :as h])
  (:import [com.rpl.agentorama AggNode]))

(defprotocol AggNodeInternal
  (internal-add-handler! [this name afn])
  (internal-add-any-handler! [this afn])
  (internal-add-complete-handler! [this afn])
  (agg-node-state [this]))

(defmacro reify-AggNode [& body]
  `(reify ~'AggNode
    ~@(for [i (range 0 (- h/MAX-ARITY 2))]
        (let [name-sym (type-hinted String 'name#)
              jfn-sym (type-hinted (h/rama-function-class (+ i 2)) 'jfn#)]
          `(~'on [this# ~name-sym ~jfn-sym]
            (internal-add-handler!
              this#
              ~name-sym
              (h/convert-jfn ~jfn-sym))
            )))
    ~@body
    ))

(defn mk-agg-node []
  (let [on-vol (volatile! {})
        on-any-vol (volatile! nil)
        on-complete-vol (volatile! nil)]
    (reify-AggNode
      (onAny [this jfn]
        (internal-add-any-handler! this (h/convert-jfn jfn)))
      (onComplete [this jfn]
        (internal-add-complete-handler! this (h/convert-void-jfn jfn)))
      AggNodeInternal
      (internal-add-handler! [this name afn]
        (when (some? @on-any-vol)
          (throw (ex-info "Agg node may not have both 'on' and 'onAny' handlers" {})))
        (when (contains? @on-vol name)
          (throw (ex-info "Agg node already has handler for given name" {:name name})))
        (vswap! on-vol assoc name afn)
        this)
      (internal-add-any-handler! [this afn]
        (when (some? @on-any-vol)
          (throw (ex-info "Agg node can only have one onAny handler" {})))
        (when-not (empty? @on-vol)
          (throw (ex-info "Agg node may not have both 'on' and 'onAny' handlers" {})))
        (vreset! on-any-vol afn)
        this )
      (internal-add-complete-handler! [this afn]
        (when (some? @on-complete-vol)
          (throw (ex-info "Agg node can only have one onComplete handler" {})))
        (vreset! on-complete-vol afn)
        this )
      (agg-node-state [this]
        {:on-handlers @on-vol
         :on-any-handler @on-any-vol
         :on-complete-handler @on-complete-vol
         }))
      ))

(defmacro agg-node-object [& body]
  (let [ret-sym (gen-sym "ret")]
    `(let [~ret-sym (mk-agg-node)]
      ~@(for [form body]
          (condp = (first form)
            'on
            (let [[_ name & body] form]
              `(internal-add-handler! ~ret-sym ~name (fn ~@body)))

            'on-any
            (let [[_ & body] form]
              `(internal-add-any-handler! ~ret-sym (fn ~@body)))

            'on-complete
            (let [[_ & body] form]
              `(internal-add-complete-handler! ~ret-sym (fn ~@body)))

            (throw (ex-info "Invalid agg node method" {:method (first form)}))
            ))
       ~ret-sym
       )))
