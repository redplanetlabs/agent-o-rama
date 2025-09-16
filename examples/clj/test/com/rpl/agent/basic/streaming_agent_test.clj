(ns com.rpl.agent.basic.streaming-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.streaming-agent :refer [StreamingAgentModule]]))

(deftest streaming-agent-test
  (System/gc)
  (testing "StreamingAgent example produces expected streaming behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc StreamingAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        StreamingAgentModule))
            agent   (aor/agent-client manager "StreamingAgent")]

        (testing "streams progress chunks and returns final result"
          (let [invoke (aor/agent-initiate agent {:data-size 20 :chunk-size 5})
                chunks-received (atom [])]

            ;; Subscribe to streaming chunks
            (aor/agent-stream
             agent
             invoke
             "process-data"
             (fn [all-chunks new-chunks reset? complete?]
               (doseq [chunk new-chunks]
                 (swap! chunks-received conj chunk))))

            ;; Get final result
            (let [result (aor/agent-result agent invoke)]
              ;; Verify final result structure
              (is (= "data-processing" (:action result)))
              (is (= 20 (:total-items result)))
              (is (= 4 (:total-chunks result)))
              (is (= 5 (:chunk-size result)))
              (is (number? (:completed-at result)))

              ;; Verify streaming chunks were received
              (is (= 4 (count @chunks-received)))

              ;; Verify chunk structure and progress
              (let [chunks @chunks-received]
                (is (every? #(contains? % :chunk-number) chunks))
                (is (every? #(contains? % :items-processed) chunks))
                (is (every? #(contains? % :progress) chunks))
                (is (every? #(contains? % :items) chunks))

                ;; Verify progress increases
                (is (= [0.25 0.5 0.75 1.0] (map :progress chunks)))

                ;; Verify chunk numbers
                (is (= [0 1 2 3] (map :chunk-number chunks)))

                ;; Verify items processed per chunk
                (is (= [5 5 5 5] (map :items-processed chunks)))))))))))
