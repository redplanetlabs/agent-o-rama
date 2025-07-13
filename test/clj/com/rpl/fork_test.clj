(ns com.rpl.fork-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [com.rpl.rama.helpers
    TopologyUtils]
   [java.util.concurrent
    CompletableFuture]))

(def GLOBAL-ATOM)

(deftest forking-test
  (tc/with-auto-builder
   (with-redefs [GLOBAL-ATOM (atom 0)]
     (with-open [ipc (rtest/create-ipc)]
       (letlocals
        (bind module
          (aor/agentmodule
           [topology]
           (->
             topology
             (aor/new-agent "foo")
             (tc/auto-node "begin" ["node1" "node2"])
             (tc/auto-node "node1" "start1")
             (tc/auto-node "start1" ["a1" "a2"])
             (tc/auto-node "a1" "agg")
             (tc/auto-node "a2" "agg")
             (tc/auto-node "agg" "node3")
             (tc/auto-node "node3" nil)

             (tc/auto-node "node2" "special1")
             (aor/node
              "special1"
              "special2"
              (fn [agent-node]
                (aor/emit! agent-node "special2" :begin)))
             (aor/node
              "special2"
              "start2"
              (fn [agent-node v]
                (aor/emit! agent-node "start2")))
             (tc/auto-node "start2" "b1")
             (tc/auto-node "b1" "start3")
             (tc/auto-node "start3" "b2")
             (tc/auto-node "b2" "agg2")
             (tc/auto-node "agg2" "b3")
             (tc/auto-node "b3" "agg3")
             (tc/auto-node "agg3" "b4")
             (tc/auto-node "b4" "special3")
             (aor/node
              "special3"
              "special4"
              (fn [agent-node]
                (aor/emit! agent-node "special4" @GLOBAL-ATOM)
                (swap! GLOBAL-ATOM dec)))
             (aor/node
              "special4"
              ["special2" "b5"]
              (fn [agent-node v]
                (if (> v 0)
                  (aor/emit! agent-node "special2" v)
                  (aor/emit! agent-node "b5"))))
             (tc/auto-node "b5" nil)
           )))
        (rtest/launch-module! ipc module {:tasks 4 :threads 2})
        (bind module-name (get-module-name module))

        (bind agent-manager (aor/agent-manager ipc module-name))
        (bind foo (aor/agent-client agent-manager "foo"))
        (bind root-pstate
          (foreign-pstate ipc
                          module-name
                          (po/agent-root-task-global-name "foo")))
        (bind traces-query
          (foreign-query ipc
                         module-name
                         (queries/tracing-query-name "foo")))

        (reset! GLOBAL-ATOM 1)
        (bind inv (aor/agent-initiate foo))
        (bind agent-task-id (.getTaskId inv))
        (bind agent-id (.getAgentInvokeId inv))
        (wait-agent-finished! root-pstate agent-task-id agent-id)

        (bind root-invoke-id
          (foreign-select-one [(keypath agent-id) :root-invoke-id]
                              root-pstate
                              {:pkey agent-task-id}))

        (bind trace
          (:invokes-map
           (foreign-invoke-query traces-query
                                 agent-task-id
                                 [[agent-task-id root-invoke-id]]
                                 10000)))

        (clojure.pprint/pprint trace)

        ;; TODO: <<<<>>>>
        ;;  - make graph similar to retries graph with pluggable node that does
        ;;  the actual result
        ;;      - graph should have a loop in it
        ;;  - get trace, make function to extract invoke IDs for specific nodes
        ;;      - how to target which invocation of a node...
        ;;        - can be based on looking at the input of the node
        ;;  - fork regular node, start agg node within another agg, start agg
        ;;  node not within another agg, agg node within another agg, agg node
        ;;  not within another agg, and combination
       )))))
