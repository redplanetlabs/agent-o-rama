(ns com.rpl.retries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama
    AgentInvoke]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]
   [com.rpl.rama.helpers
    TopologyUtils]))

(def SEM)
(def SEM2)
(def SEM3)

(deframafn short-checker-threshold-millis
  []
  (:> 100))

(defn get-executing-node-ids
  [^AgentNodeExecutorTaskGlobal node-exec]
  (.getRunningInvokeIds node-exec))

(deframafn emits-dropper
  [*atom *filters-atom]
  (<<ramaop %ret
    [*emit]
    (get *emit :node-name :> *node)
    (<<if (contains? @*atom *node)
      (swap! *filters-atom inc)
     (else>)
      (:>)))
  (:> %ret))

(deframaop no-progress-update>
  [])

(deftest retries-checker-test
  (let [orig-foreign-append!  foreign-append!
        stall-emit-nodes-atom (atom #{})
        drop-emits-atom       (atom #{})
        filters-atom          (atom 0)
        received-atom         (atom {})
        checks-atom           (atom 0)
        stalls-atom           (atom 0)
        init-retry-num-atom   (atom 0)]
    (with-redefs
      [retries/SUBSTITUTE-TICK-DEPOT true
       retries/checker-threshold-millis short-checker-threshold-millis

       aor-types/DEFAULT-MAX-RETRIES 0

       retries/hook:checker-finished
       (fn [] (swap! checks-atom inc))

       retries/hook:stall-detected
       (fn [& args] (swap! stalls-atom inc))

       at/hook:emit> (emits-dropper drop-emits-atom filters-atom)

       at/hook:update-last-progress> no-progress-update>

       at/init-retry-num (fn [] @init-retry-num-atom)

       at/hook:received-retry
       (fn [agent-task-id agent-id expected-retry-num]
         (transform [ATOM (keypath [agent-task-id agent-id]) (nil->val 0)]
                    inc
                    received-atom))

       foreign-append!
       (fn this
         ([depot data]
          (this depot data :ack))
         ([depot data ack-level]
          (if (and (aor-types/NodeComplete? data)
                   (selected-any? [:emits ALL :node-name
                                   #(contains? @stall-emit-nodes-atom %)]
                                  data))
            (do
              (swap! filters-atom inc)
              {})
            (orig-foreign-append! depot data ack-level)
          )))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (declare-depot setup *reset-depot :random {:global? true})
             (let [topology  (aor/agents-topology setup topologies)
                   s         (aor/underlying-stream-topology topology)
                   node-exec (symbol (po/agent-node-executor-name))
                   root-sym  (symbol (po/agent-root-task-global-name "foo"))
                   agent-active-invokes-pstate-sym
                   (symbol (po/agent-active-invokes-task-global-name "foo"))]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  "node1"
                  (fn [agent-node]
                    (aor/emit! agent-node "node1")))
                 (aor/agg-start-node
                  "node1"
                  ["node2" "node3"]
                  (fn [agent-node]
                    (aor/emit! agent-node "node2")
                    (aor/emit! agent-node "node3")
                    (aor/emit! agent-node "node2")))
                 (aor/node
                  "node2"
                  "agg"
                  (fn [agent-node]
                    (aor/emit! agent-node "agg" 1)))
                 (aor/node
                  "node3"
                  "node4"
                  (fn [agent-node]
                    (aor/emit! agent-node "node4")))
                 (aor/node
                  "node4"
                  "agg"
                  (fn [agent-node]
                    (aor/emit! agent-node "agg" 10)))
                 (aor/agg-node
                  "agg"
                  "next1"
                  aggs/+sum
                  (fn [agent-node agg node-start-res]
                    (aor/emit! agent-node "next1" agg)))
                 (aor/node
                  "next1"
                  "next2"
                  (fn [agent-node res]
                    (aor/emit! agent-node "next2" res)))
                 (aor/node
                  "next2"
                  nil
                  (fn [agent-node res]
                    (aor/result! agent-node res)))
               )
               (aor/define-agents! topology)
               (<<sources s
                (source> *reset-depot :> _)
                 (|all)
                 (local-transform> [MAP-VALS NONE>] root-sym)
                 (local-transform> [MAP-VALS NONE>]
                                   agent-active-invokes-pstate-sym))
               (<<query-topology topologies
                 "clear-pending"
                 [:> *res]
                 (|all)
                 (get-executing-node-ids node-exec :> *invoke-ids)
                 (ops/explode *invoke-ids :> *invoke-id)
                 (at/mark-virtual-task-complete! *invoke-id)
                 (|origin)
                 (aggs/+count :> *res))
             )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind check-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-check-tick-depot-name "foo")))
         (bind valid-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-valid-invokes-task-global-name "foo")))
         (bind reset-depot (foreign-depot ipc module-name "*reset-depot"))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind clear-q
           (foreign-query ipc module-name "clear-pending"))

         (bind checker-progress!
           (fn []
             (let [s @checks-atom]
               (foreign-append! check-depot nil)
               (is (condition-attained? (= (+ 1 s) @checks-atom)))
             )))

         (bind reset-test!
           (fn []
             (reset! received-atom {})
             (reset! stalls-atom 0)
             (reset! checks-atom 0)
             (reset! stall-emit-nodes-atom #{})
             (reset! drop-emits-atom #{})
             (reset! filters-atom 0)
             (foreign-append! reset-depot nil)))

         (bind all-tasks-retry-num?
           (fn [^AgentInvoke inv retry-num]
             (let [agent-task-id (.getTaskId inv)
                   invoke-id     (.getAgentInvokeId inv)]
               (every?
                (fn [task-id]
                  (= retry-num
                     (foreign-select-one
                      (keypath [agent-task-id invoke-id])
                      valid-pstate
                      {:pkey task-id})
                  ))
                (range 4)))))

         ;; check stall on a node not completing execution
         (reset! stall-emit-nodes-atom #{"node1"})
         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 1 @filters-atom)))

         (checker-progress!)
         (is (= 0 @stalls-atom))
         (TopologyUtils/advanceSimTime 100)
         (checker-progress!)
         ;; because haven't cleared the execution state yet
         (is (= 0 @stalls-atom))
         (is (condition-attained? (= 1 (foreign-invoke-query clear-q))))
         (checker-progress!)
         (is (= 1 @stalls-atom))
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (all-tasks-retry-num? inv 1))
         (is (= 1
                (-> @received-atom
                    first
                    last)))

         ;; now check stall happening on an emit from a finished node not making
         ;; it
         (reset-test!)
         (reset! init-retry-num-atom 2)
         (reset! drop-emits-atom #{"next2"})
         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 1 @filters-atom)))

         (checker-progress!)
         (is (= 0 @stalls-atom))
         (TopologyUtils/advanceSimTime 100)
         (checker-progress!)
         ;; because this time it cleared the execution state on its own
         (is (condition-attained? (= 1 @stalls-atom)))
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= 1
                (-> @received-atom
                    first
                    last)))
         (is (all-tasks-retry-num? inv 3))


         ;; now check stall happening on agg node execution
         (reset-test!)
         (reset! stall-emit-nodes-atom #{"next1"})
         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 1 @filters-atom)))

         (checker-progress!)
         (is (= 0 @stalls-atom))
         (TopologyUtils/advanceSimTime 100)
         (checker-progress!)
         ;; because haven't cleared the execution state yet
         (is (= 0 @stalls-atom))
         (is (condition-attained? (= 1 (foreign-invoke-query clear-q))))
         (checker-progress!)
         (is (= 1 @stalls-atom))
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= 1
                (-> @received-atom
                    first
                    last)))

         ;; now check stall happening within an agg graph
         (reset-test!)
         (reset! stall-emit-nodes-atom #{"node4"})
         (reset! drop-emits-atom #{"agg"})
         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 3 @filters-atom)))
         (is (condition-attained? (= 1 (foreign-invoke-query clear-q))))
         (TopologyUtils/advanceSimTime 100)
         (checker-progress!)
         (is (= 1 @stalls-atom))
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= 1
                (-> @received-atom
                    first
                    last)))
        )))))

(def +bad-init-agg
  (accumulator
   (fn [v]
     (term inc))
   :init-fn
   (fn []
     (throw (ex-info "fail init" {})))))

(def +bad-update-agg
  (accumulator
   (fn [v]
     (throw (ex-info "bad update" {})))
   :init-fn
   (fn [] 0)))


(deftest failure-processing-test
  (let [received-atom        (atom {})
        failure-appends-atom (atom 0)
        init-retry-num-atom  (atom 0)]
    (with-redefs
      [retries/SUBSTITUTE-TICK-DEPOT true
       at/init-retry-num (fn [] @init-retry-num-atom)

       aor-types/DEFAULT-MAX-RETRIES 0

       i/log-node-error (fn [& args])

       at/hook:appended-agent-failure (fn [& args]
                                        (swap! failure-appends-atom inc))

       at/hook:received-retry
       (fn [agent-task-id agent-id expected-retry-num]
         (transform [ATOM
                     (keypath [agent-task-id agent-id expected-retry-num])
                     (nil->val 0)]
                    inc
                    received-atom))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (declare-depot setup *reset-depot :random {:global? true})
             (let [topology  (aor/agents-topology setup topologies)
                   s         (aor/underlying-stream-topology topology)
                   node-exec (symbol (po/agent-node-executor-name))
                   root-sym  (symbol (po/agent-root-task-global-name "foo"))
                   agent-active-invokes-pstate-sym
                   (symbol (po/agent-active-invokes-task-global-name "foo"))]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  "node1"
                  (fn [agent-node v]
                    (when (= v :fail)
                      (throw (ex-info "fail node" {})))
                    (aor/emit! agent-node "node1")))
                 (aor/agg-start-node
                  "node1"
                  "agg"
                  (fn [agent-node]
                    (aor/emit! agent-node "agg" 1)
                    (aor/emit! agent-node "agg" 1)))
                 (aor/agg-node
                  "agg"
                  nil
                  +bad-init-agg
                  (fn [agent-node agg node-start-res]
                    (aor/result! agent-node agg)))
               )
               (->
                 topology
                 (aor/new-agent "bar")
                 (aor/node
                  "start"
                  "node1"
                  (fn [agent-node]
                    (aor/emit! agent-node "node1")))
                 (aor/agg-start-node
                  "node1"
                  "agg"
                  (fn [agent-node]
                    (aor/emit! agent-node "agg" 1)))
                 (aor/agg-node
                  "agg"
                  nil
                  +bad-update-agg
                  (fn [agent-node agg node-start-res]
                    (aor/result! agent-node agg)))
               )
               (aor/define-agents! topology)
             )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind bar-failures-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-failures-depot-name "bar")))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind bar (aor/agent-client agent-manager "bar"))

         (bind reset-test!
           (fn []
             (reset! failure-appends-atom 0)
             (reset! received-atom {})))

         (bind inv (aor/agent-initiate foo :fail))
         (is (condition-attained? (= 1 @failure-appends-atom)))
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= 1
                (-> @received-atom
                    first
                    last)))


         (reset-test!)
         (aor/agent-initiate foo 1)
         (is (condition-attained? (= 1 @failure-appends-atom)))
         (is (condition-attained? (= 1 (count @received-atom))))

         (reset-test!)
         (aor/agent-initiate bar)
         (is (condition-attained? (= 1 @failure-appends-atom)))
         (is (condition-attained? (= 1 (count @received-atom))))

         ;; verify mutiple failures for same invoke only result in one retry
         (reset-test!)
         (rtest/pause-microbatch-topology! ipc
                                           module-name
                                           aor-types/AGENTS-MB-TOPOLOGY-NAME)
         (bind inv (aor/agent-initiate bar))
         (is (condition-attained? (= 1 @failure-appends-atom)))

         (dotimes [_ 2]
           (foreign-append! bar-failures-depot
                            (aor-types/->AgentFailure
                             (.getTaskId inv)
                             (.getAgentInvokeId inv)
                             0)))
         (rtest/resume-microbatch-topology! ipc
                                            module-name
                                            aor-types/AGENTS-MB-TOPOLOGY-NAME)

         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= {[(.getTaskId inv)
                  (.getAgentInvokeId inv)
                  0]
                 1}
                @received-atom))

         ;; verify when retry num is off, it does not issue the retry
         (reset-test!)
         (rtest/pause-microbatch-topology! ipc
                                           module-name
                                           aor-types/AGENTS-MB-TOPOLOGY-NAME)
         (reset! init-retry-num-atom 2)
         (bind inv2 (aor/agent-initiate bar))
         (is (condition-attained? (= 1 @failure-appends-atom)))
         (foreign-append! bar-failures-depot
                          (aor-types/->AgentFailure
                           (.getTaskId inv)
                           (.getAgentInvokeId inv)
                           1))
         (rtest/resume-microbatch-topology! ipc
                                            module-name
                                            aor-types/AGENTS-MB-TOPOLOGY-NAME)
         (is (condition-attained? (= 1 (count @received-atom))))
         (is (= {[(.getTaskId inv2)
                  (.getAgentInvokeId inv2)
                  2]
                 1}
                @received-atom))
        )))))

(def BLOCKED-NODES-ATOM)
(def EVENTS-ATOM)

(deftest filtered-events-test
  (let [retries-atom (atom 0)]
    (with-redefs
      [SEM (h/mk-semaphore 0)
       SEM2 (h/mk-semaphore 0)
       SEM3 (h/mk-semaphore 0)

       BLOCKED-NODES-ATOM (atom 0)
       EVENTS-ATOM (atom [])

       aor-types/DEFAULT-MAX-RETRIES 0

       i/log-node-error (fn [& args])

       retries/SUBSTITUTE-TICK-DEPOT true

       at/hook:filtered-event (fn [& args] (swap! EVENTS-ATOM conj :filter))

       at/hook:received-retry
       (fn [agent-task-id agent-id expected-retry-num]
         (swap! retries-atom inc))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (let [topology (aor/agents-topology setup topologies)]
               (aor/declare-key-value-store
                topology
                "$$kv"
                clojure.lang.Keyword
                Object)
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  ["node1" "node2" "node3"]
                  (fn [agent-node]
                    (aor/emit! agent-node "node1")
                    (aor/emit! agent-node "node2")
                    (aor/emit! agent-node "node3")
                  ))
                 (aor/node
                  "node1"
                  nil
                  (fn [agent-node]
                    (let [kv (aor/get-store agent-node "$$kv")]
                      (store/put! kv :a 1)
                      (swap! BLOCKED-NODES-ATOM inc)
                      (h/acquire-semaphore SEM 1)
                      (swap! EVENTS-ATOM conj (store/get kv :a))
                      (try
                        (store/put! kv :a 10)
                        (catch Exception e
                          (swap! EVENTS-ATOM conj :exc)
                        ))
                    )))
                 (aor/node
                  "node2"
                  nil
                  (fn [agent-node]
                    (h/acquire-semaphore SEM2 1)
                    (throw (ex-info "fail" {}))
                  ))
                 (aor/node
                  "node3"
                  nil
                  (fn [agent-node]
                    (swap! BLOCKED-NODES-ATOM inc)
                    (h/acquire-semaphore SEM3 1)
                    (throw (ex-info "another fail" {}))
                  ))
               )
               (aor/define-agents! topology)
             )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))

         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 2 @BLOCKED-NODES-ATOM)))
         (h/release-semaphore SEM2 1)
         ;; this means tasks have all been primed
         (is (condition-attained? (= 1 @retries-atom)))
         (h/release-semaphore SEM 1)

         (is (condition-attained? (= @EVENTS-ATOM [1 :exc :filter])))

         (reset! EVENTS-ATOM [])
         (h/release-semaphore SEM3 1)
         (is (condition-attained? (= @EVENTS-ATOM [:filter])))
        )))))
