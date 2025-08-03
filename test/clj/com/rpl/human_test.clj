(ns com.rpl.human-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc])
  (:import
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
                     h2 (aor/get-human-input agent-node (str "GHI " v))]
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
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bind inv1 (aor/agent-initiate foo 0))
     (bind inv2 (aor/agent-initiate foo 10))


     (bind h (aor/agent-next-step foo inv1))
     (is (aor-types/NodeHumanInputRequest? h))
     (is (condition-attained? (= 3
                                 (-> foo
                                     (aor/pending-human-inputs inv1)
                                     count))))
     (bind [r0 r1 r2 :as items]
       (sort-by :prompt (aor/pending-human-inputs foo inv1)))

     (is (= (aor/pending-human-inputs foo inv1)
            (.get (aor/pending-human-inputs-async foo inv1))))
     (is (= ["ABC 1" "ABC 2" "DEF 3"] (mapv :prompt items)))
     (aor/provide-human-input foo r0 "hello there")
     (aor/provide-human-input foo r1 "aa")
     (aor/provide-human-input foo r2 "bb")
     (bind h (aor/agent-next-step foo inv1))
     (is (= "GHI 3" (:prompt h)))
     (aor/provide-human-input foo h "blah")

     (bind h (aor/agent-next-step foo inv1))
     (is (= "XYZ" (:prompt h)))
     (.get (aor/provide-human-input-async foo h "car"))

     (is (= [[[1 "hello there"] [2 "aa"] [3 "bb-blah"]] "car"]
            (aor/agent-result foo inv1)))


     ;; TODO: <<<<>>>>
     ;; - test next-step / next-step-async
     ;; - also test pagination and tracing topology here
    )))
