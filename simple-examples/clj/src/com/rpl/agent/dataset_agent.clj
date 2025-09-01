(ns com.rpl.agent.dataset-agent
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
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.data.message
    UserMessage]))

;;; Simple agent for dataset examples
(aor/defagentmodule DatasetExampleModule
  [topology]

  (->
    topology
    (aor/new-agent "SimpleCalculatorAgent")

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

;;; Agent module demonstrating dataset functionality
(aor/defagentmodule DatasetAgentModule
  [topology]

  (->
    topology
    (aor/new-agent "DatasetAgent")

    ;; Node that creates and manages datasets
    (aor/node
     "manage-datasets"
     nil
     (fn [agent-node _]
       (let [manager (aor/agent-manager agent-node)]

         (println "Creating datasets with different schemas...")

         ;; Create a math operations dataset
         (let [math-input-schema
               {"type" "object"
                "properties" {"operation" {"type" "string"
                                           "enum" ["add" "subtract" "multiply" "divide"]}
                              "a" {"type" "number"}
                              "b" {"type" "number"}}
                "required" ["operation" "a" "b"]}

               math-output-schema
               {"type" "object"
                "properties" {"result" {"type" ["number" "string"]}}
                "required" ["result"]}

               math-dataset-id
               (aor/create-dataset! manager
                                    "Math Operations Dataset"
                                    {:description "Dataset for testing basic math operations"
                                     :input-json-schema (pr-str math-input-schema)
                                     :output-json-schema (pr-str math-output-schema)})]

           (println "Created math dataset:" math-dataset-id)

           ;; Add examples to the math dataset
           (aor/add-dataset-example! manager
                                     math-dataset-id
                                     {:operation "add" :a 5 :b 3}
                                     {:reference-output {:result 8}
                                      :tags #{"basic" "addition"}
                                      :source "manual"})

           (aor/add-dataset-example! manager
                                     math-dataset-id
                                     {:operation "multiply" :a 4 :b 7}
                                     {:reference-output {:result 28}
                                      :tags #{"basic" "multiplication"}})

           (aor/add-dataset-example! manager
                                     math-dataset-id
                                     {:operation "divide" :a 10 :b 0}
                                     {:reference-output {:result "Error: Division by zero"}
                                      :tags #{"edge-case" "error"}})

           ;; Create a text processing dataset
           (let [text-input-schema
                 {"type" "string"}

                 text-output-schema  
                 {"type" "object"
                  "properties" {"length" {"type" "number"}
                                "uppercase" {"type" "string"}
                                "words" {"type" "array"
                                         "items" {"type" "string"}}}
                  "required" ["length" "uppercase" "words"]}

                 text-dataset-id
                 (aor/create-dataset! manager
                                      "Text Processing Dataset"
                                      {:description "Dataset for text analysis tasks"
                                       :input-json-schema (pr-str text-input-schema)
                                       :output-json-schema (pr-str text-output-schema)})]

             (println "Created text dataset:" text-dataset-id)

             ;; Add text processing examples
             (aor/add-dataset-example! manager
                                       text-dataset-id
                                       "hello world"
                                       {:reference-output {:length 11
                                                           :uppercase "HELLO WORLD"
                                                           :words ["hello" "world"]}
                                        :tags #{"simple" "two-words"}})

             (aor/add-dataset-example! manager
                                       text-dataset-id
                                       "test"
                                       {:reference-output {:length 4
                                                           :uppercase "TEST"
                                                           :words ["test"]}
                                        :tags #{"simple" "single-word"}})

             ;; Create snapshot of text dataset
             (aor/snapshot-dataset! manager text-dataset-id nil "v1.0")
             (println "Created snapshot 'v1.0' for text dataset")

             ;; Search for datasets
             (let [search-results (aor/search-datasets manager "math" 10)]
               (println "Search results for 'math':" (keys search-results)))

             (aor/result! agent-node
                          {:action "dataset-management-complete"
                           :datasets-created 2
                           :math-dataset-id math-dataset-id
                           :text-dataset-id text-dataset-id
                           :examples-added 5
                           :snapshots-created 1
                           :processed-at (System/currentTimeMillis)})))))))

(defn -main
  "Run the dataset agent example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DatasetAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                     (rama/get-module-name DatasetAgentModule))
          agent   (aor/agent-client manager "DatasetAgent")]

      (println "Dataset Agent Example:")
      (println "Creating and managing datasets for agent evaluation")

      (let [result (aor/agent-invoke agent {})]

        (println "\nResults:")
        (println "  Action:" (:action result))
        (println "  Datasets created:" (:datasets-created result))
        (println "  Examples added:" (:examples-added result))
        (println "  Snapshots created:" (:snapshots-created result))
        (println "  Math dataset ID:" (:math-dataset-id result))
        (println "  Text dataset ID:" (:text-dataset-id result))

        ;; Demonstrate searching datasets
        (println "\nSearching datasets:")
        (let [math-results (aor/search-datasets manager "Math" 10)
              text-results (aor/search-datasets manager "Text" 10)
              all-results (aor/search-datasets manager "Dataset" 10)]
          (println "  Math datasets found:" (count math-results))
          (println "  Text datasets found:" (count text-results))  
          (println "  All datasets found:" (count all-results))))

      (println "\nNotice how:")
      (println "- Datasets can have JSON schemas for validation")
      (println "- Examples include input, output, tags, and source")
      (println "- Snapshots preserve dataset state at specific points")
      (println "- Search helps find relevant datasets for testing"))))