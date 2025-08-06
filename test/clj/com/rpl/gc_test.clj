(ns com.rpl.gc-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
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

(deftest gc-by-task-test
  (let [forced-task-atom (atom 0)]
    (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                  apart/next-agent-task    (fn [& args] @forced-task-atom)]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (-> topology
                (aor/new-agent "foo")
                (aor/agg-start-node
                 "a"
                 "b"
                 (fn [agent-node]
                   (aor/emit! agent-node "b")
                   (aor/emit! agent-node "b")))
                (aor/agg-start-node
                 "b"
                 "c"
                 (fn [agent-node]
                   (aor/emit! agent-node "c")))
                (aor/node
                 "c"
                 "agg"
                 (fn [agent-node]
                   (aor/emit! agent-node "agg" 1)
                   (aor/emit! agent-node "agg" 2)))
                (aor/agg-node
                 "agg"
                 "d"
                 aggs/+sum
                 (fn [agent-node agg-state node-start-res]
                   (aor/emit! agent-node "d" agg-state)))
                (aor/node
                 "d"
                 "agg2"
                 (fn [agent-node res]
                   (aor/emit! agent-node "agg2" res)))
                (aor/agg-node
                 "agg2"
                 nil
                 aggs/+vec-agg
                 (fn [agent-node agg-state _]
                   (aor/result! agent-node agg-state)))
            )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind config-depot
           (foreign-depot ipc module-name (po/agent-config-depot-name "foo")))
         (bind gc-depot
           (foreign-depot ipc module-name (po/agent-gc-tick-depot-name "foo")))
         (bind root-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-root-task-global-name "foo")))
         (bind root-count-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-root-count-task-global-name "foo")))
         (bind node-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-node-task-global-name "foo")))
         (bind gc-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-gc-invokes-task-global-name "foo")))
         (bind traces-query
           (foreign-query ipc
                          module-name
                          (queries/tracing-query-name "foo")))


         (bind all-agent-invs
           (fn []
             (into #{}
                   (apply concat
                    (for [i (range 4)]
                      (foreign-select
                       [MAP-KEYS (view #(aor-types/->AgentInvokeImpl i %))]
                       root-pstate
                       {:pkey i})
                    )))))
         (bind all-node-ids
           (fn []
             (into #{}
                   (apply concat
                    (for [i (range 4)]
                      (foreign-select MAP-KEYS node-pstate {:pkey i})
                    )))))
         (bind trace-node-ids
           (fn [{:keys [task-id agent-invoke-id]}]
             (let [root-invoke-id (foreign-select-one [(keypath
                                                        agent-invoke-id)
                                                       :root-invoke-id]
                                                      root-pstate
                                                      {:pkey task-id})]
               (->
                 (foreign-invoke-query traces-query
                                       task-id
                                       [[task-id root-invoke-id]]
                                       10000)
                 :invokes-map
                 keys
                 set))))

         (foreign-append! config-depot
                          (aor-types/change-max-traces-per-task 3))

         (bind invs (vec (repeatedly 3 #(aor/agent-initiate foo))))
         (doseq [inv invs]
           (is (= [3 3] (aor/agent-result foo inv))))

         (is (= 3
                (foreign-select-one STAY root-count-pstate {:pkey 0})))
         (doseq [i (range 1 4)]
           (is (=
                0
                (foreign-select-one STAY root-count-pstate {:pkey i}))))



         (bind invs2 (vec (repeatedly 2 #(aor/agent-initiate foo))))
         (doseq [inv invs2]
           (is (= [3 3] (aor/agent-result foo inv))))

         (is (= 5
                (foreign-select-one STAY root-count-pstate {:pkey 0})))
         (doseq [i (range 1 4)]
           (is (=
                0
                (foreign-select-one STAY root-count-pstate {:pkey i}))))

         (dotimes [i 7]
           (println "ROUND" i)
           (foreign-append! gc-depot nil)
           (println "NUM NODES" (count (all-node-ids))))

         (is (= 3
                (foreign-select-one STAY root-count-pstate {:pkey 0})))
         (doseq [i (range 1 4)]
           (is (=
                0
                (foreign-select-one STAY root-count-pstate {:pkey i}))))

         (bind all-invs (all-agent-invs))
         (is (= all-invs (conj (set invs2) (last invs))))

         (bind all-node-ids (all-node-ids))
         (is (= all-node-ids
                (set/union (trace-node-ids (first invs2))
                           (trace-node-ids (second invs2))
                           (trace-node-ids (last invs)))))




         ;; TODO: <<<<>>>
         ;; - capture traces for each
         ;; - gc does nothing at first
         ;;     - check that each trace fully exists after a few rounds
         ;; - this should be 6 rounds worth (put printlns to check)

         ;; TODO: <<<<>>>>>
         ;;  - verify full trace is GC'd after enough iterations
         ;;     - how to actually verify this?
         ;;     - can get trace, then GC it, then check that all nodes are gone
         ;;       - don't have task IDs for all invoke IDs... would have to
         ;;       reconstruct from emits
         ;;         - could add node task ID to trace to make it easier
         ;;         - or just check all tasks
         ;;           - just do query for MAP-KEYS on each partition of $$nodes,
         ;;           and verify all trace IDs are gone
         ;;  - verify GC of restarted traces (special case)
         ;;  - verify removal from $$gc
         ;;  - verify $$root-count maintained correctly
         ;;    - check with forks, retries, restarts
        )))))
