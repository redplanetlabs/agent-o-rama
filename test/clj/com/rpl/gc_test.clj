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
         (bind assert-gc-state-empty!
           (fn []
             (let [elems (reduce concat
                          []
                          (for [i (range 4)]
                            (foreign-select MAP-KEYS gc-pstate {:pkey i})))]
               (when-not (empty? elems)
                 (throw (ex-info "GC PState not empty" {:count (count elems)})))
             )))

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
         (bind root-count
           (fn [task-id]
             (foreign-select-one STAY root-count-pstate {:pkey task-id})))

         (foreign-append! config-depot
                          (aor-types/change-max-traces-per-task 3))

         (bind invs (vec (repeatedly 3 #(aor/agent-initiate foo))))
         (doseq [inv invs]
           (is (= [3 3] (aor/agent-result foo inv))))

         (is (= 3 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         ;; do many rounds of GC and verify agent IDs and node IDs don't change
         (dotimes [i 3]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)

         (is (= (all-agent-invs) (set invs)))

         (is (= (all-node-ids)
                (apply set/union (mapv trace-node-ids invs))))

         (bind invs2 (vec (repeatedly 2 #(aor/agent-initiate foo))))
         (doseq [inv invs2]
           (is (= [3 3] (aor/agent-result foo inv))))

         (is (= 5 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         (dotimes [i 7]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)

         (is (= 3 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         (is (= (all-agent-invs) (conj (set invs2) (last invs))))

         (is (= (all-node-ids)
                (set/union (trace-node-ids (first invs2))
                           (trace-node-ids (second invs2))
                           (trace-node-ids (last invs)))))




         ;; TODO: <<<<>>>>>
         ;;  - start a new invoke mid GC, and verify it GCs another invoke while
         ;;  the other one is running
         ;;  - verify GC of restarted traces (special case)
         ;;  - verify no GC of pending invokes
         ;;  - verify valid-invokes removal
         ;;   - just fail one once, then verify after GC that it gets removed
         ;;     - will need wait-for-mb-processed-count on that mb topology
         ;;  - verify removal from $$gc
         ;;  - verify $$root-count maintained correctly
         ;;    - check with forks, retries, restarts
        )))))
