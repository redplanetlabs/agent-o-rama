(ns com.rpl.gc-test
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

(defn non-gc-vec
  [v]
  (subvec v (- (count v) 3)))

(defn all-agent-invs-fn
  [root-pstate num-tasks]
  (fn []
    (into #{}
          (apply concat
           (for [i (range num-tasks)]
             (foreign-select
              [MAP-KEYS (view #(aor-types/->AgentInvokeImpl i %))]
              root-pstate
              {:pkey i})
           )))))

(defn all-node-ids-fn
  [node-pstate num-tasks]
  (fn []
    (into #{}
          (apply concat
           (for [i (range num-tasks)]
             (foreign-select MAP-KEYS node-pstate {:pkey i})
           )))))

(defn trace-node-ids-fns
  [root-pstate traces-query]
  (let [trace-node-ids
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
              set)))

        non-gc-trace-node-ids
        (fn [invs]
          (set (apply set/union
                (mapv trace-node-ids (non-gc-vec invs)))))]
    [trace-node-ids non-gc-trace-node-ids]
  ))

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
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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
         (bind stream-shared-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-stream-shared-task-global-name "foo")))
         (bind traces-query
           (foreign-query ipc
                          module-name
                          (queries/tracing-query-name "foo")))
         (bind assert-gc-state-empty!
           (fn []
             (let [elems (reduce concat
                          []
                          (for [i (range 4)]
                            (foreign-select [:gc-root-invokes MAP-KEYS]
                                            stream-shared-pstate
                                            {:pkey i})))]
               (when-not (empty? elems)
                 (throw (ex-info "GC PState not empty" {:count (count elems)})))
             )))

         (bind all-agent-invs
           (all-agent-invs-fn root-pstate 4))
         (bind all-node-ids (all-node-ids-fn node-pstate 4))
         (bind [trace-node-ids non-gc-trace-node-ids]
           (trace-node-ids-fns root-pstate traces-query))
         (bind root-count
           (fn [task-id]
             (foreign-select-one STAY root-count-pstate {:pkey task-id})))
         (bind new-invs
           (fn [old-invs task-id amt]
             (reset! forced-task-atom task-id)
             (let [invs (vec (repeatedly amt #(aor/agent-initiate foo)))]
               (doseq [inv invs]
                 (is (= [3 3] (aor/agent-result foo inv))))
               (vec (concat old-invs invs))
             )))

         (foreign-append! config-depot
                          (aor-types/change-max-traces-per-task 3))

         (bind invs-0 (new-invs [] 0 3))

         (is (= 3 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         ;; do many rounds of GC and verify agent IDs and node IDs don't change
         (dotimes [i 3]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)

         (is (= (all-agent-invs) (set invs-0)))

         (is (= (all-node-ids)
                (non-gc-trace-node-ids invs-0)))

         (bind invs-0 (new-invs invs-0 0 2))

         (is (= 5 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         (dotimes [i 7]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)

         (is (= 3 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))

         (is (= (all-agent-invs) (set (non-gc-vec invs-0))))

         (is (= (all-node-ids) (non-gc-trace-node-ids invs-0)))

         (bind invs-1 (new-invs [] 1 3))
         (bind invs-2 (new-invs [] 2 3))
         (bind invs-3 (new-invs [] 3 3))


         (dotimes [i 3]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)
         (is (= (all-agent-invs)
                (set/union
                 (set (non-gc-vec invs-0))
                 (set invs-1)
                 (set invs-2)
                 (set invs-3))))
         (is (= (all-node-ids)
                (set/union (non-gc-trace-node-ids invs-0)
                           (non-gc-trace-node-ids invs-1)
                           (non-gc-trace-node-ids invs-2)
                           (non-gc-trace-node-ids invs-3)
                )))


         (bind invs-0 (new-invs invs-0 0 2))
         (bind invs-1 (new-invs invs-1 1 1))
         (bind invs-2 (new-invs invs-2 2 1))

         ;; get it mid GC
         (dotimes [i 3]
           (foreign-append! gc-depot nil))

         (bind invs-1 (new-invs invs-1 1 1))

         ;; finish GC for everything pending
         (dotimes [i 7]
           (foreign-append! gc-depot nil))
         (assert-gc-state-empty!)
         (is (= (all-agent-invs)
                (set/union
                 (set (non-gc-vec invs-0))
                 (set (non-gc-vec invs-1))
                 (set (non-gc-vec invs-2))
                 (set (non-gc-vec invs-3)))))
         (is (= (all-node-ids)
                (set/union (non-gc-trace-node-ids invs-0)
                           (non-gc-trace-node-ids invs-1)
                           (non-gc-trace-node-ids invs-2)
                           (non-gc-trace-node-ids invs-3)
                )))

         (doseq [i (range 1 4)]
           (is (= 3 (root-count i))))


        )))))

(def RESTART-ATOM)
(def FORCED-VERSION-ATOM)
(def SEM)

(deframaop forced-graph-version
  [*agent-name]
  (deref (var-get (clj! var FORCED-VERSION-ATOM)) :> *ret)
  (:> *ret))

(deftest gc-with-failures-test
  (let [forced-task-atom (atom 0)]
    (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                  RESTART-ATOM (atom nil)
                  FORCED-VERSION-ATOM (atom 0)
                  SEM (h/mk-semaphore 0)
                  apart/next-agent-task (fn [& args] @forced-task-atom)
                  at/fetch-graph-version forced-graph-version
                  anode/log-node-error (fn [& args])]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (-> topology
                (aor/new-agent "foo")
                (aor/set-update-mode :restart)
                (aor/node
                 "a"
                 "b"
                 (fn [agent-node]
                   (aor/emit! agent-node "b")))
                (aor/node
                 "b"
                 "c"
                 (fn [agent-node]
                   (aor/emit! agent-node "c")))
                (aor/node
                 "c"
                 "d"
                 (fn [agent-node]
                   (when (> @RESTART-ATOM 0)
                     (swap! RESTART-ATOM dec)
                     (swap! FORCED-VERSION-ATOM inc)
                     (h/acquire-semaphore SEM 1)
                     (throw (ex-info "fail" {})))
                   (aor/emit! agent-node "d")))
                (aor/node
                 "d"
                 nil
                 (fn [agent-node]
                   (aor/result! agent-node "done")))
            )))
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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
         (bind mb-shared-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-mb-shared-task-global-name "foo")))
         (bind traces-query
           (foreign-query ipc
                          module-name
                          (queries/tracing-query-name "foo")))

         (bind all-agent-invs
           (all-agent-invs-fn root-pstate 4))
         (bind all-node-ids (all-node-ids-fn node-pstate 4))
         (bind [trace-node-ids non-gc-trace-node-ids]
           (trace-node-ids-fns root-pstate traces-query))
         (bind root-count
           (fn [task-id]
             (foreign-select-one STAY root-count-pstate {:pkey task-id})))
         (bind all-valid
           (fn []
             (into #{}
                   (apply concat
                    (for [i (range 4)]
                      (foreign-select [:valid-invokes MAP-KEYS] mb-shared-pstate {:pkey i})
                    )))
           ))

         (foreign-append! config-depot
                          (aor-types/change-max-traces-per-task 2))

         (reset! RESTART-ATOM 1)

         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 0 @RESTART-ATOM)))

         (bind root-invoke-id
           (foreign-select-one [(keypath (:agent-invoke-id inv))
                                :root-invoke-id]
                               root-pstate
                               {:pkey (:task-id inv)}))

         (h/release-semaphore SEM 1)

         (is (= "done" (aor/agent-result foo inv)))
         (is (= 1 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))


         (bind root-invoke-id2
           (foreign-select-one [(keypath (:agent-invoke-id inv))
                                :root-invoke-id]
                               root-pstate
                               {:pkey (:task-id inv)}))

         (is (not= root-invoke-id root-invoke-id2))
         (is (= (all-agent-invs) #{inv}))
         (is (= 1 (count (all-valid))))


         (dotimes [i 5]
           (foreign-append! gc-depot nil))
         (is (= (all-node-ids) (trace-node-ids inv)))

         (bind inv2 (aor/agent-initiate foo))
         (bind inv3 (aor/agent-initiate foo))
         (is (= "done" (aor/agent-result foo inv2)))
         (is (= "done" (aor/agent-result foo inv3)))

         (is (= 1 (count (all-valid))))
         (dotimes [i 5]
           (foreign-append! gc-depot nil))

         (is (= (all-agent-invs) #{inv2 inv3}))
         (is (= (all-node-ids)
                (set/union (trace-node-ids inv2) (trace-node-ids inv3))))

         (is (condition-attained? (= 0 (count (all-valid)))))

         (is (= 2 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))



         (bind r
           (foreign-select-one [(keypath (:agent-invoke-id inv3))
                                :root-invoke-id]
                               root-pstate
                               {:pkey (:task-id inv3)}))

         (bind f1 (aor/agent-initiate-fork foo inv3 {r []}))
         (bind f2 (aor/agent-initiate-fork foo inv3 {r []}))
         (bind f3 (aor/agent-initiate-fork foo inv3 {r []}))
         (bind f4 (aor/agent-initiate-fork foo inv3 {r []}))
         (is (= "done" (aor/agent-result foo f1)))
         (is (= "done" (aor/agent-result foo f2)))
         (is (= "done" (aor/agent-result foo f3)))
         (is (= "done" (aor/agent-result foo f4)))
         (is (= 6 (root-count 0)))

         (dotimes [i 5]
           (foreign-append! gc-depot nil))
         (is (= 2 (root-count 0)))
         (doseq [i (range 1 4)]
           (is (= 0 (root-count i))))
         (is (= (all-agent-invs) #{f3 f4}))
         (is (= (all-node-ids)
                (set/union (trace-node-ids f3) (trace-node-ids f4))))
        )))))

(deftest gc-skips-pending-test
  (let [forced-task-atom (atom 1)]
    (with-redefs [i/SUBSTITUTE-TICK-DEPOTS true
                  SEM (h/mk-semaphore 0)
                  apart/next-agent-task (fn [& args] @forced-task-atom)]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (-> topology
                (aor/new-agent "foo")
                (aor/node
                 "a"
                 "b"
                 (fn [agent-node v]
                   (aor/emit! agent-node "b" v)))
                (aor/node
                 "b"
                 "c"
                 (fn [agent-node v]
                   (when v
                     (h/acquire-semaphore SEM 1))
                   (aor/emit! agent-node "c")))
                (aor/node
                 "c"
                 nil
                 (fn [agent-node]
                   (aor/result! agent-node "done")))
            )))
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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
         (bind traces-query
           (foreign-query ipc
                          module-name
                          (queries/tracing-query-name "foo")))

         (bind all-agent-invs
           (all-agent-invs-fn root-pstate 4))
         (bind all-node-ids (all-node-ids-fn node-pstate 4))
         (bind [trace-node-ids non-gc-trace-node-ids]
           (trace-node-ids-fns root-pstate traces-query))
         (bind root-count
           (fn [task-id]
             (foreign-select-one STAY root-count-pstate {:pkey task-id})))

         (foreign-append! config-depot
                          (aor-types/change-max-traces-per-task 2))

         (bind inv1 (aor/agent-initiate foo true))
         (bind inv2 (aor/agent-initiate foo false))
         (bind inv3 (aor/agent-initiate foo false))

         (is (= "done" (aor/agent-result foo inv2)))
         (is (= "done" (aor/agent-result foo inv3)))

         (is (= 3 (root-count 1)))
         (doseq [i [0 2 3]]
           (is (= 0 (root-count i))))

         (dotimes [i 5]
           (foreign-append! gc-depot nil))

         (is (= 3 (root-count 1)))
         (doseq [i [0 2 3]]
           (is (= 0 (root-count i))))
         (is (= (all-agent-invs) #{inv1 inv2 inv3}))
         (is (= (all-node-ids)
                (set/union (trace-node-ids inv1)
                           (trace-node-ids inv2)
                           (trace-node-ids inv3))))

         (bind inv4 (aor/agent-initiate foo false))
         (is (= "done" (aor/agent-result foo inv4)))
         (is (= 4 (root-count 1)))

         (dotimes [i 5]
           (foreign-append! gc-depot nil))
         (is (= 3 (root-count 1)))
         (doseq [i [0 2 3]]
           (is (= 0 (root-count i))))
         (is (= (all-agent-invs) #{inv1 inv3 inv4}))
         (is (= (all-node-ids)
                (set/union (trace-node-ids inv1)
                           (trace-node-ids inv3)
                           (trace-node-ids inv4))))

         (h/release-semaphore SEM 1)
         (is (= "done" (aor/agent-result foo inv1)))
         (dotimes [i 5]
           (foreign-append! gc-depot nil))
         (is (= 2 (root-count 1)))
         (doseq [i [0 2 3]]
           (is (= 0 (root-count i))))
         (is (= (all-agent-invs) #{inv3 inv4}))
         (is (= (all-node-ids)
                (set/union (trace-node-ids inv3) (trace-node-ids inv4))))
        )))))
