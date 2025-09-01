(ns com.rpl.agent.structured-langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.agent-o-rama.langchain4j.json :as lj]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.structured-langchain4j-agent :refer [StructuredLangChain4jModule]]
   [jsonista.core :as j])
  (:import
   [dev.langchain4j.data.message
    AiMessage
    SystemMessage
    UserMessage]
   [dev.langchain4j.model.chat.response
    ChatResponse]))

;; Mock ChatModel for testing structured responses
(defn create-mock-structured-model
  "Create a mock chat model that returns predefined structured response"
  []
  (reify dev.langchain4j.model.chat.ChatLanguageModel
    (chat [_ _request]
      (let [mock-response "{\"question_type\":\"technical\",\"complexity\":\"moderate\",\"main_topics\":[\"AI frameworks\",\"distributed systems\"],\"answer\":\"Agent-o-rama is a framework for building distributed AI agents.\",\"confidence\":\"high\"}"]
        (-> (ChatResponse/builder)
            (.aiMessage (AiMessage/from mock-response))
            .build)))))

(aor/defagentmodule MockStructuredLangChain4jModule
  [topology]

  ;; Use mock model instead of real OpenAI
  (aor/declare-agent-object
   topology
   "openai-model"
   (create-mock-structured-model))

  (-> (aor/new-agent topology "StructuredLangChain4jAgent")

      ;; Single node that analyzes user question and returns structured response
      (aor/node
       "analyze-question"
       nil
       (fn [agent-node user-question]
         (let [model (aor/get-agent-object agent-node "openai-model")
               system-msg (SystemMessage.
                           "You are an intelligent question analyzer.")
               user-msg (UserMessage. user-question)

               ;; Mock the structured response (in real implementation this would use :response-format)
               response (lc4j/chat model (lc4j/chat-request [system-msg user-msg]))]

           ;; Parse and return structured response
           (aor/result! agent-node
                        (j/read-value (.text (.aiMessage response)))))))))

(deftest structured-langchain4j-agent-test
  (testing "StructuredLangChain4jAgent with mock structured model"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MockStructuredLangChain4jModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        MockStructuredLangChain4jModule))
            agent (aor/agent-client manager "StructuredLangChain4jAgent")]

        (testing "returns structured response with all expected fields"
          (let [result (aor/agent-invoke agent "What is agent-o-rama?")]
            (is (map? result))
            (is (= "technical" (get result "question_type")))
            (is (= "moderate" (get result "complexity")))
            (is (vector? (get result "main_topics")))
            (is (= ["AI frameworks" "distributed systems"] (get result "main_topics")))
            (is (string? (get result "answer")))
            (is (.contains (get result "answer") "Agent-o-rama"))
            (is (= "high" (get result "confidence")))))))))