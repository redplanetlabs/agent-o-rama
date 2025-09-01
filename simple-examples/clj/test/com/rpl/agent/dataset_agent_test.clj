(ns com.rpl.agent.dataset-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.dataset-agent :refer [DatasetAgentModule]]))

(deftest dataset-agent-test
  (testing "DatasetAgent example creates and manages datasets correctly"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc DatasetAgentModule {:tasks 1 :threads 1})
      
      (let [manager (aor/agent-manager ipc (rama/get-module-name DatasetAgentModule))
            agent (aor/agent-client manager "DatasetAgent")]
        
        (testing "creates datasets with examples and snapshots"
          (let [result (aor/agent-invoke agent {})]
            
            ;; Verify basic result structure
            (is (= "dataset-management-complete" (:action result)))
            (is (= 2 (:datasets-created result)))
            (is (= 5 (:examples-added result)))
            (is (= 1 (:snapshots-created result)))
            (is (string? (:math-dataset-id result)))
            (is (string? (:text-dataset-id result)))
            (is (number? (:processed-at result)))
            
            ;; Verify datasets are searchable
            (let [math-results (aor/search-datasets manager "Math" 10)
                  text-results (aor/search-datasets manager "Text" 10)
                  operation-results (aor/search-datasets manager "operations" 10)]
              
              ;; Should find the math dataset
              (is (>= (count math-results) 1))
              (is (some #(.contains % "Math Operations") (vals math-results)))
              
              ;; Should find the text dataset
              (is (>= (count text-results) 1))
              (is (some #(.contains % "Text Processing") (vals text-results)))
              
              ;; Should find math dataset when searching for "operations"
              (is (>= (count operation-results) 1)))
            
            ;; Test that we can create additional examples on the datasets
            (let [math-dataset-id (:math-dataset-id result)
                  text-dataset-id (:text-dataset-id result)]
              
              ;; Add another example to math dataset
              (aor/add-dataset-example! manager
                                        math-dataset-id
                                        {:operation "subtract" :a 10 :b 3}
                                        {:reference-output {:result 7}
                                         :tags #{"basic" "subtraction"}})
              
              ;; Add another example to text dataset  
              (aor/add-dataset-example! manager
                                        text-dataset-id
                                        "quick brown fox"
                                        {:reference-output {:length 15
                                                            :uppercase "QUICK BROWN FOX"
                                                            :words ["quick" "brown" "fox"]}
                                         :tags #{"simple" "three-words"}})))))))))