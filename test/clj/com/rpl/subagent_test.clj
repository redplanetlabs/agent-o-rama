(ns com.rpl.subagent-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]))

(deftest local-subagent-test
  (with-open [ipc (rtest/create-ipc)
              ui  (aor/start-ui ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             "node1"
             (fn [agent-node]
               (let [bar (aor/agent-client agent-node "bar")]
                 (aor/emit! agent-node
                            "node1"
                            (aor/agent-invoke bar "some input"))
               )))
            (aor/node
             "node1"
             nil
             (fn [agent-node s]
               (aor/result! agent-node (str s "!")))
            ))
        (-> topology
            (aor/new-agent "bar")
            (aor/node
             "start"
             "q"
             (fn [agent-node input]
               (aor/emit! agent-node
                          "a"
                          input
                          (aor/get-human-input agent-node
                                               "Tell me something."))
             ))
            (aor/node
             "q"
             nil
             (fn [agent-node input res]
               (aor/result!
                agent-node
                (str input res (aor/get-human-input agent-node "More.")))
             )))
        (-> topology
            (aor/new-agent "fib")
            (aor/node
             "start"
             nil
             (fn [agent-node v]
               (let [fib (aor/agent-client agent-node "fib")]
                 (if (#{0 1} v)
                   (aor/result! agent-node 1)
                   (aor/result!
                    agent-node
                    (+ (aor/agent-invoke fib (- v 1))
                       (aor/agent-invoke fib (- v 2)))
                   ))
               ))))
       ))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind fib (aor/agent-client agent-manager "fib"))

     (println "RES:" (aor/agent-invoke fib 4))
     ;; TODO: <<<<>>>>
     ;;   - include recursion / mutual recursion
     ;;   - proxy the human inputs back and forth
     ;;     - should there be a helper for this?
     ;;   - verify trace for every callable method


     ;; TODO: <<<<>>>> methods to test
     ; (aor/agent-invoke bar ...)
     ; (aor/agent-initiate bar ...)
     ; (aor/agent-fork bar ...)
     ; (aor/agent-initiate-fork bar ...)
     ; (aor/agent-next-step bar agent-invoke)
     ; (aor/agent-result bar agent-invoke)
     ; (aor/pending-human-inputs bar agent-invoke)
     ; (aor/provide-human-input bar request response)

    )))

(deftest mirror-subagent-test
  ;; TODO: <<<<>>>>>

  )
