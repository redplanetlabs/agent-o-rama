(ns com.rpl.agent.basic.streaming-langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.streaming-langchain4j-agent :refer [StreamingLangChain4jAgentModule]])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiStreamingChatModel]))

;; Test agent module using real OpenAI streaming model
(aor/defagentmodule TestStreamingLangChain4jModule
  [topology]

  ;; Declare OpenAI API key as agent object
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (or (System/getenv "OPENAI_API_KEY") "test-key"))

  ;; Build OpenAI streaming chat model with configuration
  (aor/declare-agent-object-builder
   topology
   "openai-streaming-model"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         .build)))

  (-> (aor/new-agent topology "StreamingLangChain4jAgent")

      ;; Single node that sends user message to streaming OpenAI model
      (aor/node
       "streaming-chat"
       nil
       (fn [agent-node user-message]
         (let [model (aor/get-agent-object agent-node "openai-streaming-model")
               messages [(SystemMessage. "You are a helpful assistant.")
                         (UserMessage. user-message)]

               ;; Send chat request to streaming OpenAI model
               ;; Streaming chunks are automatically emitted by agent-o-rama
               response (lc4j/chat
                         model
                         (lc4j/chat-request
                          messages
                          {:temperature 0.7 :max-output-tokens 200}))
               response-text (.text (.aiMessage response))]

           (aor/result! agent-node response-text))))))

(deftest streaming-langchain4j-agent-test
  (testing "StreamingLangChain4jAgent with real OpenAI streaming model"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc TestStreamingLangChain4jModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          TestStreamingLangChain4jModule))
              agent (aor/agent-client manager "StreamingLangChain4jAgent")]

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
                  (is (= combined-chunks final-result) "Streaming chunks should combine to final result")))))

          (testing "handles different questions"
            (let [invoke (aor/agent-initiate agent "Explain machine learning briefly")
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

      (println "Skipping StreamingLangChain4jAgent test - OPENAI_API_KEY not set"))))