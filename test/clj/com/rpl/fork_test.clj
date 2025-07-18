(ns com.rpl.fork-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
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
(def GLOBAL-ATOM2)
(def GLOBAL-ATOM3)
(def START3-EXTRA-EMIT)
(def AGG2-EXTRA-EMIT)

(defn of-input
  [trace v]
  (select-one!
   [ALL (selected? LAST :input FIRST (pred= v)) FIRST]
   trace))

(defn of-name
  [trace n]
  (select-one!
   [ALL (selected? LAST :node (pred= n)) FIRST]
   trace))

(defn trace-node
  [trace n]
  (select-one!
   [ALL (selected? LAST :node (pred= n)) LAST]
   trace))

(defn trace-nodes
  [trace n]
  (select
   [ALL (selected? LAST :node (pred= n)) LAST]
   trace))

(defn trace-node-ids
  [trace n]
  (select
   [ALL (selected? LAST :node (pred= n)) FIRST]
   trace))

(defn normalize-node
  [node]
  (let [m (select-keys node
                       [:node :nested-ops :emits :result :input :started-agg?
                        :invoked-agg-invoke-id :agg-input-count
                        :agg-inputs-first-10 :agg-start-res :agg-state
                        :agg-finished?])]
    (->> m
         (setval [(must :emits) ALL :invoke-id] 0)
         (setval [(must :emits) ALL :fork-invoke-id] nil)
         (setval [(must :emits) ALL :target-task-id] 0)
         (setval (must :invoked-agg-invoke-id) 0)
         (setval (must :invoked-agg-invoke-id) 0)
         (setval [(must :agg-inputs-first-10) ALL :invoke-id] 0))))

(defn verify-same-nodes!
  [trace1 trace2 nodes]
  (doseq [n nodes]
    (let [orig   (trace-nodes trace1 n)
          forked (trace-nodes trace2 n)]
      (when-not (= (->> orig
                        (mapv normalize-node)
                        frequencies)
                   (->> forked
                        (mapv normalize-node)
                        frequencies))
        (throw (ex-info "Mismatch on same nodes"
                        {:node n :orig orig :forked forked})))
    )))

(deftest forking-test
  (tc/with-auto-builder
   (with-redefs [GLOBAL-ATOM       (atom 0)
                 GLOBAL-ATOM2      (atom 0)
                 GLOBAL-ATOM3      (atom 9)
                 START3-EXTRA-EMIT (atom 0)
                 AGG2-EXTRA-EMIT   (atom false)]
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
             (aor/node
              "a1"
              "agg"
              (fn [agent-node v]
                (aor/emit! agent-node "agg" (- @GLOBAL-ATOM3))))
             (aor/node
              "a2"
              "agg"
              (fn [agent-node v]
                (aor/emit! agent-node "agg" @GLOBAL-ATOM3)))
             (aor/agg-node
              "agg"
              "after"
              aggs/+vec-agg
              (fn [agent-node agg node-start-res]
                (aor/emit! agent-node "after" [agg node-start-res])))
             (aor/node
              "after"
              "node3"
              (fn [agent-node v] (aor/emit! agent-node "node3" 1)))
             (tc/auto-node "node3" nil)

             (tc/auto-node "node2" "special1")
             (aor/node
              "special1"
              "special2"
              (fn [agent-node v]
                (aor/emit! agent-node "special2" :begin)))
             (aor/node
              "special2"
              "start2"
              (fn [agent-node v]
                (aor/emit! agent-node "start2" 1)))
             (tc/auto-node "start2" "b1")
             (tc/auto-node "b1" "start3")
             (aor/agg-start-node
              "start3"
              "b2"
              (fn [agent-node v]
                (aor/emit! agent-node "b2" 1)
                (when (> @START3-EXTRA-EMIT 0)
                  (dotimes [_ @START3-EXTRA-EMIT]
                    (aor/emit! agent-node "b2" 1))
                  (swap! START3-EXTRA-EMIT dec))
              ))
             (tc/auto-node "b2" "agg2")
             (aor/agg-node
              "agg2"
              "b3"
              aggs/+vec-agg
              (fn [agent-node agg-state agg-start-res]
                (tc/record-agg! "agg2" agg-state)
                (aor/emit! agent-node "b3" 1)
                (when @AGG2-EXTRA-EMIT
                  (aor/emit! agent-node "b3" 1))
              ))
             (tc/auto-node "b3" "agg3")
             (tc/auto-node "agg3" "b4")
             (tc/auto-node "b4" "special3")
             (aor/node
              "special3"
              "special4"
              (fn [agent-node v]
                (swap! GLOBAL-ATOM2 inc)
                (aor/emit! agent-node
                           "special4"
                           ["aaa" @GLOBAL-ATOM @GLOBAL-ATOM2])
                (swap! GLOBAL-ATOM dec)))
             (aor/node
              "special4"
              ["special2" "b5"]
              (fn [agent-node [_ v _]]
                (if (> v 0)
                  (aor/emit! agent-node "special2" v)
                  (aor/emit! agent-node "b5" 1))))
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

        (bind get-trace
          (fn [^AgentInvoke inv]
            (let [agent-task-id  (.getTaskId inv)
                  agent-id       (.getAgentInvokeId inv)
                  root-invoke-id
                  (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                      root-pstate
                                      {:pkey agent-task-id})]
              (wait-agent-finished! root-pstate agent-task-id agent-id)
              (:invokes-map
               (foreign-invoke-query traces-query
                                     agent-task-id
                                     [[agent-task-id root-invoke-id]]
                                     10000))
            )))

        (reset! GLOBAL-ATOM 2)
        (reset! tc/RESULT-NODE-ATOM "b5")

        (bind inv (aor/agent-initiate foo 1))
        (bind trace (get-trace inv))
        (is (= "b5" (aor/agent-result foo inv)))

        (bind base-agent-task-id (.getTaskId inv))
        (bind base-agent-id (.getAgentInvokeId inv))
        (is (= []
               (foreign-select [(keypath base-agent-id) :forks ALL]
                               root-pstate
                               {:pkey base-agent-task-id})))
        (is (= nil
               (foreign-select-one [(keypath base-agent-id) :fork-of]
                                   root-pstate
                                   {:pkey base-agent-task-id})))

        (reset! GLOBAL-ATOM3 7)
        (bind a2 (of-name trace "a2"))
        (bind finv (aor/agent-initiate-fork foo inv {a2 [1]}))
        (bind trace2 (get-trace finv))
        (is (= "b5" (aor/agent-result foo finv)))
        (bind fagent-task-id (.getTaskId finv))
        (bind fagent-id1 (.getAgentInvokeId finv))
        (is (= fagent-task-id base-agent-task-id))
        (is (= [fagent-id1]
               (foreign-select [(keypath base-agent-id) :forks ALL]
                               root-pstate
                               {:pkey base-agent-task-id})))
        (is (= base-agent-id
               (foreign-select-one [(keypath fagent-id1) :fork-of
                                    :parent-agent-id]
                                   root-pstate
                                   {:pkey base-agent-task-id})))

        (is (empty? (set/intersection (-> trace
                                          keys
                                          set)
                                      (-> trace2
                                          keys
                                          set))))

        (bind a2-node-emits (:emits (trace-node trace2 "a2")))
        (is (= 1 (count a2-node-emits)))
        (is (= [7]
               (-> a2-node-emits
                   first
                   :args)))

        (bind a (trace-node trace2 "agg"))
        (is (or (= [7 -9] (:agg-state a))
                (= [-9 7] (:agg-state a))))
        (is (or (= [[7 -9] nil] (:input a))
                (= [[-9 7] nil] (:input a))))

        (bind after (trace-node trace2 "after"))
        (is (or (= [[[7 -9] nil]] (:input after))
                (= [[[-9 7] nil]] (:input after))))

        (verify-same-nodes!
         trace
         trace2
         ["begin" "node1" "start1" "a1" "node2" "special1" "special2" "start2"
          "b1" "start3" "b2" "agg2" "b3" "agg3" "b4" "special3" "special4"
          "b5"])

        (bind special4-1 (of-input trace ["aaa" 1 2]))
        (bind agg-node (of-name trace "agg"))

        (bind finv
          (aor/agent-initiate-fork foo
                                   inv
                                   {special4-1 [["aaa" 0 10]]
                                    agg-node   [[1 2 3 4] :a]}))
        (bind trace2 (get-trace finv))
        (is (= "b5" (aor/agent-result foo finv)))
        (bind fagent-task-id (.getTaskId finv))
        (bind fagent-id2 (.getAgentInvokeId finv))
        (is (= fagent-task-id base-agent-task-id))
        (is (= [fagent-id1 fagent-id2]
               (foreign-select [(keypath base-agent-id) :forks ALL]
                               root-pstate
                               {:pkey base-agent-task-id})))
        (is (= base-agent-id
               (foreign-select-one [(keypath fagent-id2) :fork-of
                                    :parent-agent-id]
                                   root-pstate
                                   {:pkey base-agent-task-id})))


        ;; since reduced number of iterations of the loop
        (is (< (count trace2) (count trace)))
        (verify-same-nodes!
         trace
         trace2
         ["begin" "node1" "start1" "a1" "a2" "node3" "node2" "special1" "b5"])
        (doseq [n ["start2" "b1" "start3" "b2" "agg2" "b3" "agg3" "b4"]]
          (let [nodes (mapv normalize-node (trace-nodes trace2 n))
                orig  (-> (trace-nodes trace n)
                          first
                          normalize-node)]
            (when-not (every? #(= orig %) nodes)
              (throw (ex-info "Not equal to orig"
                              {:node n :orig orig :nodes nodes})))
            (when (not= 2 (count nodes))
              (throw (ex-info "Mismatched count"
                              {:node n :count (count nodes)})))
          ))

        (bind an (trace-node trace2 "agg"))
        (is (= (normalize-node an)
               {:node          "agg"
                :nested-ops    []
                :emits
                [(aor-types/->AgentNodeEmit 0 nil 0 "after" [[[1 2 3 4] :a]])]
                :result        nil
                :input         [[1 2 3 4] :a]
                :agg-start-res :a
                :agg-state     [1 2 3 4]
                :agg-finished? true}
            ))

        (bind an (trace-node trace2 "after"))
        (is (= (normalize-node an)
               {:node       "after"
                :nested-ops []
                :emits
                [(aor-types/->AgentNodeEmit 0 nil 0 "node3" [1])]
                :result     nil
                :input      [[[1 2 3 4] :a]]}
            ))

        (bind nodes (trace-nodes trace2 "special2"))
        (is (= 2 (count nodes)))
        (is (= #{[:begin] [2]}
               (->> nodes
                    (mapv :input)
                    set)))

        (bind nodes (trace-nodes trace2 "special3"))
        (is (= 2 (count nodes)))
        (is (= (->> nodes
                    (mapv normalize-node)
                    frequencies)
               {{:node       "special3"
                 :nested-ops []
                 :emits
                 [(aor-types/->AgentNodeEmit 0 nil 0 "special4" [["aaa" 2 1]])]
                 :result     nil
                 :input      [[1]]}
                1

                {:node       "special3"
                 :nested-ops []
                 :emits
                 [(aor-types/->AgentNodeEmit 0 nil 0 "special4" [["aaa" 1 2]])]
                 :result     nil
                 :input      [[1]]}
                1}))

        (bind nodes (trace-nodes trace2 "special4"))
        (is (= 2 (count nodes)))
        (is (= (->> nodes
                    (mapv normalize-node)
                    frequencies)
               {{:node "special4"
                 :nested-ops []
                 :emits [(aor-types/->AgentNodeEmit 0 nil 0 "special2" [2])]
                 :result nil
                 :input [["aaa" 2 1]]}
                1

                {:node       "special4"
                 :nested-ops []
                 :emits      [(aor-types/->AgentNodeEmit 0 nil 0 "b5" [1])]
                 :result     nil
                 :input      [["aaa" 0 10]]}
                1
               }))


        (reset! GLOBAL-ATOM 4)
        (reset! START3-EXTRA-EMIT 3)
        (reset! tc/AGG-RESULTS-ATOM {})
        (bind start3 (rand-nth (trace-node-ids trace "start3")))
        (bind finv
          (aor/agent-initiate-fork foo
                                   inv
                                   {start3 [1]}))
        (bind trace2 (get-trace finv))
        (is (= "b5" (aor/agent-result foo finv)))
        (bind fagent-task-id (.getTaskId finv))
        (bind fagent-id3 (.getAgentInvokeId finv))
        (is (= fagent-task-id base-agent-task-id))
        (is (= [fagent-id1 fagent-id2 fagent-id3]
               (foreign-select [(keypath base-agent-id) :forks ALL]
                               root-pstate
                               {:pkey base-agent-task-id})))
        (is (= base-agent-id
               (foreign-select-one [(keypath fagent-id3) :fork-of
                                    :parent-agent-id]
                                   root-pstate
                                   {:pkey base-agent-task-id})))

        (is (= @tc/AGG-RESULTS-ATOM
               {"agg2" [[1 1 1 1] [1 1 1] [1 1] [1] [1]]
                "agg3" [[1] [1] [1] [1] [1]]}))
        (is (> (count trace2) (count trace)))

        (bind nodes (mapv normalize-node (trace-nodes trace2 "start3")))
        (bind total (count nodes))
        (bind one-emit (aor-types/->AgentNodeEmit 0 nil 0 "b2" [1]))
        (bind base-start3
          {:node         "start3"
           :nested-ops   []
           :result       nil
           :input        [1]
           :started-agg? true})
        (bind mk-start3
          (fn [amt]
            (assoc base-start3 :emits (vec (repeat amt one-emit)))))
        (is (= {(mk-start3 4) 1
                (mk-start3 3) 1
                (mk-start3 2) 1
                (mk-start3 1) (- total 3)}
               (frequencies nodes)))

        (bind nodes (mapv normalize-node (trace-nodes trace2 "agg2")))

        (bind base-agg2
          {:emits         [(aor-types/->AgentNodeEmit 0 nil 0 "b3" [1])]
           :node          "agg2"
           :result        nil
           :agg-finished? true
           :nested-ops    []})
        (bind mk-agg2
          (fn [amt]
            (let [s   (vec (repeat amt 1))
                  asr (if (>= amt 2) (- amt 2))]
              (assoc base-agg2
               :agg-start-res asr
               :agg-input-count amt
               :agg-inputs-first-10 (vec (repeat amt
                                                 (aor-types/->AggInput 0 [1])))
               :agg-state s
               :input [s asr]
              ))))

        (is (= {(mk-agg2 4) 1
                (mk-agg2 3) 1
                (mk-agg2 2) 1
                (mk-agg2 1) (- total 3)}
               (frequencies nodes)))


        (reset! AGG2-EXTRA-EMIT true)
        (reset! tc/AGG-RESULTS-ATOM {})
        (bind agg2 (rand-nth (trace-node-ids trace "agg2")))
        (bind finv
          (aor/agent-initiate-fork foo
                                   inv
                                   {agg2 [[:x] 123]}))
        (bind trace2 (get-trace finv))
        (bind fagent-task-id (.getTaskId finv))
        (bind fagent-id4 (.getAgentInvokeId finv))
        (is (= fagent-task-id base-agent-task-id))
        (is (= [fagent-id1 fagent-id2 fagent-id3 fagent-id4]
               (foreign-select [(keypath base-agent-id) :forks ALL]
                               root-pstate
                               {:pkey base-agent-task-id})))
        (is (= base-agent-id
               (foreign-select-one [(keypath fagent-id4) :fork-of
                                    :parent-agent-id]
                                   root-pstate
                                   {:pkey base-agent-task-id})))

        (is (= "b5" (aor/agent-result foo finv)))
        (is (= @tc/AGG-RESULTS-ATOM {"agg2" [[:x]] "agg3" [[1 1]]}))

        (bind changed
          {:node          "agg2"
           :nested-ops    []
           :emits         (vec (repeat
                                2
                                (aor-types/->AgentNodeEmit 0 nil 0 "b3" [1])))
           :result        nil
           :input         [[:x] 123]
           :agg-start-res 123
           :agg-state     [:x]
           :agg-finished? true})
        (bind unchanged
          {:agg-input-count 1
           :agg-start-res   nil
           :emits           [(aor-types/->AgentNodeEmit 0 nil 0 "b3" [1])]
           :node            "agg2"
           :agg-inputs-first-10 [(aor-types/->AggInput 0 [1])]
           :result          nil
           :agg-finished?   true
           :nested-ops      []
           :agg-state       [1]
           :input           [[1] nil]})

        (bind nodes (mapv normalize-node (trace-nodes trace2 "agg2")))
        (is (<= 1 (count nodes) 3))
        (bind expected
          (if (> (count nodes) 1)
            {changed   1
             unchanged (-> nodes
                           count
                           dec)}
            {changed 1}
          ))
        (is (= expected (frequencies nodes)))

        (bind mk-agg3
          (fn [amt]
            {:agg-input-count amt
             :agg-start-res   nil
             :emits
             [(aor-types/->AgentNodeEmit 0 nil 0 "b4" [[1 1]])]
             :node            "agg3"
             :agg-inputs-first-10 (vec (repeat amt
                                               (aor-types/->AggInput 0 [1])))
             :result          nil
             :agg-finished?   true
             :nested-ops      []
             :agg-state       (vec (repeat amt 1))
             :input           [(vec (repeat amt 1)) nil]}
          ))

        (bind nodes (mapv normalize-node (trace-nodes trace2 "agg3")))
        (bind expected
          (if (> (count nodes) 1)
            {(mk-agg3 2) 1
             (mk-agg3 1) (-> nodes
                             count
                             dec)}
            {(mk-agg3 2) 1}
          ))
        (is (= expected (frequencies nodes)))
       )))))



(deftest retry-fork-test
  (tc/with-auto-builder
   (with-open [ipc (rtest/create-ipc)]
     (letlocals
      (bind module
        (aor/agentmodule
         [topology]
         (->
           topology
           (aor/new-agent "foo")
           (tc/auto-node "begin" "node1")
           (tc/auto-node "node1" "start1")
           (tc/auto-node "start1" "a")
           (tc/auto-node "a" "start2")
           (aor/agg-start-node
            "start2"
            "b"
            (fn [agent-node v]
              (aor/emit! agent-node "b" v)
              (aor/emit! agent-node "b" v)))
           (aor/auto-node "b" "agg2")
           (aor/auto-node "agg2" "agg1")
           (aor/auto-node "agg1" "end")
           (aor/node
            "end"
            nil
            (fn [agent-node v]
              (aor/result! agent-node v)))
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

      (bind get-trace
        (fn [^AgentInvoke inv]
          (let [agent-task-id  (.getTaskId inv)
                agent-id       (.getAgentInvokeId inv)
                root-invoke-id
                (foreign-select-one [(keypath agent-id) :root-invoke-id]
                                    root-pstate
                                    {:pkey agent-task-id})]
            (wait-agent-finished! root-pstate agent-task-id agent-id)
            (:invokes-map
             (foreign-invoke-query traces-query
                                   agent-task-id
                                   [[agent-task-id root-invoke-id]]
                                   10000))
          )))



      (bind inv (aor/agent-initiate foo))
      (bind trace (get-trace inv))

      ;; TODO: <<<<>>>>> how to force retry
      ;;   - easier to put hook to wrap nodes and throw
      ;;   - want tracking of which nodes were executed...
      ;;     - auto-node would be perfect if it emitted elements down
      (bind finv (aor/initiate-fork foo inv {}))


      ;(clojure.pprint/pprint trace)

      ;; TODO: <<<<>>>>>
      ;; - test retry of fork where agg is complete
      ;; - test retry of fork where agg is not complete (something inside was
      ;; forked so it has to retry)
      ;;   - verify subsequent aggregation goes through fine, as fork context
      ;;   will  be set where it normally isn't
      ;; - test retry of agg start node where agg node was forked but it never
      ;; completed, so it has to retry with the forked args
      ;;   - verifies node-agg-res and agg-state were overridden correctly in
      ;;   the PState
      ;; - test retry of agg subgraph for COMPLETED FORKED AGG
      ;;   - so it has to go down and traverse, but shouldn't ack the agg at
      ;;   all

     ))))
