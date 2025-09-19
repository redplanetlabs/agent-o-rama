(ns com.rpl.agent.basic.streaming-langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.streaming-langchain4j-agent :refer [StreamingLangChain4jAgentModule]]))

(deftest streaming-langchain4j-agent-test
  ;; Test verifies that the StreamingLangChain4jAgentModule correctly
  ;; handles streaming responses from OpenAI's API
  (System/gc)
  (testing "StreamingLangChain4jAgent"
    (testing "with real OpenAI streaming model"
      (if (System/getenv "OPENAI_API_KEY")
        (with-open [ipc (rtest/create-ipc)]
          (rtest/launch-module! ipc StreamingLangChain4jAgentModule {:tasks 1 :threads 1})

          (let [manager (aor/agent-manager ipc
                                           (rama/get-module-name
                                            StreamingLangChain4jAgentModule))
                agent   (aor/agent-client manager "StreamingLangChain4jAgent")]

            (testing "receives streaming chunks and final result"
              (let [invoke (aor/agent-initiate agent "What is AI?")
                    streaming-chunks (atom [])]

                ;; Subscribe to streaming chunks
                (aor/agent-stream
                 agent
                 invoke
                 "streaming-chat"
                 (fn [all-chunks new-chunks reset? complete?]
                   (doseq [chunk new-chunks]
                     (swap! streaming-chunks conj chunk))))

                ;; Wait for final result
                (let [final-result (aor/agent-result agent invoke)]
                  ;; Verify we received streaming chunks
                  (is (> (count @streaming-chunks) 0) "Should receive streaming chunks")

                  ;; Verify final result is a complete response
                  (is (string? final-result))
                  (is (> (count final-result) 10) "Should get substantial response")

                  ;; Verify streaming chunks combine to final result
                  (let [combined-chunks (apply str @streaming-chunks)]
                    (is (= combined-chunks final-result)
                        "Streaming chunks should combine to final result")))))

            (testing "handles different questions"
              (let [invoke (aor/agent-initiate agent "Explain ML in 2 sentences")
                    streaming-chunks (atom [])]

                (aor/agent-stream
                 agent
                 invoke
                 "streaming-chat"
                 (fn [all-chunks new-chunks reset? complete?]
                   (doseq [chunk new-chunks]
                     (swap! streaming-chunks conj chunk))))

                (let [final-result (aor/agent-result agent invoke)]
                  (is (> (count @streaming-chunks) 0))
                  (is (string? final-result))
                  (is (> (count final-result) 10)))))))

        (println "Skipping StreamingLangChain4jAgent test - OPENAI_API_KEY not set")))))
