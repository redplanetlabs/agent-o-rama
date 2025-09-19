(ns com.rpl.agent.basic.evaluator-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.evaluator-agent :refer [EvaluatorAgentModule]]))

(deftest evaluator-agent-test
  (System/gc)
  (testing "Evaluator example creates evaluators and tests agent outputs"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc EvaluatorAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name EvaluatorAgentModule))
            agent   (aor/agent-client manager "TextProcessor")]

        (testing "agent produces expected outputs"
          (let [short-output  (aor/agent-invoke agent "Hi")
                medium-output (aor/agent-invoke agent "This is a test input")
                long-output   (aor/agent-invoke
                               agent
                               "This is a much longer test input")]

            (is (= "short" short-output))
            (is (= "good medium length" medium-output))
            (is (= "too long" long-output))))

        (testing "evaluators can be created and used"
          ;; Create a simple evaluator
          (aor/create-evaluator! manager
                                 "test-length"
                                 "length-checker"
                                 {"maxLength" "20"}
                                 "Test length evaluator")

          ;; Test the evaluator
          (let [result (aor/try-evaluator manager
                                          "test-length"
                                          "input"
                                          "reference"
                                          "short")]
            (is (contains? result "within-limit?"))
            (is (= true (get result "within-limit?")))
            (is (= 5 (get result "actual-length")))
            (is (= 20 (get result "max-length")))))))))
