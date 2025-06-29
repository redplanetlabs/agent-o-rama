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
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest])
  (:import
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]
   [com.rpl.rama.helpers
    TopologyUtils]))

(def SEM)

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

;; TODO: <<<<<>>>> this test will need to set max retries to 0
(deftest retries-checker-test
  (let [orig-foreign-append!  foreign-append!
        stall-emit-nodes-atom (atom #{})
        drop-emits-atom       (atom #{})
        filters-atom          (atom 0)
        received-atom         (atom {})
        checks-atom           (atom 0)
        stalls-atom           (atom 0)]
    (with-redefs
      [SEM (h/mk-semaphore 0)
       retries/SUBSTITUTE-TICK-DEPOT true
       retries/DEFAULT-CHECKER-TICK-MILLIS 10
       retries/checker-threshold-millis short-checker-threshold-millis

       retries/hook:checker-finished
       (fn [] (swap! checks-atom inc))

       retries/hook:stall-detected
       (fn [& args] (swap! stalls-atom inc))

       i/hook:emit> (emits-dropper drop-emits-atom filters-atom)

       i/hook:received-retry
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
                   root-sym  (symbol (po/agent-invoke-task-global-name "foo"))
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
                 (i/mark-virtual-task-complete! *invoke-id)
                 (|origin)
                 (aggs/+count :> *res))
             )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind check-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-check-tick-depot-name "foo")))
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
         (is (= 1
                (-> @received-atom
                    first
                    last)))

         (reset-test!)
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

         ; (rtest/pause-microbatch-topology! ipc
         ;                                   module-name
         ;                                   aor-types/AGENTS-MB-TOPOLOGY-NAME)
         ; (rtest/resume-microbatch-topology! ipc
         ;                                    module-name
         ;                                    aor-types/AGENTS-MB-TOPOLOGY-NAME)
         ;; TODO: <<<<<>>>>>
         ;;   - verify failures going to retry checker
         ;;   - verify it uniques failure requests
         ;;   - check stalling on:
         ;;      - regular node execution
         ;;      - regular node never being received (after the 10s timeout)
         ;;      - agg node execution
         ;;      - something after agg node (verify it keeps going on emits)
         ;;      - agg graph stall
         ;;      - multiple stalls in one agent run get deduplicated
         ;;  - check that it does the broadcast
         ;;  - check that events from prior executions get filtered
         ;;      - can use semaphore to stall the virtual thread invoke, then
         ;;      manually cause a stall and release
         ;;      - store writes
         ;;      - subsequent node invokes
         ;;      - check prime between NodeComplete and processing of emit
         ;;      (there's a filter there)
        )))))
