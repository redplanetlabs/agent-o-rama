(ns com.rpl.agent.basic.dataset-example-agent
  "Demonstrates dataset example management for agent testing and evaluation.

  Features demonstrated:
  - add-dataset-example!: Add examples to datasets
  - set-dataset-example-input!: Update example inputs
  - set-dataset-example-reference-output!: Update reference outputs
  - add-dataset-example-tag!: Add tags to examples
  - remove-dataset-example-tag!: Remove tags from examples
  - remove-dataset-example!: Delete examples
  - Snapshot-specific example operations"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [jsonista.core :as j]))

;;; Simple agent for dataset example testing
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

;;; Dataset example management demonstration

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
  "Demonstrates dataset example management operations"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc DatasetExampleModule {:tasks 1 :threads 1})

    (let [manager    (aor/agent-manager
                      ipc
                      (rama/get-module-name DatasetExampleModule))
          calc-agent (aor/agent-client manager "SimpleCalculatorAgent")]

      (let [dataset-id
            (aor/create-dataset!
             manager
             "Math Examples Dataset"
             {:description        "Dataset for demonstrating example management"
              :input-json-schema  math-input-schema
              :output-json-schema math-output-schema})]

        ;; Add initial examples with different tags and sources
        (let [example-1-id
              (aor/add-dataset-example!
               manager
               dataset-id
               {:operation "add" :a 5 :b 3}
               {:reference-output {:result 8}
                :tags #{"basic" "addition"}})]

          (let [example-2-id
                (aor/add-dataset-example!
                 manager
                 dataset-id
                 {:operation "multiply" :a 4 :b 7}
                 {:reference-output {:result 28}
                  :tags #{"basic" "multiplication"}})]

            ;; Create snapshot to work with
            (aor/snapshot-dataset! manager dataset-id nil "v1.0")

            ;; Add example with snapshot specified
            (let [example-3-id
                  (aor/add-dataset-example!
                   manager
                   dataset-id
                   {:operation "subtract" :a 10 :b 3}
                   {:reference-output {:result 7}
                    :tags     #{"basic" "subtraction"}
                    :snapshot "v1.0"})]

              (aor/add-dataset-example-tag!
               manager
               dataset-id
               example-1-id
               "verified")

              (aor/add-dataset-example-tag!
               manager
               dataset-id
               example-2-id
               "performance-test"
               {:snapshot "v1.0"})

              ;; Update example input
              (aor/set-dataset-example-input!
               manager
               dataset-id
               example-1-id
               {:operation "add" :a 10 :b 5})

              ;; Update reference output accordingly
              (aor/set-dataset-example-reference-output!
               manager
               dataset-id
               example-1-id
               {:result 15})

              ;; Remove a tag
              (aor/remove-dataset-example-tag!
               manager
               dataset-id
               example-1-id
               "basic")

              ;; Add example with error case
              (let [error-example-id
                    (aor/add-dataset-example!
                     manager
                     dataset-id
                     {:operation "divide" :a 10 :b 0}
                     {:reference-output {:result "Error: Division by zero"}
                      :tags #{"edge-case" "error"}})]


                ;; Remove an example
                (aor/remove-dataset-example!
                 manager
                 dataset-id
                 error-example-id)

                ;; Snapshot-specific operations
                (aor/set-dataset-example-input!
                 manager
                 dataset-id
                 example-3-id
                 {:operation "subtract" :a 20 :b 8}
                 {:snapshot "v1.0"})))))))))
