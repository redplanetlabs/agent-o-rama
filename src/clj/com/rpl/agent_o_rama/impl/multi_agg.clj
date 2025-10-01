(ns com.rpl.agent-o-rama.impl.multi-agg
  (:use [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [loom.attr :as lattr]
   [loom.graph :as lgraph])
  (:import
   [com.rpl.agentorama
    MultiAgg
    MultiAgg$Impl]))

(defprotocol MultiAggInternal
  (internal-add-init! [this afn])
  (internal-add-handler! [this name afn])
  (multi-agg-state [this]))

(defmacro reify-MultiAgg
  [& body]
  `(reify
    ~'MultiAgg$Impl
    (~'init [this# f#] (internal-add-init! this# (h/convert-jfn f#)))
    ~@(for [i (range 0 (- h/MAX-ARITY 1))]
        (let [name-sym (h/type-hinted String 'name#)
              jfn-sym  (h/type-hinted (h/rama-function-class (+ i 1)) 'jfn#)
              on-sym   (h/type-hinted MultiAgg$Impl 'on)]
          `(~on-sym
            [this# ~name-sym ~jfn-sym]
            (internal-add-handler!
             this#
             ~name-sym
             (h/convert-jfn ~jfn-sym))
           )))
    ~@body
   ))

(defn mk-multi-agg
  []
  (let [on-vol   (volatile! {})
        init-vol (volatile! nil)]
    (reify-MultiAgg
     MultiAggInternal
     (internal-add-init!
      [this afn]
      (when (some? @init-vol)
        (throw (h/ex-info "MultiAgg already has init function specified" {})))
      (vreset! init-vol afn)
      this)
     (internal-add-handler!
      [this name afn]
      (when (contains? @on-vol name)
        (throw (h/ex-info "MultiAgg already has handler for given name"
                          {:name name})))
      (vswap! on-vol assoc name afn)
      this)
     (multi-agg-state [this]
                      {:init-fn     (or @init-vol (fn [] nil))
                       :on-handlers @on-vol
                      }))
  ))
