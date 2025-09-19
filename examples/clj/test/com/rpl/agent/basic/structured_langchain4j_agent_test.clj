(ns com.rpl.agent.basic.structured-langchain4j-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.structured-langchain4j-agent :refer [StructuredLangChain4jModule]]))

(deftest structured-langchain4j-agent-test
  (System/gc)
  (testing "StructuredLangChain4jAgent with real OpenAI model"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc StructuredLangChain4jModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          StructuredLangChain4jModule))
              agent   (aor/agent-client manager "StructuredLangChain4jAgent")]

          (testing "returns structured response with all expected fields"
            (let [result (aor/agent-invoke agent "What is agent-o-rama?")]
              (is (map? result))
              (is (contains? result "question_type"))
              (is (contains? result "complexity"))
              (is (contains? result "main_topics"))
              (is (contains? result "answer"))
              (is (contains? result "confidence"))
              (is (string? (get result "answer")))
              (is (vector? (get result "main_topics")))))))
      (println "Skipping test: OPENAI_API_KEY not set"))))
