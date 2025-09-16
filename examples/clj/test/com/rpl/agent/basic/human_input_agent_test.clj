(ns com.rpl.agent.basic.human-input-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.human-input-agent :refer [HumanInputAgentModule]]
   [clojure.string :as str])
  (:import
   [com.rpl.agentorama
    HumanInputRequest]))

(deftest human-input-agent-test
  ;; Tests the HumanInputAgent's ability to request and process human input
  ;; while integrating with an AI model for generating responses
  (System/gc)
  (testing "HumanInputAgent handles human input correctly"
    (if (System/getenv "OPENAI_API_KEY")
      (with-open [ipc (rtest/create-ipc)]
        (rtest/launch-module! ipc HumanInputAgentModule {:tasks 1 :threads 1})

        (let [manager (aor/agent-manager ipc
                                         (rama/get-module-name
                                          HumanInputAgentModule))
              agent   (aor/agent-client manager "HumanInputAgent")]

          (testing "processes user message and collects helpfulness feedback"
            (let [invoke (aor/agent-initiate agent "What is AI?")]

              ;; Handle helpfulness input request
              (let [step1 (aor/agent-next-step agent invoke)]
                (is (instance? HumanInputRequest step1))
                (is (str/includes? (:prompt step1) "AI Response"))
                (is (str/includes?
                     (:prompt step1)
                     "Was this response helpful?"))
                (aor/provide-human-input agent step1 "y"))

              ;; Get final result
              (let [result (aor/agent-result agent invoke)]
                (is (string? (:response result)))
                (is (= true (:helpful result))))))

          (testing "handles validation loop"
            (let [invoke (aor/agent-initiate agent "Tell me about ML")]
              ;; First try with invalid input
              (let [step1 (aor/agent-next-step agent invoke)]
                (is (str/includes?
                     (:prompt step1)
                     "Was this response helpful? (y/n)"))
                (aor/provide-human-input agent step1 "maybe"))

              ;; Should get validation prompt
              (let [step2 (aor/agent-next-step agent invoke)]
                (is (instance? HumanInputRequest step2))
                (when (instance? HumanInputRequest step2)
                  (is (str/includes?
                       (:prompt step2)
                       "Please answer 'y' or 'n'"))
                  (aor/provide-human-input agent step2 "n")

                  ;; Get final result
                  (let [result (aor/agent-result agent invoke)]
                    (is (string? (:response result)))
                    (is (= false (:helpful result))))))))))

      (println "Skipping HumanInputAgent test - OPENAI_API_KEY not set"))))
