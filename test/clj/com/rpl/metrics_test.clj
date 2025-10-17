(ns com.rpl.metrics-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.impl.agent-node :as anode]
   [com.rpl.agent-o-rama.impl.analytics :as ana]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [com.rpl.test-common :as tc]))


;; TODO: <<<<>>>>

(def TICKS)

(deftest basic-metrics-test
  (with-redefs [TICKS (atom 0)
                i/SUBSTITUTE-TICK-DEPOTS true

                i/hook:analytics-tick
                (fn [& args] (swap! TICKS inc))


                anode/gen-node-id
                (fn [& args]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))

                ana/max-node-scan-time (fn [] (+ (h/current-time-millis) 60000))

                at/gen-new-agent-id
                (fn [agent-name]
                  (h/random-uuid7-at-timestamp (h/current-time-millis)))]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (-> topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               nil
               (fn [agent-node input]
                   ;; TODO: <<<<>>>>
               )
              )
          )))
       (rtest/launch-module! ipc module {:tasks 2 :threads 2})
       (bind module-name (get-module-name module))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind global-actions-depot
         (:global-actions-depot (aor-types/underlying-objects agent-manager)))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind ana-depot (foreign-depot ipc module-name (po/agent-analytics-tick-depot-name)))

       (bind cycle!
         (fn []
           (reset! TICKS 0)
           (foreign-append! ana-depot nil)
           (is (condition-attained? (> @TICKS 0)))
           (rtest/pause-microbatch-topology! ipc
                                             module-name
                                             aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)
           (rtest/resume-microbatch-topology! ipc
                                              module-name
                                              aor-types/AGENT-ANALYTICS-MB-TOPOLOGY-NAME)))


       (aor/create-evaluator! agent-manager
                              "concise5"
                              "aor/conciseness"
                              {"threshold" "5"}
                              "")

       (ana/add-rule! global-actions-depot
                      "eval1"
                      "foo"
                      {:action-name       "aor/eval"
                       :action-params     {"name" "concise5"}
                       :filter            (aor-types/->AndFilter [])
                       :sampling-rate     1.0
                       :start-time-millis 0
                       :status-filter     :success
                      })

       ;; TODO: <<<<>>>> add another eval with multiple scores


       (bind inv1 (aor/agent-initiate foo "ab"))

      ))))
