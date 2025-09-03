(ns com.rpl.agent.basic.dataset-agent
  "Demonstrates dataset creation and management for agent testing and evaluation.

  Features demonstrated:
  - create-dataset!: Create datasets with input/output schemas
  - add-dataset-example!: Add examples to datasets
  - search-datasets: Find datasets by name/description
  - Dataset snapshots and example management
  - JSON schema validation for inputs and outputs"
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
  "Demonstrates proper client-side dataset management"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DatasetExampleModule {:tasks 1 :threads 1})

    (let [manager    (aor/agent-manager
                      ipc
                      (rama/get-module-name DatasetExampleModule))
          calc-agent (aor/agent-client manager "SimpleCalculatorAgent")]

      (println
       "Dataset Agent Example: Testing calculator agent with datasets\n")

      ;; Create dataset with JSON schema for our calculator agent
      (let [math-dataset-id
            (aor/create-dataset!
             manager
             "Math Operations Dataset"
             {:description
              "Dataset for testing calculator agent"
              :input-json-schema  math-input-schema
              :output-json-schema math-output-schema})]

        (println "Created dataset:" math-dataset-id)

        ;; Add examples with different tags and sources
        (aor/add-dataset-example!
         manager
         math-dataset-id
         {:operation "add" :a 5 :b 3}
         {:reference-output {:result 8}
          :tags   #{"basic" "addition"}
          :source "manual"})

        (aor/add-dataset-example!
         manager
         math-dataset-id
         {:operation "multiply" :a 4 :b 7}
         {:reference-output {:result 28}
          :tags   #{"basic" "multiplication"}
          :source "manual"})

        (aor/add-dataset-example!
         manager
         math-dataset-id
         {:operation "divide" :a 10 :b 0}
         {:reference-output {:result
                             "Error: Division by zero"}
          :tags   #{"edge-case" "error"}
          :source "manual"})

        (aor/add-dataset-example!
         manager
         math-dataset-id
         {:operation "subtract" :a 10 :b 3}
         {:reference-output {:result 7}
          :tags   #{"basic" "subtraction"}
          :source "generated"})

        ;; Create snapshot to preserve current state
        (aor/snapshot-dataset! manager math-dataset-id nil "v1.0")
        (println "Created snapshot 'v1.0'")

        ;; Test agent against dataset examples
        (println "\nTesting calculator agent with dataset examples:")
        (doseq [[input expected] [[{:operation "add" :a 5 :b 3} {:result 8}]
                                  [{:operation "divide" :a 10 :b 0}
                                   {:result "Error: Division by zero"}]]]
          (let [result (aor/agent-invoke calc-agent input)]
            (println "  Input:" input "→ Agent:" result "Expected:" expected)))

        ;; Demonstrate search functionality
        (println "\nSearching datasets:")
        (let [search-results
              {"Math"       (aor/search-datasets manager "Math" 10)
               "Operations" (aor/search-datasets manager "Operations" 10)}]
          (doseq [[term results] search-results]
            (println (str "  " term " datasets found: " (count results)))))

        (println
         "\nDemonstrated: JSON schemas, examples with tags/sources, snapshots, search")))))

(comment
  (-main))
