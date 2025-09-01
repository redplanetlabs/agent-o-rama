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
   [dev.langchain4j.model.chat
    ChatModel]
   [dev.langchain4j.model.chat.response
    ChatResponse]
   [dev.langchain4j.model.output
    Response]))

;; Mock ChatLanguageModel for testing
(defn create-mock-model
  "Create a mock chat model that returns predefined responses"
  [responses]
  #_(let [call-count (atom 0)]
      (reify
       ChatLanguageModel
       (chat [_ _request]
         (let [idx @call-count
               _ (swap! call-count inc)
               response-text (get responses idx "Default response")]
           (ChatResponse/builder
            (.aiMessage (AiMessage/from response-text))
            .build))))))

(aor/defagentmodule MockLangChain4jModule
  [topology]

  ;; Use mock model instead of real OpenAI
  (aor/declare-agent-object
   topology
   "openai-model"
   (create-mock-model
    ["Functional programming is a paradigm that treats computation as evaluation of mathematical functions."
     "For example, in functional programming, you use pure functions that always return the same output for the same input, avoid side effects, and use immutability."
     "FP emphasizes pure functions and immutability."]))

  (->
    topology
    (aor/new-agent "LangChain4jAgent")

    ;; Initial chat node that starts conversation
    (aor/node
     "start-chat"
     "continue-chat"
     (fn [agent-node {:keys [system-prompt user-message]}]
       (let [model         (aor/get-agent-object agent-node "openai-model")
             messages      [(SystemMessage. system-prompt)
                            (UserMessage. user-message)]

             response      (lc4j/chat model
                                      (lc4j/chat-request messages
                                                         {:temperature       0.7
                                                          :max-output-tokens
                                                          200}))
             ai-message    (.aiMessage response)
             response-text (.text ai-message)]

         (aor/emit! agent-node
                    "continue-chat"
                    {:conversation-history (conj messages ai-message)
                     :last-response        response-text
                     :turn-count           1}))))

    ;; Continue conversation with follow-up
    (aor/node
     "continue-chat"
     "analyze-conversation"
     (fn [agent-node {:keys [conversation-history last-response turn-count]}]
       (let [model             (aor/get-agent-object agent-node "openai-model")
             follow-up-message (UserMessage.
                                "Can you provide more details or an example?")
             updated-history   (conj conversation-history follow-up-message)

             response          (lc4j/chat model
                                          (lc4j/chat-request updated-history
                                                             {:temperature 0.5
                                                              :max-output-tokens
                                                              300}))
             ai-message        (.aiMessage response)
             response-text     (.text ai-message)]

         (aor/emit! agent-node
                    "analyze-conversation"
                    {:full-conversation  (conj updated-history ai-message)
                     :initial-response   last-response
                     :follow-up-response response-text
                     :total-turns        (inc turn-count)}))))

    ;; Analyze and summarize conversation
    (aor/node
     "analyze-conversation"
     nil
     (fn [agent-node
          {:keys [full-conversation initial-response
                  follow-up-response total-turns]}]
       (let [model           (aor/get-agent-object agent-node "openai-model")
             analysis-prompt (str
                              "Summarize this conversation in one sentence: "
                              initial-response
                              " ... " follow-up-response)

             summary-response (lc4j/chat model
                                         (lc4j/chat-request
                                          [(UserMessage. analysis-prompt)]
                                          {:temperature       0.3
                                           :max-output-tokens 50}))
             summary         (.text (.aiMessage summary-response))]

         (aor/result! agent-node
                      {:action               "chat-complete"
                       :total-turns          (inc total-turns)
                       :message-count        (count full-conversation)
                       :initial-response     initial-response
                       :follow-up-response   follow-up-response
                       :conversation-summary summary
                       :model-used           "mock-model"
                       :processed-at         (System/currentTimeMillis)}))))))

(deftest langchain4j-agent-test
  (testing "LangChain4jAgent example with mock model"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MockLangChain4jModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        MockLangChain4jModule))
            agent   (aor/agent-client manager "LangChain4jAgent")]

        (testing "completes multi-turn conversation"
          (let [result (aor/agent-invoke agent
                                         {:system-prompt
                                          "You are a helpful assistant."
                                          :user-message
                                          "What is functional programming?"})]

            (is (= "chat-complete" (:action result)))
            (is (= 2 (:total-turns result)))
            (is (= 5 (:message-count result))) ; system + user + ai + user + ai
            (is (= "mock-model" (:model-used result)))
            (is (number? (:processed-at result)))

            ;; Check responses match our mock
            (is (.contains (:initial-response result) "Functional programming"))
            (is (.contains (:initial-response result) "paradigm"))

            (is (.contains (:follow-up-response result) "example"))
            (is (.contains (:follow-up-response result) "pure functions"))

            (is (.contains (:conversation-summary result) "FP"))
            (is (.contains (:conversation-summary result) "immutability"))))))))
