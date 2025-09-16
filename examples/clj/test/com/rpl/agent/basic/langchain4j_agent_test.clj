(ns com.rpl.agent.basic.langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.langchain4j-agent
    :refer [LangChain4jAgentModule]])
  (:import
   [dev.langchain4j.data.message
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;; Test agent module using real OpenAI
(aor/defagentmodule TestLangChain4jModule
  [topology]

  ;; Declare OpenAI API key as agent object
  (aor/declare-agent-object
   topology
   "openai-api-key"
   (or (System/getenv "OPENAI_API_KEY") "test-key"))

  ;; Build OpenAI chat model with configuration
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         (.temperature 0.7)
         (.maxTokens (int 200))
         .build)))

  (-> (aor/new-agent topology "LangChain4jAgent")

      ;; Single node that sends user message to OpenAI and returns response
      (aor/node
       "chat"
       nil
       (fn [agent-node ^String user-message]
         (let [model         (aor/get-agent-object agent-node "openai-model")
               messages      [(SystemMessage. "You are a helpful assistant.")
                              (UserMessage. user-message)]

               ;; Send chat request to OpenAI
               response      (lc4j/chat model
                                        (lc4j/chat-request messages
                                                           {:temperature       0.7
                                                            :max-output-tokens 200}))
               response-text (.text (.aiMessage response))]

           (aor/result! agent-node response-text))))))

(deftest langchain4j-agent-test
  (testing "LangChain4jAgent with real OpenAI model"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc TestLangChain4jModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          TestLangChain4jModule))
              agent   (aor/agent-client manager "LangChain4jAgent")]

          (testing "returns response from OpenAI chat model"
            (let [result (aor/agent-invoke agent "What is artificial intelligence?")]
              (is (string? result))
              (is (> (count result) 20)) ; Should get a substantial response
              (is (not (empty? result)))))

          (testing "handles different types of questions"
            (let [result (aor/agent-invoke agent "Explain machine learning briefly")]
              (is (string? result))
              (is (> (count result) 10))))))

      (println "Skipping LangChain4jAgent test - OPENAI_API_KEY not set"))))
