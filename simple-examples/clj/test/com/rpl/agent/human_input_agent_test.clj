(ns com.rpl.agent.human-input-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.langchain4j :as lc4j]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.human-input-agent :refer [HumanInputAgentModule human-helpful?]])
  (:import
   [com.rpl.agentorama
    HumanInputRequest]
   [dev.langchain4j.data.message
    UserMessage]
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;; Test agent module using OpenAI
(aor/defagentmodule TestHumanInputAgentModule
  [topology]
  (aor/declare-agent-object topology
                            "openai-api-key"
                            (or (System/getenv "OPENAI_API_KEY") "test-key"))
  (aor/declare-agent-object-builder
   topology
   "openai"
   (fn [setup]
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))
  (->
   topology
   (aor/new-agent "HumanInputAgent")
   (aor/node
    "chat"
    nil
    (fn [agent-node user-message]
      (let [openai (aor/get-agent-object agent-node "openai")
            response (-> (lc4j/chat openai [(UserMessage. user-message)])
                         .aiMessage
                         .text)
            helpful? (human-helpful? agent-node response)]
        (aor/result! agent-node
                     {:response response
                      :helpful helpful?}))))))

(deftest human-input-agent-test
  (testing "HumanInputAgent handles human input correctly"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc TestHumanInputAgentModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          TestHumanInputAgentModule))
              agent (aor/agent-client manager "HumanInputAgent")]

          (testing "processes user message and collects helpfulness feedback"
            (let [invoke (aor/agent-initiate agent "What is AI?")]

              ;; Handle helpfulness input request
              (let [step1 (aor/agent-next-step agent invoke)]
                (is (instance? HumanInputRequest step1))
                (is (.contains (:prompt step1) "AI Response"))
                (is (.contains (:prompt step1) "Was this response helpful?"))
                (aor/provide-human-input agent step1 "y"))

              ;; Get final result
              (let [result (aor/agent-result agent invoke)]
                (is (string? (:response result)))
                (is (= true (:helpful result))))))

          (testing "handles validation loop"
            (let [invoke (aor/agent-initiate agent "Tell me about ML")]

              ;; First try with invalid input
              (let [step1 (aor/agent-next-step agent invoke)]
                (aor/provide-human-input agent step1 "maybe"))

              ;; Should get validation prompt
              (let [step2 (aor/agent-next-step agent invoke)]
                (is (instance? HumanInputRequest step2))
                (is (.contains (:prompt step2) "Please answer 'y' or 'n'"))
                (aor/provide-human-input agent step2 "n"))

              ;; Get final result
              (let [result (aor/agent-result agent invoke)]
                (is (string? (:response result)))
                (is (= false (:helpful result))))))))

      (println "Skipping HumanInputAgent test - OPENAI_API_KEY not set"))))