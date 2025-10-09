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
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))
     (bind affected-aggs-query
       (foreign-query ipc
                      module-name
                      (queries/agent-get-fork-affected-aggs-query-name "foo")))

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
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "b2")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "b3")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                agent-task-id
                                agent-id
                                #{(inv-id "b4")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                agent-task-id
                                agent-id
                                #{(inv-id "start")})))
     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                agent-task-id
                                agent-id
                                #{(inv-id "b4") (inv-id "node1")
                                  (inv-id "node2")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  agent-task-id
                                  agent-id
                                  #{start2})))

     (is (= #{start2 start3}
            (foreign-invoke-query affected-aggs-query
                                  agent-task-id
                                  agent-id
                                  #{start3})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                agent-task-id
                                agent-id
                                #{(inv-id "agg")})))

     (is (empty?
          (foreign-invoke-query affected-aggs-query
                                agent-task-id
                                agent-id
                                #{(inv-id "agg3")})))

     (is (= #{start2}
            (foreign-invoke-query affected-aggs-query
                                  agent-task-id
                                  agent-id
                                  #{(inv-id "agg2")})))

     (is (= #{start1 start2}
            (foreign-invoke-query affected-aggs-query
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
          (rtest/launch-module! ipc module {:tasks 4 :threads 2})
          (bind module-name (get-module-name module))

          (bind agent-manager (aor/agent-manager ipc module-name))
          (bind foo (aor/agent-client agent-manager "foo"))
          (bind active-pstate
            (foreign-pstate ipc
                            module-name
                            (po/agent-active-invokes-task-global-name "foo")))
          (bind ks (rtest/gen-hashing-index-keys 4))

          (bind check-active!
            (fn [expected]
              (let [c (reduce
                       (fn [c k]
                         (+ c
                            (foreign-select-one (view count)
                                                active-pstate
                                                {:pkey k})))
                       0
                       ks)]
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
              (when-not (condition-attained?
                         (= @tc/AGG-RESULTS-ATOM
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
          (rtest/launch-module! ipc module {:tasks 4 :threads 2})
          (bind module-name (get-module-name module))

          (bind agent-manager (aor/agent-manager ipc module-name))
          (bind foo (aor/agent-client agent-manager "foo"))
          (bind bar (aor/agent-client agent-manager "bar"))
          (bind car (aor/agent-client agent-manager "car"))

          (bind actives
            (vec
             (for [n ["foo" "bar" "car"]]
               (foreign-pstate ipc
                               module-name
                               (po/agent-active-invokes-task-global-name n)))))
          (bind ks (rtest/gen-hashing-index-keys 4))

          (bind check-active!
            (fn [expected]
              (let [c (reduce
                       (fn [c k]
                         (reduce
                          (fn [c ap]
                            (+ c
                               (foreign-select-one (view count)
                                                   ap
                                                   {:pkey k})))
                          c
                          actives))
                       0
                       ks)]
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
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))

       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind root-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "foo")))
       (bind traces-query
         (foreign-query ipc
                        module-name
                        (queries/tracing-query-name "foo")))

       (bind foo (aor/agent-client agent-manager "foo"))
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
