(ns com.rpl.agent.multi-agg-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.multi-agg-agent :refer [MultiAggAgentModule]]))

(deftest multi-agg-agent-test
  (testing "MultiAggAgent example produces expected tagged aggregation behavior"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc MultiAggAgentModule {:tasks 2 :threads 2})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name MultiAggAgentModule))
            agent (aor/agent-client manager "MultiAggAgent")]
        
        (testing "processes mixed data types with custom aggregation"
          (let [result (aor/agent-invoke agent
                                         {:numbers [5 10 15]
                                          :strings ["test" "hello" "world"]
                                          :keywords [:foo :bar :ns/qualified]})]
            ;; Verify final result structure
            (is (= "multi-analysis-complete" (:action result)))
            (is (number? (:processed-at result)))
            
            ;; Verify summary calculations
            (let [summary (:summary result)]
              (is (= 3 (:numbers-analyzed summary)))
              (is (= 3 (:strings-analyzed summary)))
              (is (= 3 (:keywords-analyzed summary)))
              
              ;; Sum of squares: 5² + 10² + 15² = 25 + 100 + 225 = 350
              (is (= 350 (:total-square-sum summary)))
              
              ;; Average string length: (4 + 5 + 5) / 3 = 14/3 ≈ 4.67
              (is (< (Math/abs (- 4.666666666666667 (:avg-string-length summary))) 0.001))
              
              ;; Only :ns/qualified has a namespace
              (is (= 1 (:namespaced-keywords-count summary))))
            
            ;; Verify detailed results structure
            (let [details (:detailed-results result)]
              ;; Numbers analysis
              (is (= 3 (count (:numbers details))))
              (let [first-number (first (:numbers details))]
                (is (= 5 (:value first-number)))
                (is (= 25 (:square first-number)))
                (is (= true (:even? first-number))))
              
              ;; Strings analysis
              (is (= 3 (count (:strings details))))
              (let [first-string (first (:strings details))]
                (is (= "test" (:value first-string)))
                (is (= 4 (:length first-string)))
                (is (= "TEST" (:uppercase first-string))))
              
              ;; Keywords analysis
              (is (= 3 (count (:keywords details))))
              (let [first-keyword (first (:keywords details))]
                (is (= :foo (:value first-keyword)))
                (is (= "foo" (:name first-keyword)))
                (is (= nil (:namespace first-keyword)))))))
        
        (testing "handles empty collections correctly"
          (let [result (aor/agent-invoke agent
                                         {:numbers []
                                          :strings []
                                          :keywords []})]
            (let [summary (:summary result)]
              (is (= 0 (:numbers-analyzed summary)))
              (is (= 0 (:strings-analyzed summary)))
              (is (= 0 (:keywords-analyzed summary)))
              (is (= 0 (:total-square-sum summary)))
              (is (= 0 (:avg-string-length summary)))
              (is (= 0 (:namespaced-keywords-count summary)))))))))))