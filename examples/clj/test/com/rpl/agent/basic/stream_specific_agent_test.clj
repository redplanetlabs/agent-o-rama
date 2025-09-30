(ns com.rpl.agent.basic.stream-specific-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.stream-specific-agent :refer [StreamSpecificAgentModule]]))

(deftest stream-specific-agent-test
  ;; Streaming from a specific invocation tests the ability to subscribe to
  ;; streaming chunks from one particular agent execution using its invoke-id,
  ;; verifying selective monitoring and agent-next-step behavior.
  (System/gc)
  (testing "StreamSpecificAgent"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc StreamSpecificAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        StreamSpecificAgentModule))
            agent   (aor/agent-client manager "StreamSpecificAgent")]

        (testing "subscribes to streaming from a specific invocation only"
          (let [chunks-received (atom [])
                ;; Start three invocations
                invoke1 (aor/agent-initiate agent {:task-id "test-1" :items-to-process 3})
                invoke2 (aor/agent-initiate agent {:task-id "test-2" :items-to-process 4})
                invoke3 (aor/agent-initiate agent {:task-id "test-3" :items-to-process 2})
                ;; Get the invoke-id for the specific invocation to monitor
                target-invoke-id (:agent-invoke-id invoke2)]

            ;; Subscribe to streaming from only invoke2.
            ;; agent-stream-specific with agent-invoke-id targets this specific invocation.
            (aor/agent-stream-specific
             agent
             invoke2
             "process-task"
             target-invoke-id
             (fn [_all-chunks new-chunks _reset? _complete?]
               (doseq [chunk new-chunks]
                 (swap! chunks-received conj chunk))))

            ;; Get final results
            (let [result1 (aor/agent-result agent invoke1)
                  result2 (aor/agent-result agent invoke2)
                  result3 (aor/agent-result agent invoke3)]

              ;; Verify final results
              (is (= "test-1" (:task-id result1)))
              (is (= 3 (:total-items result1)))
              (is (= "completed" (:status result1)))

              (is (= "test-2" (:task-id result2)))
              (is (= 4 (:total-items result2)))
              (is (= "completed" (:status result2)))

              (is (= "test-3" (:task-id result3)))
              (is (= 2 (:total-items result3)))
              (is (= "completed" (:status result3)))

              ;; Verify streaming chunks were received ONLY from invoke2
              (is (= 4 (count @chunks-received)))
              (is (every? #(= "test-2" (:task-id %)) @chunks-received))

              ;; Verify chunk structure
              (is (every? #(contains? % :task-id) @chunks-received))
              (is (every? #(contains? % :item-number) @chunks-received))
              (is (every? #(= "processing" (:status %)) @chunks-received))

              ;; Verify we got the expected item numbers
              (is (= #{0 1 2 3} (set (map :item-number @chunks-received)))))))))))