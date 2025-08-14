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
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m]))

(deftest local-subagent-test
  (with-redefs [aor-types/get-config (max-retries-override 0)
                anode/log-node-error (fn [& args])]
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
                 (let [bar (aor/agent-client agent-node "bar")]
                   (aor/emit! agent-node
                              "node1"
                              (aor/agent-invoke bar "some input")
                              (aor/get-human-input agent-node "what?"))
                 )))
              (aor/node
               "node1"
               nil
               (fn [agent-node s s2]
                 (aor/result! agent-node (str s "-" s2)))
              ))
          (-> topology
              (aor/new-agent "bar")
              (aor/node
               "start"
               "q"
               (fn [agent-node input]
                 (aor/emit! agent-node
                            "q"
                            input
                            (aor/get-human-input agent-node
                                                 "Tell me something."))
               ))
              (aor/node
               "q"
               nil
               (fn [agent-node input res]
                 (aor/result!
                  agent-node
                  (str input res (aor/get-human-input agent-node "More.")))
               )))
          (-> topology
              (aor/new-agent "fib")
              (aor/node
               "start"
               nil
               (fn [agent-node v]
                 (let [fib (aor/agent-client agent-node "fib")]
                   (if (#{0 1} v)
                     (aor/result! agent-node 1)
                     (aor/result!
                      agent-node
                      (+ (aor/agent-invoke fib (- v 1))
                         (aor/agent-invoke fib (- v 2)))
                     ))
                 ))))
          (-> topology
              (aor/new-agent "error")
              (aor/node
               "start"
               nil
               (fn [agent-node]
                 (throw (ex-info "fail" {})))))
          (-> topology
              (aor/new-agent "error2")
              (aor/node
               "start"
               nil
               (fn [agent-node]
                 (let [error (aor/agent-client agent-node "error")]
                   (aor/result! (aor/agent-invoke error))
                 ))))
          (->
            topology
            (aor/new-agent "all-methods")
            (aor/node
             "start"
             nil
             (fn [agent-node foo-inv foo-invoke-id]
               (let [bar     (aor/agent-client agent-node "bar")
                     fib     (aor/agent-client agent-node "fib")
                     inv     (aor/agent-initiate bar "0")
                     pending (volatile! nil)]
                 (loop [step (aor/agent-next-step bar inv)
                        i    1]
                   (when (= i 1)
                     (vreset! pending (aor/pending-human-inputs bar inv)))
                   (if (aor/human-input-request? step)
                     (do
                       (aor/provide-human-input bar step (str i))
                       (recur (aor/agent-next-step bar inv) (inc i)))))

                 (aor/result!
                  agent-node
                  [@pending
                   (aor/agent-result bar inv)
                   (aor/agent-invoke fib 2)
                   (aor/agent-fork fib foo-inv {foo-invoke-id [1]})
                   (aor/agent-result
                    fib
                    (aor/agent-initiate-fork fib foo-inv {foo-invoke-id [2]}))
                  ])
               ))))
         ))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind fib (aor/agent-client agent-manager "fib"))
       (bind error2 (aor/agent-client agent-manager "error2"))
       (bind all-methods (aor/agent-client agent-manager "all-methods"))

       (bind fib-root
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "fib")))
       (bind all-methods-root
         (foreign-pstate ipc
                         module-name
                         (po/agent-root-task-global-name "all-methods")))
       (bind all-methods-trace
         (foreign-query ipc
                        module-name
                        (queries/tracing-query-name "all-methods")))

       (bind fib-inv (aor/agent-initiate fib 5))
       (is (= 8 (aor/agent-result fib fib-inv)))

       (bind inv (aor/agent-initiate foo))
       (bind step (aor/agent-next-step foo inv))
       (is (aor/human-input-request? step))
       (is (= (:prompt step) "Tell me something."))
       (aor/provide-human-input foo step "XY")
       (bind step (aor/agent-next-step foo inv))
       (is (aor/human-input-request? step))
       (is (= (:prompt step) "More."))
       (aor/provide-human-input foo step "ZZ")
       (bind step (aor/agent-next-step foo inv))
       (is (aor/human-input-request? step))
       (is (= (:prompt step) "what?"))
       (aor/provide-human-input foo step "CBB")
       (bind step (aor/agent-next-step foo inv))
       (is (not (aor/human-input-request? step)))
       (is (= "some inputXYZZ-CBB" (:result step)))

       (is (thrown? Exception (aor/agent-invoke error2)))

       (bind root-invoke-id
         (foreign-select-one [(keypath (:agent-invoke-id fib-inv))
                              :root-invoke-id]
                             fib-root
                             {:pkey (:task-id fib-inv)}))
       (bind inv (aor/agent-initiate all-methods fib-inv root-invoke-id))
       (bind res (aor/agent-result all-methods inv))

       (is (= (rest res) ["012" 2 1 2]))
       (bind pending (first res))
       (is (= 1 (count pending)))
       (is (= "Tell me something."
              (-> pending
                  first
                  :prompt)))

       (bind agent-task-id (:task-id inv))
       (bind agent-id (:agent-invoke-id inv))

       (bind root-invoke-id
         (foreign-select-one [(keypath agent-id) :root-invoke-id]
                             all-methods-root
                             {:pkey agent-task-id}))

       (bind trace
         (foreign-invoke-query all-methods-trace
                               agent-task-id
                               [[agent-task-id root-invoke-id]]
                               10000))

       (is
        (trace-matches?
         (:invokes-map trace)
         {!id1
          {:agg-invoke-id nil
           :agent-id      ?agent-id
           :agent-task-id ?agent-task-id
           :emits         []
           :node          "start"
           :nested-ops    [{:start-time-millis ?t1
                            :finish-time-millis ?t2
                            :type :agent-call
                            :info
                            {"op"         "initiate"
                             "args"       ["0"]
                             "result"     !bar-inv1
                             "agent-module-name" ?module-name
                             "agent-name" "bar"}}
                           {:start-time-millis ?t3
                            :finish-time-millis ?t4
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !bar-inv1
                             "result"       {:prompt    "Tell me something."
                                             :invoke-id !node-inv1
                                             :node      "start"}
                             "agent-module-name" ?module-name
                             "agent-name"   "bar"}}
                           {:start-time-millis ?t5
                            :finish-time-millis ?t6
                            :type :agent-call
                            :info
                            {"op"           "pendingHumanInputs"
                             "agent-invoke" !bar-inv1
                             "result"       [{:prompt "Tell me something."
                                              :node   "start"}]
                             "agent-module-name" ?module-name
                             "agent-name"   "bar"}}
                           {:start-time-millis ?t7
                            :finish-time-millis ?t8
                            :type :agent-call
                            :info
                            {"op"         "provideHumanInput"
                             "request"    {:prompt "Tell me something."}
                             "response"   "1"
                             "agent-module-name" ?module-name
                             "agent-name" "bar"}}
                           {:start-time-millis ?t9
                            :finish-time-millis ?t10
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !bar-inv1
                             "result"       {:prompt "More."}
                             "agent-module-name" ?module-name
                             "agent-name"   "bar"}}
                           {:start-time-millis ?t11
                            :finish-time-millis ?t12
                            :type :agent-call
                            :info
                            {"op"         "provideHumanInput"
                             "request"    {:prompt "More."}
                             "response"   "2"
                             "agent-module-name" ?module-name
                             "agent-name" "bar"}}
                           {:start-time-millis ?t13
                            :finish-time-millis ?t14
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !bar-inv1
                             "result"       {:result "012"}
                             "agent-module-name" ?module-name
                             "agent-name"   "bar"}}
                           {:start-time-millis ?t15
                            :finish-time-millis ?t16
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !bar-inv1
                             "result"       {:result "012"}
                             "agent-module-name" ?module-name
                             "agent-name"   "bar"}}
                           {:start-time-millis ?t17
                            :finish-time-millis ?t18
                            :type :agent-call
                            :info
                            {"op"         "initiate"
                             "args"       [2]
                             "result"     !fib-inv2
                             "agent-module-name" ?module-name
                             "agent-name" "fib"}}
                           {:start-time-millis ?t19
                            :finish-time-millis ?t20
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !fib-inv2
                             "result"       {:result 2}
                             "agent-module-name" ?module-name
                             "agent-name"   "fib"}}
                           {:start-time-millis ?t21
                            :finish-time-millis ?t22
                            :type :agent-call
                            :info
                            {"op"           "initiateFork"
                             "invoke"       !fib-inv3
                             "new-args-map" {!root-id [1]}
                             "result"       !fib-inv4
                             "agent-module-name" ?module-name
                             "agent-name"   "fib"}}
                           {:start-time-millis ?t23
                            :finish-time-millis ?t24
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !fib-inv4
                             "result"       {:result 1}
                             "agent-module-name" ?module-name
                             "agent-name"   "fib"}}
                           {:start-time-millis ?t25
                            :finish-time-millis ?t26
                            :type :agent-call
                            :info
                            {"op"           "initiateFork"
                             "invoke"       !fib-inv3
                             "new-args-map" {!root-id [2]}
                             "result"       !fib-inv5
                             "agent-module-name" ?module-name
                             "agent-name"   "fib"}}
                           {:start-time-millis ?t27
                            :finish-time-millis ?t28
                            :type :agent-call
                            :info
                            {"op"           "nextStep"
                             "agent-invoke" !fib-inv5
                             "result"       {:result 2}
                             "agent-module-name" ?module-name
                             "agent-name"   "fib"}}
                          ]}}
         (m/guard
          (and
           (= ?agent-id agent-id)
           (= ?agent-task-id agent-task-id)
           (= ?module-name module-name)
           (<=
            ?t1
            ?t2
            ?t3
            ?t4
            ?t5
            ?t6
            ?t7
            ?t8
            ?t9
            ?t10
            ?t11
            ?t12
            ?t13
            ?t14
            ?t15
            ?t16
            ?t17
            ?t18
            ?t19
            ?t20
            ?t21
            ?t22
            ?t23
            ?t24
            ?t25
            ?t26
            ?t27
            ?t28)
          ))))
      ))))

(deftest mirror-subagent-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module1
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node v]
               (aor/result! agent-node (inc v))
             )))
       ))
     (bind module-name1 (get-module-name module1))
     (bind module2
       (aor/agentmodule
        [topology]
        (aor/declare-cluster-agent topology "foo2" module-name1 "foo")
        (-> topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node v]
               (let [foo2 (aor/agent-client agent-node "foo2")]
                 (aor/result! agent-node (aor/agent-invoke foo2 (* 10 v))))
             )))
       ))
     (bind module-name2 (get-module-name module2))
     (rtest/launch-module! ipc module1 {:tasks 2 :threads 1})
     (rtest/launch-module! ipc module2 {:tasks 4 :threads 2})
     (bind agent-manager (aor/agent-manager ipc module-name2))
     (bind foo (aor/agent-client agent-manager "foo"))

     (is (= 31 (aor/agent-invoke foo 3)))
    )))
