(ns com.rpl.agent.basic.stream-reset-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.stream-reset-agent :refer [StreamResetAgentModule
                                                   first-execution?]]))

(deftest stream-reset-agent-test
  ;; Test demonstrates stream reset behavior when a node is retried after failure
  ;; Contracts tested:
  ;; - Node throws exception on first execution
  ;; - Agent automatically retries failed node
  ;; - Streaming subscription receives reset notification
  ;; - agent-stream-reset-info returns correct reset count

  (System/gc)
  (testing "StreamResetAgent"
    (with-open [ipc (rtest/create-ipc)]
      (testing "demonstrates stream reset on node retry"
        ;; Reset the execution flag for this test
        (reset! first-execution? true)

        (rtest/launch-module! ipc StreamResetAgentModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          StreamResetAgentModule))
              agent (aor/agent-client manager "StreamResetAgent")]

          (let [invoke (aor/agent-initiate agent {:data-size 5})
                chunks-received (atom [])
                reset-seen? (atom false)
                complete-seen? (atom false)]

            ;; Subscribe to streaming chunks
            (let [stream (aor/agent-stream
                          agent
                          invoke
                          "process-with-retry"
                          (fn [all-chunks new-chunks reset? complete?]
                            (when reset?
                              (reset! reset-seen? true))
                            (when complete?
                              (reset! complete-seen? true))
                            (doseq [chunk new-chunks]
                              (swap! chunks-received conj chunk))))]

              ;; Wait for final result
              (let [result (aor/agent-result agent invoke)
                    reset-count (aor/agent-stream-reset-info stream)]

                ;; Verify final result
                (is (= "completed" (:status result)))
                (is (= 5 (:total-chunks result)))
                (is (= "Processing completed after retry" (:message result)))

                ;; Verify streaming behavior
                ;; We receive chunks from both the failed attempt and the retry
                (is (= 7 (count @chunks-received))
                    "Should receive 2 chunks from first attempt + 5 chunks from retry")

                ;; Verify chunk data includes both attempts
                (is (= ["chunk-1" "chunk-2" "chunk-1" "chunk-2" "chunk-3" "chunk-4" "chunk-5"]
                       (map :data @chunks-received)))

                ;; Verify chunk numbers include both attempts
                (is (= [0 1 0 1 2 3 4]
                       (map :chunk-number @chunks-received)))

                ;; Verify reset was detected
                (is @reset-seen?
                    "Stream callback should have received reset?=true")

                ;; Verify completion was detected
                (is @complete-seen?
                    "Stream callback should have received complete?=true")

                ;; Verify reset count
                (is (= 1 reset-count)
                    "agent-stream-reset-info should return 1")))))))))