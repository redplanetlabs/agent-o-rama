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
               ))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
  ;; TODO: <<<<>>>>
  ;;   - include recursion / mutual recursion
  ;;   - verify trace for every callable method
  )))

(deftest mirror-subagent-test
  ;; TODO: <<<<>>>>>

  )
