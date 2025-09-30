(ns com.rpl.agent.basic.stream-all-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.stream-all-agent :refer [StreamAllAgentModule]]))

(deftest stream-all-agent-test
  ;; Streaming across multiple invocations tests ability to track streaming
  ;; chunks from multiple concurrent agent executions, verifying the invoke-id
  ;; mapping and callback behavior.
  (System/gc)
  (testing "StreamAllAgent"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc StreamAllAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        StreamAllAgentModule))
            agent   (aor/agent-client manager "StreamAllAgent")]

        (testing "subscribes to streaming from multiple invocations"
          (let [chunks-by-invoke (atom {})
                invoke-ids-seen (atom #{})
                ;; Start invocations first
                invoke1 (aor/agent-initiate agent {:task-id "test-1" :items-to-process 3})
                invoke2 (aor/agent-initiate agent {:task-id "test-2" :items-to-process 2})]

            ;; Then subscribe to streaming chunks from ALL invocations
            (aor/agent-stream-all
             agent
             invoke1
             "process-task"
             (fn [_invoke-id->all-chunks invoke-id->new-chunks _reset-invoke-ids _complete?]
               (doseq [[invoke-id new-chunks] invoke-id->new-chunks]
                 (swap! invoke-ids-seen conj invoke-id)
                 (swap! chunks-by-invoke update invoke-id
                        (fn [existing]
                          (concat (or existing []) new-chunks))))))

            ;; Get final results for all invocations
            (let [result1 (aor/agent-result agent invoke1)
                  result2 (aor/agent-result agent invoke2)]

              ;; Verify final results
              (is (= "test-1" (:task-id result1)))
              (is (= 3 (:total-items result1)))
              (is (= "completed" (:status result1)))

              (is (= "test-2" (:task-id result2)))
              (is (= 2 (:total-items result2)))
              (is (= "completed" (:status result2)))

              ;; Verify streaming chunks were received for all invocations
              (is (= 2 (count @chunks-by-invoke)))
              (is (= 2 (count @invoke-ids-seen)))

              ;; Verify chunk counts match the items processed
              (let [chunks @chunks-by-invoke
                    chunk-counts (set (map count (vals chunks)))]
                (is (= #{2 3} chunk-counts))

                ;; Verify chunk structure
                (doseq [[_invoke-id invoke-chunks] chunks]
                  (is (every? #(contains? % :task-id) invoke-chunks))
                  (is (every? #(contains? % :item-number) invoke-chunks))
                  (is (every? #(= "processing" (:status %)) invoke-chunks))))))))))))