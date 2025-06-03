(ns com.rpl.test-helpers
  (:use [clojure.test]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama.impl.types :as aor-types]))

(defmacro letlocals
  [& body]
  (let [[tobind [last-binding-or-expr]]
        (split-at (dec (count body)) body)

        last-expr
        (if (and (list? last-binding-or-expr)
                 (= 'bind (first last-binding-or-expr)))
          (last last-binding-or-expr)
          last-binding-or-expr)

        binded
        (vec (mapcat (fn [e]
                       (if (and (list? e) (= 'bind (first e)))
                         [(second e) (last e)]
                         ['_ e]))

              tobind))]
    `(let ~binded
          ~last-expr)))

(defmacro ex-info-thrown?
  [re data & body]
  `(try
     ~@body
     (is false "Did not throw exception")
     (catch clojure.lang.ExceptionInfo e#
       (is (re-matches ~re (ex-message e#)))
       (is (= ~data (ex-data e#)))
     )))

(defn invoke-agent-and-wait!
  [depot invokes-pstate args]
  (let [res   (foreign-append! depot (aor-types/->AgentInvoke args 0))
        [graph-task-id graph-id] (-> res
                                     vals
                                     first)
        prom  (promise)
        proxy (foreign-proxy [(keypath graph-id) :ack-val]
                             invokes-pstate
                             {:pkey        graph-task-id
                              :callback-fn (fn [new-val _ _]
                                             (when (= new-val 0)
                                               (deliver prom nil))
                                           )})]
    (when (= ::failed (deref prom 30000 ::failed))
      (throw (ex-info "Agent did not complete" {})))
    (close! proxy)
    [graph-task-id graph-id]
  ))

(defn invoke-agent-and-return!
  [depot invokes-pstate args]
  (let [[graph-task-id graph-id] (invoke-agent-and-wait! depot
                                                         invokes-pstate
                                                         args)]
    (foreign-select-one
     [(keypath graph-id) :result]
     invokes-pstate
     {:pkey graph-task-id})
  ))
