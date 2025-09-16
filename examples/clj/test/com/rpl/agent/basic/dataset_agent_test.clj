(ns com.rpl.agent.basic.dataset-agent-test
  (:require
   [clojure.test :refer [deftest testing is]]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [com.rpl.agent.basic.dataset-agent :refer [DatasetExampleModule math-input-schema math-output-schema]]
   [clojure.string :as str]))

(deftest dataset-example-test
  (System/gc)
  (testing "Dataset management at client level with calculator agent"
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc DatasetExampleModule {:tasks 1 :threads 1})

      (let [manager    (aor/agent-manager
                        ipc
                        (rama/get-module-name DatasetExampleModule))
            calc-agent (aor/agent-client manager "SimpleCalculatorAgent")]

        (testing "calculator agent performs basic operations correctly"
          ;; Test calculator functionality
          (let [add-result      (aor/agent-invoke
                                 calc-agent
                                 {:operation "add" :a 5 :b 3})
                mult-result     (aor/agent-invoke
                                 calc-agent
                                 {:operation "multiply"
                                  :a         4
                                  :b         7})
                div-zero-result (aor/agent-invoke
                                 calc-agent
                                 {:operation "divide"
                                  :a         10
                                  :b         0})]

            (is (= {:result 8} add-result))
            (is (= {:result 28} mult-result))
            (is (= {:result "Error: Division by zero"} div-zero-result))))

        (testing "can create datasets and manage examples at client level"
          ;; Create a math dataset for testing the calculator using shared
          ;; schemas
          (let [math-dataset-id
                (aor/create-dataset!
                 manager
                 "Test Math Operations Dataset"
                 {:description
                  "Dataset for testing calculator agent"
                  :input-json-schema  math-input-schema
                  :output-json-schema math-output-schema})]

            (is (some? math-dataset-id) "Dataset ID should be returned")
            (is (or (string? math-dataset-id) (uuid? math-dataset-id))
                "Dataset ID should be a string or UUID")

            ;; Add test examples
            (aor/add-dataset-example!
             manager
             math-dataset-id
             {:operation "add" :a 5 :b 3}
             {:reference-output {:result 8}
              :tags   #{"basic" "addition"}
              :source "test"})

            (aor/add-dataset-example!
             manager
             math-dataset-id
             {:operation "multiply" :a 4 :b 7}
             {:reference-output {:result 28}
              :tags #{"basic" "multiplication"}})

            (aor/add-dataset-example!
             manager
             math-dataset-id
             {:operation "divide" :a 10 :b 0}
             {:reference-output
              {:result "Error: Division by zero"}
              :tags #{"edge-case" "error"}})

            ;; Verify datasets are searchable
            (let [math-results (aor/search-datasets manager "Math" 10)]
              (is (>= (count math-results) 1))
              (is (some
                   #(str/includes? % "Math Operations")
                   (vals math-results))))

            ;; Create a snapshot
            (aor/snapshot-dataset! manager math-dataset-id nil "v1.0")

            ;; Test that we can validate agent behavior against dataset examples
            (testing "agent results match dataset expectations"
              (let [test-cases [{:input    {:operation "add" :a 5 :b 3}
                                 :expected {:result 8}}
                                {:input    {:operation "multiply" :a 4 :b 7}
                                 :expected {:result 28}}
                                {:input    {:operation "divide" :a 10 :b 0}
                                 :expected {:result
                                            "Error: Division by zero"}}]]

                (doseq [{:keys [input expected]} test-cases]
                  (let [actual (aor/agent-invoke calc-agent input)]
                    (is (= expected actual)
                        (str "Agent result " actual
                             " should match expected " expected
                             " for input " input))))))

            ;; Add more examples to demonstrate ongoing dataset management
            (aor/add-dataset-example!
             manager
             math-dataset-id
             {:operation "subtract" :a 10 :b 3}
             {:reference-output {:result 7}
              :tags #{"basic" "subtraction"}})

            ;; Verify new example was added
            (let [updated-results (aor/search-datasets manager "Test Math" 10)]
              (is (>= (count updated-results) 1)))))

        (testing "demonstrates proper separation of concerns"
          ;; This test verifies the key architectural point:
          ;; - Agents focus on computation (calculator logic)
          ;; - Dataset management happens at client level
          ;; - No agent tries to get a manager from within a node

          (is (some? manager) "Manager should be created at client level")
          (is (some? calc-agent)
              "Agent client should be available for computation")

          ;; Verify agent only does computation, not dataset management
          (let [computation-result (aor/agent-invoke
                                    calc-agent
                                    {:operation "add"
                                     :a         1
                                     :b         1})]
            (is (= {:result 2} computation-result)
                "Agent should focus purely on computation")))))))
