(ns com.rpl.agent.langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.langchain4j-agent :refer [LangChain4jAgentModule]])
  (:import
   [dev.langchain4j.data.message
    AiMessage
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.chat.response
    ChatResponse]))

;; Mock ChatModel for testing
(defn create-mock-model
  "Create a mock chat model that returns predefined response"
  []
  (reify dev.langchain4j.model.chat.ChatLanguageModel
    (chat [_ _request]
      (let [response-text "Agent-o-rama is a framework for building distributed AI agents."]
        (-> (ChatResponse/builder)
            (.aiMessage (AiMessage/from response-text))
            .build)))))

(aor/defagentmodule MockLangChain4jModule
  [topology]

  ;; Use mock model instead of real OpenAI
  (aor/declare-agent-object
   topology
   "openai-model"
   (create-mock-model))

  (-> (aor/new-agent topology "LangChain4jAgent")

      ;; Single node that sends user message to mock model and returns response
      (aor/node
       "chat"
       nil
       (fn [agent-node user-message]
         (let [model (aor/get-agent-object agent-node "openai-model")
               messages [(SystemMessage. "You are a helpful assistant.")
                         (UserMessage. user-message)]

               ;; Send chat request to mock model
               response (lc4j/chat model
                                   (lc4j/chat-request messages
                                                      {:temperature 0.7
                                                       :max-output-tokens 200}))
               response-text (.text (.aiMessage response))]

           (aor/result! agent-node response-text))))))

(deftest langchain4j-agent-test
  (testing "LangChain4jAgent with mock model"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MockLangChain4jModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        MockLangChain4jModule))
            agent (aor/agent-client manager "LangChain4jAgent")]

        (testing "returns response from chat model"
          (let [result (aor/agent-invoke agent "What is agent-o-rama?")]
            (is (string? result))
            (is (.contains result "Agent-o-rama"))
            (is (.contains result "framework"))
            (is (.contains result "distributed AI agents"))))))))