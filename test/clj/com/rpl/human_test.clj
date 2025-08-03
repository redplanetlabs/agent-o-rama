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
             agent-node
             "a"
             "agg"
             (fn [agent-node v]
               (let [h (aor/get-human-input agent-node (str "ABC " v))]
                 (aor/emit! agent-node "agg" [v h]))))
            (aor/node
             agent-node
             "b"
             "agg"
             (fn [agent-node v]
               (let [h (aor/get-human-input agent-node (str "DEF " v))]
                 (aor/emit! agent-node "agg" [v h]))))
            (aor/agg-node
             agent-node
             "agg"
             nil
             aggs/+vec-agg
             (fn [agent-node agg-state _]
               (let [h (aor/get-human-input agent-node "XYZ")]
                 (aor/result! agent-node [agg-state h])
               )))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 1})
     (bind module-name (get-module-name module))
     (bind agent-manager (aor/agent-manager ipc module-name))
     (bind foo (aor/agent-client agent-manager "foo"))
     (bund inv1 (aor/agent-initiate foo 0))
     (bund inv2 (aor/agent-initiate foo 10))
     ;; TODO: <<<<>>>>
     ;; - test pending-human-inputs / pending-human-inputs-async
     ;; - test provide-human-input / provide-human-input-async
     ;; - test with many pending and that they route correctly
     ;; - test next-step / next-step-async
     ;; - also test pagination and tracing topology here
    )))
