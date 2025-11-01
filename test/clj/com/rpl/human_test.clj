(ns com.rpl.human-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]
   [meander.epsilon :as m])
  (:import
   [com.rpl.agentorama
    AgentComplete
    HumanInputRequest]
   [java.util.concurrent
    CompletableFuture]))


(deftest human-in-the-loop-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/agg-start-node
             "start"
             ["a" "b"]
             (fn [agent-node v]
               (aor/emit! agent-node "a" (+ v 1))
               (aor/emit! agent-node "a" (+ v 2))
               (aor/emit! agent-node "b" (+ v 3))
             ))
            (aor/node
             "a"
             "agg"
             (fn [agent-node v]
               (let [h (aor/get-human-input agent-node (str "ABC " v))]
                 (aor/emit! agent-node "agg" [v h]))))
            (aor/node
             "b"
             "agg"
             (fn [agent-node v]
               (let [h1 (aor/get-human-input agent-node (str "DEF " v))
                     h2 (aor/get-human-input agent-node (str "GHI " h1))]
                 (aor/emit! agent-node "agg" [v (str h1 "-" h2)]))))
            (aor/agg-node
             "agg"
             nil
             aggs/+vec-agg
             (fn [agent-node agg-state _]
               (let [h (aor/get-human-input agent-node "XYZ")]
                 (aor/result! agent-node [agg-state h])
               )))
        )))
     (launch-module-without-eval-agent! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind invokes-page-query (:invokes-page-query (aor-types/underlying-objects foo)))
     (bind traces-query (:tracing-query (aor-types/underlying-objects foo)))
     (bind inv1 (aor/agent-initiate foo 0))
     (bind inv2 (aor/agent-initiate foo 10))

     (bind [agent-task-id1 agent-id1] (tc/extract-invoke inv1))
     (bind root1
       (foreign-select-one [(keypath agent-id1) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id1}))

     (is (not (aor/agent-invoke-complete? foo inv1)))
     (bind h (aor/agent-next-step foo inv1))
     (is (not (aor/agent-invoke-complete? foo inv1)))
     (aor/agent-next-step foo inv2)
     (is (instance? HumanInputRequest h))
     (is (condition-attained? (= 3
                                 (-> foo
                                     (aor/pending-human-inputs inv1)
                                     count))))

     (bind page
       (:agent-invokes (foreign-invoke-query invokes-page-query 10 nil)))
     (is (= 2 (count page)))
     (is (every? :human-request? page))


     (bind trace
       (foreign-invoke-query traces-query
                             agent-task-id1
                             [[agent-task-id1 root1]]
                             10000))
     (is
      (trace-matches?
       (:invokes-map trace)
       {!id1
        {:agent-id      ?agent-id
         :agent-task-id ?agent-task-id
         :node          "a"
         :input         [1]
         :nested-ops    nil
         :human-request {:agent-task-id ?agent-task-id
                         :agent-id      ?agent-id
                         :node          "a"
                         :node-task-id  ?agent-task-id
                         :invoke-id     !id1
                         :prompt        "ABC 1"
                         :uuid          !uuid1}}
        !id2
        {:agent-id      ?agent-id
         :agent-task-id ?agent-task-id
         :node          "a"
         :input         [2]
         :nested-ops    nil
         :human-request {:agent-task-id ?agent-task-id
                         :agent-id      ?agent-id
                         :node          "a"
                         :node-task-id  !task-id1
                         :invoke-id     !id2
                         :prompt        "ABC 2"
                         :uuid          !uuid2}}
        !id3
        {:agent-id      ?agent-id
         :agent-task-id ?agent-task-id
         :node          "b"
         :input         [3]
         :nested-ops    nil
         :human-request {:agent-task-id ?agent-task-id
                         :agent-id      ?agent-id
                         :node          "b"
                         :node-task-id  !task-id2
                         :invoke-id     !id3
                         :prompt        "DEF 3"
                         :uuid          !uuid3}}
       }
       (m/guard
        (and (= ?agent-id agent-id1)
             (= ?agent-task-id agent-task-id1)))
      ))

     (bind [r0 r1 r2 :as items]
       (sort-by :prompt (aor/pending-human-inputs foo inv1)))

     (is (= (aor/pending-human-inputs foo inv1)
            (.get (aor/pending-human-inputs-async foo inv1))))
     (is (= ["ABC 1" "ABC 2" "DEF 3"] (mapv :prompt items)))
     (aor/provide-human-input foo r0 "hello there")

     (is
      ;; provide-human-input only blocks until it's delivered, not until node is
      ;; complete
      (condition-attained?
       (trace-matches?
        (:invokes-map
         (foreign-invoke-query traces-query
                               agent-task-id1
                               [[agent-task-id1 root1]]
                               10000))
        {!id1
         {:agent-id      ?agent-id
          :agent-task-id ?agent-task-id
          :node          "a"
          :input         [1]
          :nested-ops    [{:type :human-input
                           :info
                           {"prompt" "ABC 1"
                            "result" "hello there"}}]
          :human-request nil}
         !id2
         {:agent-id      ?agent-id
          :agent-task-id ?agent-task-id
          :node          "a"
          :input         [2]
          :human-request {:agent-task-id ?agent-task-id
                          :agent-id      ?agent-id
                          :node          "a"
                          :node-task-id  !task-id1
                          :invoke-id     !id2
                          :prompt        "ABC 2"
                          :uuid          !uuid2}}
         !id3
         {:agent-id      ?agent-id
          :agent-task-id ?agent-task-id
          :node          "b"
          :input         [3]
          :human-request {:agent-task-id ?agent-task-id
                          :agent-id      ?agent-id
                          :node          "b"
                          :node-task-id  !task-id2
                          :invoke-id     !id3
                          :prompt        "DEF 3"
                          :uuid          !uuid3}}
        }
        (m/guard
         (and (= ?agent-id agent-id1)
              (= ?agent-task-id agent-task-id1)))
       )))

     (aor/provide-human-input foo r1 "aa")
     (aor/provide-human-input foo r2 "bb")
     (bind h (aor/agent-next-step foo inv1))
     (is (not (aor/agent-invoke-complete? foo inv1)))
     (is (= "GHI bb" (:prompt h)))
     (aor/provide-human-input foo h "blah")

     (bind h (.get (aor/agent-next-step-async foo inv1)))
     (is (= "XYZ" (:prompt h)))
     (.get (aor/provide-human-input-async foo h "car"))


     (bind r (aor/agent-next-step foo inv1))
     (bind expected [[[1 "hello there"] [2 "aa"] [3 "bb-blah"]] "car"])
     (is (instance? AgentComplete r))
     (is (= expected (:result r)))
     (is (= expected (aor/agent-result foo inv1)))
     (is (aor/agent-invoke-complete? foo inv1))


     (bind page
       (:agent-invokes (foreign-invoke-query invokes-page-query 10 nil)))
     (is (= 2 (count page)))
     (is (= 1 (count (filter :human-request? page))))

     (bind h (aor/agent-next-step foo inv2))
     (is (instance? HumanInputRequest h))
     (is (condition-attained? (= 3
                                 (-> foo
                                     (aor/pending-human-inputs inv2)
                                     count))))

     (bind [r0 r1 r2 :as items]
       (sort-by :prompt (aor/pending-human-inputs foo inv2)))

     (is (= (aor/pending-human-inputs foo inv2)
            (.get (aor/pending-human-inputs-async foo inv2))))
     (is (= ["ABC 11" "ABC 12" "DEF 13"] (mapv :prompt items)))
     (aor/provide-human-input foo r0 "a b c")
     (aor/provide-human-input foo r1 "xy")
     (aor/provide-human-input foo r2 "apple banana")
     (bind h (aor/agent-next-step foo inv2))
     (is (= "GHI apple banana" (:prompt h)))
     (aor/provide-human-input foo h "blah2")

     (bind h (.get (aor/agent-next-step-async foo inv2)))
     (is (= "XYZ" (:prompt h)))
     (.get (aor/provide-human-input-async foo h "alice"))


     (bind r (aor/agent-next-step foo inv2))
     (bind expected
       [[[11 "a b c"] [12 "xy"] [13 "apple banana-blah2"]] "alice"])
     (is (instance? AgentComplete r))
     (is (= expected (:result r)))
     (is (= expected (aor/agent-result foo inv2)))
    )))
