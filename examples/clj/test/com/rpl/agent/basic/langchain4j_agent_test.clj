(ns com.rpl.agent.basic.langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.langchain4j-agent
    :refer [LangChain4jAgentModule]]))

(deftest langchain4j-agent-test
  ;; Tests the LangChain4jAgent's integration with OpenAI models
  ;; and its ability to process chat requests with configured parameters
  (System/gc)
  (testing "LangChain4jAgent with real OpenAI model"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc LangChain4jAgentModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          LangChain4jAgentModule))
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
