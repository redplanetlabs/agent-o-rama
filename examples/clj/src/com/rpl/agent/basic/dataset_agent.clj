(ns com.rpl.agent.basic.dataset-agent
  "Demonstrates dataset creation and lifecycle management for agent testing and evaluation.

  Features demonstrated:
  - create-dataset!: Create datasets with input/output schemas
  - set-dataset-name!: Update dataset names
  - set-dataset-description!: Update dataset descriptions
  - search-datasets: Find datasets by name/description
  - snapshot-dataset!: Create dataset snapshots
  - remove-dataset-snapshot!: Remove snapshots
  - destroy-dataset!: Delete entire datasets"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j]))

;;; Simple agent for dataset examples
(aor/defagentmodule DatasetExampleModule
  [topology]

  (->
    (aor/new-agent topology "SimpleCalculatorAgent")

    (aor/node
     "calculate"
     nil
     (fn [agent-node {:keys [operation a b]}]
       (let [result (case operation
                      "add" (+ a b)
                      "subtract" (- a b)
                      "multiply" (* a b)
                      "divide" (if (zero? b)
                                 "Error: Division by zero"
                                 (/ a b))
                      "Unknown operation")]
         (aor/result! agent-node {:result result}))))))

;;; Dataset functionality demonstration

(def math-input-schema
  (j/write-value-as-string
   {"type"       "object"
    "properties" {"operation" {"type" "string"
                               "enum" ["add" "subtract" "multiply" "divide"]}
                  "a"         {"type" "number"}
                  "b"         {"type" "number"}}
    "required"   ["operation" "a" "b"]}))

(def math-output-schema
  (j/write-value-as-string
   {"type"       "object"
    "properties" {"result" {"type" ["number" "string"]}}
    "required"   ["result"]}))

(defn -main
  "Demonstrates dataset lifecycle management"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DatasetExampleModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name DatasetExampleModule))]

      ;; Create initial dataset
      (let [dataset-id
            (aor/create-dataset!
             manager
             "Initial Calculator Dataset"
             {:description        "Basic calculator operations dataset"
              :input-json-schema  math-input-schema
              :output-json-schema math-output-schema})]

        (aor/set-dataset-name! manager dataset-id "Advanced Math Dataset")

        (aor/set-dataset-description!
         manager
         dataset-id
         "Comprehensive mathematical operations with edge case handling")

        (aor/snapshot-dataset! manager dataset-id nil "baseline")
        (aor/snapshot-dataset! manager dataset-id nil "v1.0")
        (aor/snapshot-dataset! manager dataset-id nil "experimental")

        (aor/remove-dataset-snapshot! manager dataset-id "experimental")

        (let [math-results (aor/search-datasets manager "Math" 5)
              calc-results (aor/search-datasets manager "Calculator" 5)]
          (println (str "'Math' search: " (count math-results) " results"))
          (println (str "'Calculator' search: " (count calc-results) " results")))

        ;; Create another dataset to demonstrate multiple datasets
        (let [geometry-dataset-id
              (aor/create-dataset!
               manager
               "Geometry Dataset"
               {:description        "Geometric calculations and formulas"
                :input-json-schema  math-input-schema
                :output-json-schema math-output-schema})]

          ;; Search again to show multiple results
          (let [all-results (aor/search-datasets manager "" 10)]
            (println (str "Total datasets: " (count all-results))))

          ;; Demonstrate dataset destruction
          (aor/destroy-dataset! manager geometry-dataset-id)

          ;; Final search to confirm deletion
          (let [final-results (aor/search-datasets manager "" 10)]
            (println (str "Datasets remaining: " (count final-results)))))))))

(comment
  (-main))
