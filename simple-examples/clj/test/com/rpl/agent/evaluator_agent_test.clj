(ns com.rpl.agent.evaluator-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.evaluator-agent :refer [EvaluatorAgentModule]]))

(deftest evaluator-agent-test
  (testing "EvaluatorAgent example creates and tests evaluators correctly"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc EvaluatorAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name EvaluatorAgentModule))
            agent (aor/agent-client manager "EvaluatorAgent")]
        
        (testing "creates multiple evaluator types and tests them"
          (let [result (aor/agent-invoke agent {})]
            
            ;; Verify basic result structure
            (is (= "evaluator-management-complete" (:action result)))
            (is (= 5 (:evaluators-created result)))
            (is (number? (:processed-at result)))
            
            ;; Verify length evaluator result
            (let [length-result (:length-test-result result)]
              (is (contains? length-result "within-limit?"))
              (is (contains? length-result "actual-length"))
              (is (contains? length-result "max-length"))
              (is (= 50 (get length-result "max-length")))
              (is (= true (get length-result "within-limit?"))))
            
            ;; Verify conciseness evaluator result
            (let [concise-result (:conciseness-test-result result)]
              (is (contains? concise-result "concise?"))
              ;; "This is a longer response for testing" > 30 chars
              (is (= false (get concise-result "concise?"))))
            
            ;; Verify comparison evaluator result
            (let [comparison-result (:comparison-result result)]
              (is (contains? comparison-result "best-index"))
              (is (contains? comparison-result "best-output"))
              (is (contains? comparison-result "best-score"))
              ;; "good sunny day" should win due to length + "good" bonus
              (is (= 1 (get comparison-result "best-index")))
              (is (= "good sunny day" (get comparison-result "best-output"))))
            
            ;; Verify F1 score result
            (let [f1-result (:f1-score-result result)]
              (is (contains? f1-result "score"))
              (is (contains? f1-result "precision"))
              (is (contains? f1-result "recall"))
              ;; 3 correct out of 4 total, with 3 true positives
              (is (> (get f1-result "score") 0.5)))
            
            ;; Verify accuracy result
            (let [accuracy-result (:accuracy-result result)]
              (is (contains? accuracy-result "total-examples"))
              (is (contains? accuracy-result "correct-predictions"))
              (is (contains? accuracy-result "accuracy"))
              (is (= 4 (get accuracy-result "total-examples")))
              (is (= 3 (get accuracy-result "correct-predictions")))
              (is (= 0.75 (get accuracy-result "accuracy"))))
            
            ;; Verify search functionality
            (let [search-results (:search-results result)]
              (is (set? search-results))
              (is (contains? search-results "length-50"))))))))))