(ns com.rpl.agent.basic.provided-evaluator-builders-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.provided-evaluator-builders-agent :refer [ProvidedEvaluatorBuildersModule create-demo-evaluators]]))

(deftest provided-evaluator-builders-agent-test
  ;; Test validates that the example runs properly and evaluators are created
  ;; Does not test actual evaluation execution to avoid API dependencies
  (testing "ProvidedEvaluatorBuildersModule example"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module!
       ipc
       ProvidedEvaluatorBuildersModule
       {:tasks 1 :threads 1})
      (let [manager (aor/agent-manager
                     ipc
                     (rama/get-module-name ProvidedEvaluatorBuildersModule))
            agent   (aor/agent-client manager "TextGenerator")]

        (testing "generates different text outputs based on input type"
          (is (= "Yes" (aor/agent-invoke agent "short")))
          (is (= "This is a medium-length response" (aor/agent-invoke agent "medium")))
          (is (= "positive" (aor/agent-invoke agent "positive")))
          (is (= "negative" (aor/agent-invoke agent "negative"))))

        (let [evaluator-names (create-demo-evaluators manager)]
          (testing "successfully creates evaluators with provided builders"
            ;; Use the shared function to create demo evaluators
            (is (map? evaluator-names) "Should return a map of evaluator names")
            (is (contains? evaluator-names :llm-judge) "Should create LLM judge evaluator")
            (is (contains? evaluator-names :conciseness) "Should create conciseness evaluator")
            (is (contains? evaluator-names :f1-score) "Should create F1-score evaluator")

            ;; Verify evaluators were created by searching for them
            (let [search-results (aor/search-evaluators manager "")]
              (is (>= (count search-results) 3)
                  "Should find at least 3 evaluators")))

          (testing "conciseness evaluator works correctly"
            ;; Create demo evaluators and test the conciseness one
            (let [concise-name (:conciseness evaluator-names)
                  short-result (aor/try-evaluator manager concise-name "test" "ref" "Yes")
                  long-result  (aor/try-evaluator
                                manager
                                concise-name
                                "test"
                                "ref"
                                "This is a much longer response that exceeds threshold")]
              (is (true? (get short-result "concise?")) "Short response should be concise")
              (is (false? (get long-result "concise?")) "Long response should not be concise")))

          (testing "F1-score evaluator works correctly"
            ;; Create demo evaluators and test the F1-score one
            (let [f1-name   (:f1-score evaluator-names)
                  examples  [(aor/mk-example-run "input1" "positive" "positive")  ; TP
                             (aor/mk-example-run "input2" "negative" "negative")  ; TN
                             (aor/mk-example-run "input3" "positive" "negative")  ; FN
                             (aor/mk-example-run "input4" "negative" "positive")] ; FP
                  f1-result (aor/try-summary-evaluator manager f1-name examples)]
              (is (number? (get f1-result "score")) "F1 score should be a number")
              (is (number? (get f1-result "precision")) "Precision should be a number")
              (is (number? (get f1-result "recall")) "Recall should be a number")
              (is (<= 0.0 (get f1-result "score") 1.0) "F1 score should be between 0 and 1"))))))))
