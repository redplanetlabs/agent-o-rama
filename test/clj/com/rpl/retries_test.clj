(ns com.rpl.retries-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.string :as str]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.partitioner :as apart]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.retries :as retries]
   [com.rpl.agent-o-rama.impl.topology :as at]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
   [com.rpl.agentorama
    AgentFailedException
    AgentInvoke]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]
   [com.rpl.rama.helpers
    TopologyUtils]
   [dev.langchain4j.data.message
    AiMessage]
   [dev.langchain4j.model.chat.response
    ChatResponse$Builder]
   [dev.langchain4j.model.chat
    ChatModel
    StreamingChatModel]
   [dev.langchain4j.model.chat.response
    ChatResponse]
   [dev.langchain4j.model.output
    FinishReason
    TokenUsage]
   [java.util.concurrent
    CompletableFuture]))

(def SEM)
(def SEM2)
(def SEM3)

(deframafn short-checker-threshold-millis
  [*agent-name]
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
      [i/SUBSTITUTE-TICK-DEPOTS true
       retries/checker-threshold-millis short-checker-threshold-millis

       aor-types/get-config (max-retries-override 0)

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
             (let [topology  (aor/agent-topology setup topologies)
                   s         (aor/underlying-stream-topology topology)
                   node-exec (symbol (po/agent-node-executor-name))
                   root-sym  (symbol (po/agent-root-task-global-name "foo"))
                   agent-stream-shared-pstate-sym
                   (symbol (po/agent-stream-shared-task-global-name "foo"))]
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
                 (local-transform> [:active-invokes ALL NONE>]
                                   agent-stream-shared-pstate-sym))
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
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind check-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-check-tick-depot-name "foo")))
         (bind mb-shared-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-mb-shared-task-global-name "foo")))
         (bind reset-depot (foreign-depot ipc module-name "*reset-depot"))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind clear-q
           (foreign-query ipc module-name "clear-pending"))

         (bind checker-progress!
           (fn []
             (let [s @checks-atom]
               (foreign-append! check-depot nil)
               (when-not (condition-attained? (= (+ 1 s) @checks-atom))
                 (throw (ex-info "Didn't make progress" {:checks @checks-atom :s s})))
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
                      (keypath :valid-invokes [agent-task-id invoke-id])
                      mb-shared-pstate
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

         ;; now check stall happening on an emit from a finished node not making it
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
      [i/SUBSTITUTE-TICK-DEPOTS true
       at/init-retry-num        (fn [] @init-retry-num-atom)

       aor-types/get-config     (max-retries-override 0)

       anode/log-node-error     (fn [& args])

       anode/hook:appended-agent-failure (fn [& args]
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
             (let [topology  (aor/agent-topology setup topologies)
                   s         (aor/underlying-stream-topology topology)
                   node-exec (symbol (po/agent-node-executor-name))
                   root-sym  (symbol (po/agent-root-task-global-name "foo"))]
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
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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
                                           aor-types/AGENT-MB-TOPOLOGY-NAME)
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
                                            aor-types/AGENT-MB-TOPOLOGY-NAME)

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
                                           aor-types/AGENT-MB-TOPOLOGY-NAME)
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
                                            aor-types/AGENT-MB-TOPOLOGY-NAME)
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

       aor-types/get-config (max-retries-override 0)

       anode/log-node-error (fn [& args])

       i/SUBSTITUTE-TICK-DEPOTS true

       apart/hook:filtered-event (fn [& args] (swap! EVENTS-ATOM conj :filter))

       at/hook:received-retry
       (fn [agent-task-id agent-id expected-retry-num]
         (swap! retries-atom inc))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (let [topology (aor/agent-topology setup topologies)]
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
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
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

(deftest fork-affected-aggs-query-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (module
         [setup topologies]
         (let [topology (aor/agent-topology setup topologies)]
           (->
             topology
             (aor/new-agent "foo")
             (aor/node
              "start"
              ["node1" "node2"]
              (fn [agent-node]
                (aor/emit! agent-node "node1")
                (aor/emit! agent-node "node2")))
             (aor/node
              "node1"
              "start1"
              (fn [agent-node]
                (aor/emit! agent-node "start1")))
             (aor/agg-start-node
              "start1"
              ["a1" "a2"]
              (fn [agent-node]
                (aor/emit! agent-node "a1")
                (aor/emit! agent-node "a2")))
             (aor/node
              "a1"
              "agg"
              (fn [agent-node]
                (aor/emit! agent-node "agg" 1)))
             (aor/node
              "a2"
              "agg"
              (fn [agent-node]
                (aor/emit! agent-node "agg" 2)))
             (aor/agg-node
              "agg"
              "node3"
              aggs/+vec-agg
              (fn [agent-node agg node-start-res]
                (aor/emit! agent-node "node3")))
             (aor/node
              "node3"
              nil
              (fn [agent-node]
                (aor/result! agent-node "abc")))

             (aor/node
              "node2"
              "start2"
              (fn [agent-node]
                (aor/emit! agent-node "start2")))
             (aor/agg-start-node
              "start2"
              "b1"
              (fn [agent-node]
                (aor/emit! agent-node "b1")))
             (aor/node
              "b1"
              "start3"
              (fn [agent-node]
                (aor/emit! agent-node "start3")))
             (aor/agg-start-node
              "start3"
              "b2"
              (fn [agent-node]
                (aor/emit! agent-node "b2")))
             (aor/node
              "b2"
              "agg2"
              (fn [agent-node]
                (aor/emit! agent-node "agg2" 1)))
             (aor/agg-node
              "agg2"
              "b3"
              aggs/+vec-agg
              (fn [agent-node agg node-start-res]
                (aor/emit! agent-node "b3")))
             (aor/node
              "b3"
              "agg3"
              (fn [agent-node]
                (aor/emit! agent-node "agg3" 1)))
             (aor/agg-node
              "agg3"
              "b4"
              aggs/+vec-agg
              (fn [agent-node agg node-start-res]
                (aor/emit! agent-node "b4")))
             (aor/node
              "b4"
              nil
              (fn [agent-node]
              ))
           )
           (aor/define-agents! topology)
         )))
     (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client manager "foo"))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind affected-aggs-query
       (foreign-query ipc
                      module-name
                      (queries/agent-get-fork-affected-aggs-query-name)))

     (bind [agent-task-id agent-id]
       (invoke-agent-and-wait! depot root-pstate []))

     (bind root-invoke-id
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind trace
       (:invokes-map (foreign-invoke-query traces-query
                                           agent-task-id
                                           [[agent-task-id root-invoke-id]]
                                           10000)))

     (bind inv-id
       (fn [node]
         (select-one! [ALL (selected? LAST :node (pred= node)) FIRST]
                      trace)))

     (bind start1 (inv-id "start1"))
     (bind start2 (inv-id "start2"))
     (bind start3 (inv-id "start3"))

     (is (= #{start2 start3}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "b2")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "b3")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                "foo"
                                agent-task-id
                                agent-id
                                #{(inv-id "b4")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                "foo"
                                agent-task-id
                                agent-id
                                #{(inv-id "start")})))
     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                "foo"
                                agent-task-id
                                agent-id
                                #{(inv-id "b4") (inv-id "node1")
                                  (inv-id "node2")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{start2})))

     (is (= #{start2 start3}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{start3})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                "foo"
                                agent-task-id
                                agent-id
                                #{(inv-id "agg")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                "foo"
                                agent-task-id
                                agent-id
                                #{(inv-id "agg3")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "agg2")})))

     (is (= #{start1 start2}
            (foreign-invoke-query affected-aggs-query
                                  "foo"
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "a1") (inv-id "b1")})))
    )))

(def CF-ATOM)

(deframaop cf-running-retry>
  [*agent-task-id *agent-id *retry-num]
  (tc/mk-cf :> *cf)
  (reset! (var-get (clj! var CF-ATOM)) *cf)
  (completable-future> *cf)
  (:>))

(deftest retry-on-failure-test
  (let [failures-atom (atom 0)]
    (tc/with-auto-builder
     (with-redefs [CF-ATOM (atom nil)
                   anode/log-node-error (fn [& args])
                   at/hook:running-retry> cf-running-retry>
                   aor-types/get-config (max-retries-override 3)

                   anode/hook:appended-agent-failure
                   (fn [& args] (swap! failures-atom inc))]
       (with-open [ipc (rtest/create-ipc)]
         (letlocals
          (bind module
            (aor/agentmodule
             [topology]
             (->
               topology
               (aor/new-agent "foo")
               ;; verify this is ignored
               (aor/set-update-mode (rand-nth [:drop :continue :restart]))
               (tc/auto-node "begin" ["node1" "node2"])
               (tc/auto-node "node1" "start1")
               (tc/auto-node "start1" ["a1" "a2"])
               (tc/auto-node "a1" "agg")
               (tc/auto-node "a2" "agg")
               (tc/auto-node "agg" "node3")
               (tc/auto-node "node3" nil)

               (tc/auto-node "node2" "start2")
               (tc/auto-node "start2" "b1")
               (tc/auto-node "b1" "start3")
               (tc/auto-node "start3" "b2")
               (tc/auto-node "b2" "agg2")
               (tc/auto-node "agg2" "b3")
               (tc/auto-node "b3" "agg3")
               (tc/auto-node "agg3" "b4")
               (tc/auto-node "b4" nil)
             )))
          (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
          (bind module-name (get-module-name module))

          (bind agent-manager (aor/agent-manager ipc module-name))
          (bind foo (aor/agent-client agent-manager "foo"))
          (bind stream-shared-pstate
            (foreign-pstate ipc
                            module-name
                            (po/agent-stream-shared-task-global-name "foo")))

          (bind check-active!
            (fn [expected]
              (let [c (reduce
                       (fn [c task-id]
                         (+ c
                            (foreign-select-one [:active-invokes (view count)]
                                                stream-shared-pstate
                                                {:pkey task-id})))
                       0
                       (range 4))]
                (when-not (= c expected)
                  (throw (ex-info "Mismatched active count"
                                  {:expected expected :count c})))
              )))

          (bind fail-and-retry!
            (fn [result-node num-fails nodes]
              (letlocals
               (reset! tc/AGG-RESULTS-ATOM {})
               (reset! tc/FAIL-NODES-ATOM (set nodes))
               (reset! tc/RAN-NODES-ATOM {})
               (reset! tc/RESULT-NODE-ATOM result-node)
               (reset! failures-atom 0)
               (reset! CF-ATOM nil)
               (rtest/pause-microbatch-topology!
                ipc
                module-name
                aor-types/AGENT-MB-TOPOLOGY-NAME)
               (bind inv (aor/agent-initiate foo 1))
               (when-not (condition-attained? (= (count nodes) @failures-atom))
                 (throw (ex-info "Didn't reach initial failures" {})))
               (rtest/resume-microbatch-topology!
                ipc
                module-name
                aor-types/AGENT-MB-TOPOLOGY-NAME)
               (dotimes [i (min (dec num-fails) 3)]
                 (check-active! 1)
                 (reset! failures-atom 0)
                 (when-not (condition-attained? (some? @CF-ATOM))
                   (throw (ex-info "Did not reach CF" {})))
                 (rtest/pause-microbatch-topology!
                  ipc
                  module-name
                  aor-types/AGENT-MB-TOPOLOGY-NAME)
                 (tc/complete-cf! @CF-ATOM true)
                 (when-not (condition-attained? (= (count nodes)
                                                   @failures-atom))
                   (throw (ex-info "Didn't reach failures" {})))
                 (reset! CF-ATOM nil)
                 (rtest/resume-microbatch-topology!
                  ipc
                  module-name
                  aor-types/AGENT-MB-TOPOLOGY-NAME)
               )
               (reset! tc/FAIL-NODES-ATOM #{})
               (when-not (condition-attained? (some? @CF-ATOM))
                 (throw (ex-info "Did not reach CF at end" {})))
               (tc/complete-cf! @CF-ATOM true)
               inv
              )))

          (bind verify!
            (fn [m]
              (doseq [[k v] m]
                (let [actual (get @tc/RAN-NODES-ATOM k)]
                  (when (not= v actual)
                    (throw (ex-info "Matches failed!"
                                    {:node k :expected v :actual actual})))
                ))
              (check-active! 0)
              ;; distinct because a node on the other path can be running when the
              ;; failure's retry invalidates its attempt. Its completion is then
              ;; dropped and it runs again, so it can record the same value twice.
              ;; What matters here is that the value is right: a retry that didn't
              ;; reset a partial aggregation would change it.
              (when-not (condition-attained?
                         (= (transform MAP-VALS #(vec (distinct %)) @tc/AGG-RESULTS-ATOM)
                            {"agg" [[1 1]] "agg2" [[1]] "agg3" [[[1]]]}))
                (throw (ex-info "Agg failed" {:result @tc/AGG-RESULTS-ATOM})))
            ))

          (bind inv (fail-and-retry! "node3" 1 ["node1"]))
          (is (= "node3" (aor/agent-result foo inv)))
          (verify! {"node1" 2 "begin" 1})

          (bind inv (fail-and-retry! "node3" 2 ["node1"]))
          (is (= "node3" (aor/agent-result foo inv)))
          (verify! {"node1" 3 "begin" 1})

          (bind inv (fail-and-retry! "node3" 3 ["node1"]))
          (is (= "node3" (aor/agent-result foo inv)))
          (verify! {"node1" 4 "begin" 1})

          (bind inv (fail-and-retry! "node3" 4 ["node1"]))
          (is (thrown? Exception (aor/agent-result foo inv)))

          ;; this one can partially aggregate, so it verifies the retry resets
          ;; properly
          (bind inv (fail-and-retry! "node3" 1 ["a2"]))
          (is (= "node3" (aor/agent-result foo inv)))
          (verify! {"begin" 1 "node1" 1 "start1" 1 "a2" 2 "agg" 1 "node3" 1})

          (bind inv (fail-and-retry! "b4" 2 ["b1"]))
          (is (= "b4" (aor/agent-result foo inv)))
          (verify! {"begin"  1
                    "node2"  1
                    "start2" 1
                    "b1"     3
                    "start3" 1
                    "b2"     1
                    "agg2"   1
                    "b3"     1
                    "agg3"   1
                    "b4"     1})

          (bind inv (fail-and-retry! "b4" 2 ["agg2"]))
          (is (= "b4" (aor/agent-result foo inv)))
          (verify! {"begin"  1
                    "node2"  1
                    "start2" 1
                    "b1"     1
                    "start3" 1
                    "b2"     1
                    "agg2"   3
                    "b3"     1
                    "agg3"   1
                    "b4"     1})

          (bind inv (fail-and-retry! "b4" 1 ["start3"]))
          (is (= "b4" (aor/agent-result foo inv)))
          (verify! {"begin"  1
                    "node2"  1
                    "start2" 1
                    "b1"     1
                    "start3" 2
                    "b2"     1
                    "agg2"   1
                    "b3"     1
                    "agg3"   1
                    "b4"     1})

          (bind inv (fail-and-retry! "b4" 1 ["start2" "agg"]))
          (is (= "b4" (aor/agent-result foo inv)))
          (verify! {"begin"  1
                    "node2"  1
                    "start2" 2
                    "b1"     1
                    "start3" 1
                    "b2"     1
                    "agg2"   1
                    "b3"     1
                    "agg3"   1
                    "b4"     1

                    "node1"  1
                    "start1" 1
                    "a1"     1
                    "a2"     1
                    ;; since completion is on other path, can't say for sure how
                    ;; many times reset of this path runs
                   })
         ))))))

(def FORCED-VERSION-ATOM)

(deframaop forced-graph-version
  [*agent-name]
  (deref (var-get (clj! var FORCED-VERSION-ATOM)) :> *ret)
  (:> *ret))


(deftest update-modes-test
  (let [failures-atom (atom 0)
        retries-atom  (atom 0)]
    (with-redefs [FORCED-VERSION-ATOM    (atom 0)
                  at/fetch-graph-version forced-graph-version
                  anode/log-node-error   (fn [& args])

                  at/hook:received-retry (fn [& args] (swap! retries-atom inc))

                  anode/hook:appended-agent-failure
                  (fn [& args] (swap! failures-atom inc))]
      (tc/with-auto-builder
       (with-open [ipc (rtest/create-ipc)]
         (letlocals
          (bind module
            (aor/agentmodule
             [topology]
             (->
               topology
               (aor/new-agent "foo")
               (aor/set-update-mode :continue)
               (tc/auto-node "begin" "node1")
               (tc/auto-node "node1" "node2")
               (tc/auto-node "node2" nil))
             (->
               topology
               (aor/new-agent "bar")
               (aor/set-update-mode :restart)
               (tc/auto-node "begin" "node1")
               (tc/auto-node "node1" "node2")
               (tc/auto-node "node2" nil))
             (->
               topology
               (aor/new-agent "car")
               (aor/set-update-mode :drop)
               (tc/auto-node "begin" "node1")
               (tc/auto-node "node1" "node2")
               (tc/auto-node "node2" nil))
            ))
          (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
          (bind module-name (get-module-name module))

          (bind agent-manager (aor/agent-manager ipc module-name))
          (bind foo (aor/agent-client agent-manager "foo"))
          (bind bar (aor/agent-client agent-manager "bar"))
          (bind car (aor/agent-client agent-manager "car"))

          (bind stream-shared
            (vec
             (for [n ["foo" "bar" "car"]]
               (foreign-pstate ipc
                               module-name
                               (po/agent-stream-shared-task-global-name n)))))

          (bind check-active!
            (fn [expected]
              (let [c (reduce
                       (fn [c task-id]
                         (reduce
                          (fn [c ap]
                            (+ c
                               (foreign-select-one [:active-invokes (view count)]
                                                   ap
                                                   {:pkey task-id})))
                          c
                          stream-shared))
                       0
                       (range 4))]
                (when-not (= c expected)
                  (throw (ex-info "Mismatched active count"
                                  {:expected expected :count c})))
              )))


          (reset! tc/RESULT-NODE-ATOM "node2")

          (reset! tc/FAIL-NODES-ATOM #{"node1"})
          (reset! failures-atom 0)
          (rtest/pause-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (bind inv (aor/agent-initiate foo))
          (is (condition-attained? (= 1 @failures-atom)))
          (check-active! 1)
          (reset! tc/FAIL-NODES-ATOM #{})
          (reset! FORCED-VERSION-ATOM 1)
          (rtest/resume-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (is (= "node2" (aor/agent-result foo inv)))
          (is (= {"begin" 1 "node1" 2 "node2" 1} @tc/RAN-NODES-ATOM))
          (check-active! 0)
          (is (= 1 @retries-atom))


          (reset! tc/FAIL-NODES-ATOM #{"node1"})
          (reset! FORCED-VERSION-ATOM 0)
          (reset! failures-atom 0)
          (reset! tc/RAN-NODES-ATOM {})
          (rtest/pause-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (bind inv (aor/agent-initiate bar))
          (is (condition-attained? (= 1 @failures-atom)))
          (check-active! 1)
          (reset! tc/FAIL-NODES-ATOM #{})
          (reset! FORCED-VERSION-ATOM 1)
          (rtest/resume-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (is (= "node2" (aor/agent-result bar inv)))
          (is (= {"begin" 2 "node1" 2 "node2" 1} @tc/RAN-NODES-ATOM))
          (check-active! 0)
          (is (= 2 @retries-atom))


          (reset! tc/FAIL-NODES-ATOM #{"node1"})
          (reset! FORCED-VERSION-ATOM 0)
          (reset! failures-atom 0)
          (reset! tc/RAN-NODES-ATOM {})
          (rtest/pause-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (bind inv (aor/agent-initiate car))
          (is (condition-attained? (= 1 @failures-atom)))
          (check-active! 1)
          (reset! tc/FAIL-NODES-ATOM #{})
          (reset! FORCED-VERSION-ATOM 1)
          (rtest/resume-microbatch-topology!
           ipc
           module-name
           aor-types/AGENT-MB-TOPOLOGY-NAME)
          (try
            (aor/agent-result car inv)
            (is false)
            (catch java.util.concurrent.ExecutionException e
              (is
               (=
                "Retry dropped (last failure: clojure.lang.ExceptionInfo: Intentional {})"
                (ex-message (.getCause e))))
            ))
          (is (= {"begin" 1 "node1" 1} @tc/RAN-NODES-ATOM))
          (check-active! 0)
          (is (= 3 @retries-atom))
         ))))))


(def VAL-ATOM)

(defrecord MockChatModel []
  ChatModel
  (doChat [this request]
    (let [v (swap! VAL-ATOM dec)]
      (if (even? v)
        (-> (ChatResponse$Builder.)
            (.aiMessage (AiMessage. "!!!"))
            (.finishReason FinishReason/STOP)
            (.modelName "aor-model")
            (.tokenUsage (TokenUsage. (int 10) (int 20)))
            .build)
        (throw (ex-info "intentional" {:v v}))))))

(defrecord MockStreamingChatModel []
  StreamingChatModel
  (doChat [this request handler]
    (let [response (-> (ChatResponse$Builder.)
                       (.aiMessage (AiMessage. "!!!"))
                       (.finishReason FinishReason/LENGTH)
                       (.modelName "s-aor-model")
                       (.tokenUsage (TokenUsage. (int 10) (int 20)))
                       .build)
          v        @VAL-ATOM]
      (if (or (odd? v) (= 0 v))
        (.onCompleteResponse handler response)
        (throw (ex-info "intentional" {:v v}))
      ))))


(deftest exceptions-test
  (with-redefs [SEM      (h/mk-semaphore 0)
                VAL-ATOM (atom 0)
                anode/log-node-error (fn [& args])
                aor-types/get-config (max-retries-override 3)]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (aor/declare-agent-object-builder
           topology
           "chat1"
           (fn [setup] (->MockChatModel)))
          (aor/declare-agent-object-builder
           topology
           "schat1"
           (fn [setup] (->MockStreamingChatModel)))
          (->
            topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node]
               (let [chat  (aor/get-agent-object agent-node "chat1")
                     schat (aor/get-agent-object agent-node "schat1")]
                 (aor/record-nested-op!
                  agent-node
                  :other
                  10
                  11
                  {"abc" 123})
                 (h/acquire-semaphore SEM)
                 (lc4j/basic-chat chat "prompt")
                 (lc4j/basic-chat schat "prompt")
                 (aor/result! agent-node "done")
               ))))
         ))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))

       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind root-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "foo")))

       (bind foo (aor/agent-client agent-manager "foo"))
       (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))
       (bind get-exceptions
         (fn [{:keys [task-id agent-invoke-id]}]
           (foreign-select-one [(keypath agent-invoke-id) :exception-summaries]
                               root-pstate
                               {:pkey task-id})
         ))

       (reset! VAL-ATOM 5)
       (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo))

       (h/release-semaphore SEM 1)
       (is (condition-attained? (= 1
                                   (-> inv
                                       get-exceptions
                                       count))))
       (is (= "clojure.lang.ExceptionInfo: intentional {:v 4}"
              (-> inv
                  get-exceptions
                  first
                  :throwable-str
                  h/first-line)))

       (h/release-semaphore SEM 1000)
       (is (condition-attained? (= 4
                                   (-> inv
                                       get-exceptions
                                       count))))
       (bind es (get-exceptions inv))
       (is (= ["clojure.lang.ExceptionInfo: intentional {:v 4}"
               "clojure.lang.ExceptionInfo: intentional {:v 3}"
               "clojure.lang.ExceptionInfo: intentional {:v 2}"
               "clojure.lang.ExceptionInfo: intentional {:v 1}"]
              (->> es
                   (mapv :throwable-str)
                   (mapv h/first-line))))
       (is (every? #(= "start" %) (mapv :node es)))

       (try
         (aor/agent-result foo inv)
         (is false)
         (catch Exception e
           (let [e (ex-cause e)]
             (is (instance? AgentFailedException e))
             (is (str/includes?
                  (ex-message e)
                  "clojure.lang.ExceptionInfo: intentional {:v 1}"))
           )))

       (try
         (aor/agent-next-step foo inv)
         (is false)
         (catch Exception e
           (let [e (ex-cause e)]
             (is (instance? AgentFailedException e))
             (is (str/includes?
                  (ex-message e)
                  "clojure.lang.ExceptionInfo: intentional {:v 1}"))
           )))

       (bind root-invoke-id
         (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                             root-pstate
                             {:pkey task-id}))

       (bind trace
         (:invokes-map
          (foreign-invoke-query traces-query
                                task-id
                                [[task-id root-invoke-id]]
                                10000)))

       (is (= 1 (count trace)))
       (bind excs (select [MAP-VALS :exceptions ALL] trace))
       (is (= ["clojure.lang.ExceptionInfo: intentional {:v 4}"
               "clojure.lang.ExceptionInfo: intentional {:v 3}"
               "clojure.lang.ExceptionInfo: intentional {:v 2}"
               "clojure.lang.ExceptionInfo: intentional {:v 1}"]
              (mapv h/first-line excs)))

       (reset! VAL-ATOM 3)
       (bind {:keys [task-id agent-invoke-id] :as inv} (aor/agent-initiate foo))
       (is (= "done" (aor/agent-result foo inv)))
       (bind root-invoke-id
         (foreign-select-one [(keypath agent-invoke-id) :root-invoke-id]
                             root-pstate
                             {:pkey task-id}))

       (bind trace
         (:invokes-map
          (foreign-invoke-query traces-query
                                task-id
                                [[task-id root-invoke-id]]
                                10000)))
       (is (= 1 (count trace)))

       (bind nested-ops
         (select [MAP-VALS :nested-ops ALL (transformed [:info (must "failure")] (constantly :fail))
                  (view #(into {} %))]
                 trace))

       ;; verifies:
       ;;  - nested-ops are additive on both failure and success
       ;;  - failed model calls are recorded
       (is (= nested-ops
              [{:start-time-millis 10
                :finish-time-millis 11
                :type :other
                :info {"abc" 123}}
               {:start-time-millis 0
                :finish-time-millis 0
                :type :model-call
                :info
                {"totalTokenCount"  30
                 "modelName"        "aor-model"
                 "inputTokenCount"  10
                 "finishReason"     "stop"
                 "objectName"       "chat1"
                 "input"
                 [{"type" "user" "contents" [{"type" "text" "text" "prompt"}]}]
                 "response"         "!!!"
                 "outputTokenCount" 20}}
               {:start-time-millis 0
                :finish-time-millis 0
                :type :model-call
                :info
                {"objectName" "schat1"
                 "input"
                 [{"type" "user" "contents" [{"type" "text" "text" "prompt"}]}]
                 "failure"    :fail}}
               {:start-time-millis 10
                :finish-time-millis 11
                :type :other
                :info {"abc" 123}}
               {:start-time-millis 0
                :finish-time-millis 0
                :type :model-call
                :info
                {"objectName" "chat1"
                 "input"
                 [{"type" "user" "contents" [{"type" "text" "text" "prompt"}]}]
                 "failure"    :fail}}
               {:start-time-millis 10
                :finish-time-millis 11
                :type :other
                :info {"abc" 123}}
               {:start-time-millis 0
                :finish-time-millis 0
                :type :model-call
                :info
                {"totalTokenCount"  30
                 "modelName"        "aor-model"
                 "inputTokenCount"  10
                 "finishReason"     "stop"
                 "objectName"       "chat1"
                 "input"
                 [{"type" "user" "contents" [{"type" "text" "text" "prompt"}]}]
                 "response"         "!!!"
                 "outputTokenCount" 20}}
               {:start-time-millis 0
                :finish-time-millis 0
                :type :model-call
                :info
                {"totalTokenCount"      30
                 "modelName"            "s-aor-model"
                 "inputTokenCount"      10
                 "finishReason"         "length"
                 "objectName"           "schat1"
                 "firstTokenTimeMillis" 0
                 "input"
                 [{"type" "user" "contents" [{"type" "text" "text" "prompt"}]}]
                 "response"             "!!!"
                 "outputTokenCount"     20}}]))
      ))))

(def SLOW-INVOKES-ATOM)

;; In :continue mode a retry re-runs an incomplete node under the same node
;; invoke id. If the invalidated attempt's run of that node finishes later, its
;; completion is filtered as stale, but it must not untrack the current
;; attempt's run: the stall checker would then see a running node as stalled
;; and issue a spurious retry.
(deftest stale-attempt-completion-test
  (let [checks-atom   (atom 0)
        stalls-atom   (atom 0)
        retries-atom  (atom 0)
        filtered-atom (atom 0)]
    (with-redefs
      [SEM (h/mk-semaphore 0)
       SEM2 (h/mk-semaphore 0)
       SLOW-INVOKES-ATOM (atom 0)

       i/SUBSTITUTE-TICK-DEPOTS true
       retries/checker-threshold-millis short-checker-threshold-millis
       aor-types/get-config (max-retries-override 5)
       anode/log-node-error (fn [& args])

       retries/hook:checker-finished (fn [] (swap! checks-atom inc))
       retries/hook:stall-detected (fn [& args] (swap! stalls-atom inc))
       apart/hook:filtered-event (fn [& args] (swap! filtered-atom inc))
       at/hook:received-retry (fn [& args] (swap! retries-atom inc))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (let [topology  (aor/agent-topology setup topologies)
                   node-exec (symbol (po/agent-node-executor-name))]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  "slow"
                  (fn [agent-node]
                    (aor/emit! agent-node "slow")))
                 (aor/node
                  "slow"
                  nil
                  (fn [agent-node]
                    ;; first run (attempt 0) waits on SEM, later runs on SEM2
                    (let [n (swap! SLOW-INVOKES-ATOM inc)]
                      (h/acquire-semaphore (if (= 1 n) SEM SEM2) 1)
                      (aor/result! agent-node n)))))
               (aor/define-agents! topology)
               (<<query-topology topologies
                 "executing"
                 [:> *res]
                 (|all)
                 (get-executing-node-ids node-exec :> *invoke-ids)
                 (ops/explode *invoke-ids :> *invoke-id)
                 (|origin)
                 (aggs/+set-agg *invoke-id :> *res)))))
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind check-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-check-tick-depot-name "foo")))
         (bind failures-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-failures-depot-name "foo")))
         (bind executing-q (foreign-query ipc module-name "executing"))
         (bind executing #(or (foreign-invoke-query executing-q) #{}))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))

         (bind checker-progress!
           (fn []
             (let [s @checks-atom]
               (foreign-append! check-depot nil)
               (when-not (condition-attained? (= (+ 1 s) @checks-atom))
                 (throw (ex-info "Didn't make progress"
                                 {:checks @checks-atom :s s}))))))

         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 1 @SLOW-INVOKES-ATOM)))
         (bind slow-ids (executing))
         (is (= 1 (count slow-ids)))

         ;; invalidate attempt 0 while "slow" is still running in it
         (foreign-append! failures-depot
                          (aor-types/->AgentFailure (.getTaskId inv)
                                                    (.getAgentInvokeId inv)
                                                    0))
         (is (condition-attained? (= 2 @SLOW-INVOKES-ATOM)))
         (is (= 1 @retries-atom))
         ;; attempt 1 re-runs "slow" under the same invoke id
         (is (= slow-ids (executing)))

         ;; attempt 0's run of "slow" completes late and is filtered
         (bind filtered-before @filtered-atom)
         (h/release-semaphore SEM 1)
         (is (condition-attained? (> @filtered-atom filtered-before)))

         ;; attempt 1's run is still executing, so it is still tracked...
         (is (= slow-ids (executing)))
         ;; ...and the stall checker doesn't see a stall
         (TopologyUtils/advanceSimTime 1000)
         (checker-progress!)
         (is (= 0 @stalls-atom))
         (is (= 1 @retries-atom))

         ;; two permits so a spurious attempt 2 can't hang the test
         (h/release-semaphore SEM2 2)
         (is (= 2 (aor/agent-result foo inv)))
        )))))

(def ASK-INVOKES-ATOM)
(def STALE-THREAD-ATOM)
(def FILTERED-ATOM)

;; Same reuse of the node invoke id: if the invalidated attempt's run asks for
;; human input after the current attempt's run did, it must not hide the
;; current attempt's request, so the response to it still reaches the current
;; attempt.
(deftest stale-attempt-human-input-test
  (with-redefs
    [SEM (h/mk-semaphore 0)
     ASK-INVOKES-ATOM (atom 0)
     STALE-THREAD-ATOM (atom nil)
     FILTERED-ATOM (atom 0)

     i/SUBSTITUTE-TICK-DEPOTS true
     aor-types/get-config (max-retries-override 5)
     anode/log-node-error (fn [& args])
     apart/hook:filtered-event (fn [& args] (swap! FILTERED-ATOM inc))]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (module
           [setup topologies]
           (let [topology (aor/agent-topology setup topologies)]
             (->
               topology
               (aor/new-agent "foo")
               (aor/node
                "start"
                "ask"
                (fn [agent-node]
                  (aor/emit! agent-node "ask")))
               (aor/node
                "ask"
                nil
                (fn [agent-node]
                  (if (= 1 (swap! ASK-INVOKES-ATOM inc))
                    ;; attempt 0: asks only once released, after attempt 1 asked
                    (do
                      (h/acquire-semaphore SEM 1)
                      (reset! STALE-THREAD-ATOM (Thread/currentThread))
                      (aor/get-human-input agent-node "stale"))
                    (aor/result! agent-node
                                 (aor/get-human-input agent-node "current"))))))
             (aor/define-agents! topology))))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind failures-depot
         (foreign-depot ipc
                        module-name
                        (po/agent-failures-depot-name "foo")))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind inv (aor/agent-initiate foo))
       (bind pending #(aor/pending-human-inputs foo inv))
       (bind prompts #(set (map :prompt (pending))))
       (is (condition-attained? (= 1 @ASK-INVOKES-ATOM)))

       ;; invalidate attempt 0; attempt 1 re-runs "ask" under the same invoke id
       (foreign-append! failures-depot
                        (aor-types/->AgentFailure (.getTaskId inv)
                                                  (.getAgentInvokeId inv)
                                                  0))
       (is (condition-attained? (= #{"current"} (prompts))))

       ;; now attempt 0's run asks too; its request is dropped as stale
       (bind filtered-before @FILTERED-ATOM)
       (h/release-semaphore SEM 1)
       (is (condition-attained? (> @FILTERED-ATOM filtered-before)))
       (is (= #{"current"} (prompts)))

       (aor/provide-human-input foo
                                (first (filter #(= "current" (:prompt %))
                                               (pending)))
                                "answer")
       (is (condition-attained? (aor/agent-invoke-complete? foo inv)))
       (when (aor/agent-invoke-complete? foo inv)
         (is (= "answer" (aor/agent-result foo inv))))

       ;; attempt 0's thread waits for input that can't arrive
       (some-> ^Thread @STALE-THREAD-ATOM .interrupt)
      ))))

;; A :continue retry is progress. Until the first replayed node's emits refresh
;; it, the run's last progress time would otherwise still be the old attempt's,
;; so a checker tick in that window walks the run and can count the node that
;; failed (started, unfinished, not executing) as a stall: a spurious retry.
(deftest retry-refreshes-progress-test
  (with-redefs
    [SEM (h/mk-semaphore 0)

     i/SUBSTITUTE-TICK-DEPOTS true
     aor-types/get-config (max-retries-override 5)
     anode/log-node-error (fn [& args])
     ;; leave only init and retry to write progress
     at/hook:update-last-progress> no-progress-update>]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (module
           [setup topologies]
           (let [topology (aor/agent-topology setup topologies)]
             (->
               topology
               (aor/new-agent "foo")
               (aor/node
                "start"
                "slow"
                (fn [agent-node]
                  (aor/emit! agent-node "slow")))
               (aor/node
                "slow"
                nil
                (fn [agent-node]
                  (h/acquire-semaphore SEM 1)
                  (aor/result! agent-node :done))))
             (aor/define-agents! topology))))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind failures-depot
         (foreign-depot ipc
                        module-name
                        (po/agent-failures-depot-name "foo")))
       (bind root-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "foo")))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind inv (aor/agent-initiate foo))
       (bind root
         #(foreign-select-one [(keypath (.getAgentInvokeId inv))
                               (submap [:retry-num :last-progress-time-millis])]
                              root-pstate
                              {:pkey (.getTaskId inv)}))
       (is (= 0 (:last-progress-time-millis (root))))

       (TopologyUtils/advanceSimTime 1000)
       (foreign-append! failures-depot
                        (aor-types/->AgentFailure (.getTaskId inv)
                                                  (.getAgentInvokeId inv)
                                                  0))
       (is (condition-attained? (= 1 (:retry-num (root)))))
       (is (= 1000 (:last-progress-time-millis (root))))

       (h/release-semaphore SEM 2)
       (is (= :done (aor/agent-result foo inv)))
      ))))

(deframaop hopping-graph-version
  "Hops to another task and back, as fetch-graph-version does (to the global
  task) on a task's first use after a module update."
  [*agent-name]
  (ops/current-task-id :> *task-id)
  (|direct (mod (inc *task-id) 4))
  (|direct *task-id)
  (:> 0))

(deframaop append-duplicate-retries>
  [*agent-task-id *agent-id]
  (<<with-substitutions
   [*agent-depot (po/agent-depot-task-global "foo")]
   (|direct *agent-task-id)
   (aor-types/->RetryAgentInvoke *agent-task-id *agent-id 0 :> *retry)
   (depot-partition-append! *agent-depot *retry :append-ack)
   (depot-partition-append! *agent-depot *retry :append-ack)
   (:>)))

;; intake-retry's checks (run unfinished, expected attempt) must still hold
;; where it writes. fetch-graph-version can hop away from the run's task in
;; between, and a duplicate retry of the same attempt (as the comment in
;; intake-retry allows) can pass the same checks before the first one is back.
;; Both would then dispatch the next attempt, running its nodes twice.
(deftest retry-checks-hold-across-graph-version-hop-test
  (let [retries-atom (atom 0)]
    (with-redefs
      [SEM (h/mk-semaphore 0)
       SEM2 (h/mk-semaphore 0)
       SLOW-INVOKES-ATOM (atom 0)

       i/SUBSTITUTE-TICK-DEPOTS true
       aor-types/get-config (max-retries-override 5)
       anode/log-node-error (fn [& args])
       at/fetch-graph-version hopping-graph-version
       at/hook:received-retry (fn [& args] (swap! retries-atom inc))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (declare-depot setup *duplicate-retries-depot :random)
             (let [topology (aor/agent-topology setup topologies)
                   s        (aor/underlying-stream-topology topology)]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  "slow"
                  (fn [agent-node]
                    (aor/emit! agent-node "slow")))
                 (aor/node
                  "slow"
                  nil
                  (fn [agent-node]
                    (let [n (swap! SLOW-INVOKES-ATOM inc)]
                      (h/acquire-semaphore (if (= 1 n) SEM SEM2) 1)
                      (aor/result! agent-node n)))))
               (aor/define-agents! topology)
               (<<sources s
                (source> *duplicate-retries-depot :> [*agent-task-id *agent-id])
                 (append-duplicate-retries> *agent-task-id *agent-id)))))
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind duplicate-retries-depot
           (foreign-depot ipc module-name "*duplicate-retries-depot"))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))

         (bind inv (aor/agent-initiate foo))
         (is (condition-attained? (= 1 @SLOW-INVOKES-ATOM)))

         ;; two retries of attempt 0 land on the run's task back to back
         (foreign-append! duplicate-retries-depot
                          [(.getTaskId inv) (.getAgentInvokeId inv)])
         (is (condition-attained? (= 2 @retries-atom)))
         (is (condition-attained? (>= @SLOW-INVOKES-ATOM 2)))
         ;; nothing signals a dropped retry, so allow a second dispatch time to
         ;; show: it takes a few depot round trips
         (Thread/sleep 2000)
         (is (= 2 @SLOW-INVOKES-ATOM))

         (h/release-semaphore SEM2 3)
         (h/release-semaphore SEM 1)
         (is (some? (aor/agent-result foo inv)))
        )))))

;; A run whose nodes all finish without a result is completed with "Agent
;; completed without result". It must leave :active-invokes like any finished
;; run. The stall checker walks every active run on each tick, and once GC has
;; deleted such a run, it read a missing root: the microbatch threw, which
;; kills the worker, again after every restart. Entries left by runs that
;; finished before the fix must not do that either.
(deftest completed-without-result-leaves-active-invokes-test
  (let [checks-atom (atom 0)]
    (with-redefs
      [i/SUBSTITUTE-TICK-DEPOTS true
       apart/next-agent-task (fn [& args] 0)
       retries/checker-threshold-millis short-checker-threshold-millis
       retries/hook:checker-finished (fn [] (swap! checks-atom inc))]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (declare-depot setup *legacy-active-depot :random)
             (let [topology (aor/agent-topology setup topologies)
                   s        (aor/underlying-stream-topology topology)
                   stream-shared-sym
                   (symbol (po/agent-stream-shared-task-global-name "foo"))]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  nil
                  (fn [agent-node result?]
                    (when result?
                      (aor/result! agent-node :ok)))))
               (aor/define-agents! topology)
               ;; adds an :active-invokes entry as runs finished before the
               ;; fix left them
               (<<sources s
                (source> *legacy-active-depot :> *agent-id)
                 (|direct 0)
                 (local-transform> [:active-invokes NONE-ELEM (termval *agent-id)]
                                   stream-shared-sym)))))
         (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind check-depot
           (foreign-depot ipc
                          module-name
                          (po/agent-check-tick-depot-name "foo")))
         (bind config-depot
           (foreign-depot ipc module-name (po/agent-config-depot-name "foo")))
         (bind gc-depot
           (foreign-depot ipc module-name (po/agent-gc-tick-depot-name "foo")))
         (bind legacy-active-depot
           (foreign-depot ipc module-name "*legacy-active-depot"))
         (bind root-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-root-task-global-name "foo")))
         (bind stream-shared-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-stream-shared-task-global-name "foo")))
         (bind active-invokes
           #(set (foreign-select [:active-invokes ALL]
                                 stream-shared-pstate
                                 {:pkey 0})))
         (bind root-exists?
           (fn [^AgentInvoke inv]
             (some? (foreign-select-one [(keypath (.getAgentInvokeId inv))
                                         :root-invoke-id]
                                        root-pstate
                                        {:pkey 0}))))
         (bind checker-progress!
           (fn []
             (let [s @checks-atom]
               (TopologyUtils/advanceSimTime 1000)
               (foreign-append! check-depot nil)
               (condition-attained? (= (+ 1 s) @checks-atom)))))

         (bind inv (aor/agent-initiate foo false))
         (is (condition-attained? (aor/agent-invoke-complete? foo inv)))
         (is (thrown-with-msg? Exception
                               #"Agent completed without result"
                               (aor/agent-result foo inv)))
         (is (= #{} (active-invokes)))

         ;; an entry from before the fix: the checker skips the finished run...
         (foreign-append! legacy-active-depot (.getAgentInvokeId inv))
         (is (= #{(.getAgentInvokeId inv)} (active-invokes)))
         (is (checker-progress!))

         ;; ...and GC removes the entry with the run
         (foreign-append! config-depot (aor-types/change-max-traces-per-task 1))
         (bind inv2 (aor/agent-initiate foo true))
         (is (= :ok (aor/agent-result foo inv2)))
         (dotimes [_ 5]
           (foreign-append! gc-depot nil))
         (is (condition-attained? (not (root-exists? inv))))
         (is (condition-attained? (= #{} (active-invokes))))
         (is (checker-progress!))

         ;; an entry whose run is already gone: the checker skips it
         (foreign-append! legacy-active-depot (h/random-uuid7))
         (is (condition-attained? (= 1 (count (active-invokes)))))
         (is (checker-progress!))
        )))))

(def ASKING-THREADS-ATOM)

;; A retry invalidates every pending human-input request of the run: answering
;; one would reach no running node. Only the new attempt's requests may stay.
(deftest retry-clears-pending-human-requests-test
  (with-redefs
    [ASK-INVOKES-ATOM (atom 0)
     ASKING-THREADS-ATOM (atom [])

     i/SUBSTITUTE-TICK-DEPOTS true
     aor-types/get-config (max-retries-override 5)
     anode/log-node-error (fn [& args])]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (module
           [setup topologies]
           (let [topology (aor/agent-topology setup topologies)]
             (->
               topology
               (aor/new-agent "foo")
               (aor/node
                "start"
                "ask"
                (fn [agent-node]
                  (aor/emit! agent-node "ask")))
               (aor/node
                "ask"
                nil
                (fn [agent-node]
                  (swap! ASKING-THREADS-ATOM conj (Thread/currentThread))
                  (let [prompt (if (= 1 (swap! ASK-INVOKES-ATOM inc))
                                 "first"
                                 "second")]
                    (aor/result! agent-node
                                 (aor/get-human-input agent-node prompt))))))
             (aor/define-agents! topology))))
       (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind failures-depot
         (foreign-depot ipc
                        module-name
                        (po/agent-failures-depot-name "foo")))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind inv (aor/agent-initiate foo))
       (bind pending #(aor/pending-human-inputs foo inv))
       (bind prompts #(set (map :prompt (pending))))
       (is (condition-attained? (= #{"first"} (prompts))))

       ;; retry while attempt 0 waits for input
       (foreign-append! failures-depot
                        (aor-types/->AgentFailure (.getTaskId inv)
                                                  (.getAgentInvokeId inv)
                                                  0))
       (is (condition-attained? (contains? (prompts) "second")))
       (is (= #{"second"} (prompts)))

       (aor/provide-human-input foo
                                (first (filter #(= "second" (:prompt %)) (pending)))
                                "answer")
       (is (= "answer" (aor/agent-result foo inv)))

       ;; attempt 0's thread waits for input that can't arrive
       (doseq [^Thread t @ASKING-THREADS-ATOM]
         (.interrupt t))
      ))))
