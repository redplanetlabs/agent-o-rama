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
  (with-open [ipc (rtest/create-ipc)]
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
                 (aor/result! agent-node (aor/agent-invoke bar "some input"))
               )))
        )


        (-> topology
            (aor/new-agent "bar")
            (aor/node
             "start"
             nil
             (fn [agent-node input]
               (aor/result! agent-node
                            (aor/get-human-input agent-node
                                                 "Tell me something."))
             )))
       ))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
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
