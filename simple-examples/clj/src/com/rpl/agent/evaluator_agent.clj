(ns com.rpl.agent.evaluator-agent
  "Demonstrates evaluator creation and execution for agent performance assessment.

  Features demonstrated:
  - create-evaluator!: Create evaluators with different types (regular, comparative, summary)
  - try-evaluator: Test evaluator performance on examples
  - Built-in evaluators: conciseness, F1-score, LLM judge
  - Custom evaluator builders and parameter configuration"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;;; Agent module demonstrating evaluator functionality
#_(aor/defagentmodule EvaluatorAgentModule
    [topology]

    ;; Declare custom evaluator builders
    (aor/declare-evaluator-builder
     topology
     "length-checker"
     "Checks if text length meets criteria"
     (fn [params]
       (let [max-length (Integer/parseInt (get params "maxLength" "100"))]
         (fn [fetcher input reference-output output]
           (let [output-length (count (str output))]
             {"within-limit?" (<= output-length max-length)
              "actual-length" output-length
              "max-length"    max-length}))))
     {:params {"maxLength" {:description "Maximum allowed length"}}})

    (aor/declare-comparative-evaluator-builder
     topology
     "quality-ranker"
     "Ranks outputs by simple quality metric"
     (fn [params]
       (fn [fetcher input reference-output outputs]
         ;; Simple quality metric based on length and content
         (let [scored-outputs (map-indexed
                               (fn [idx output]
                                 {:index  idx
                                  :output output
                                  :score  (+ (count (str output))
                                             (if (.contains (str output) "good")
                                               10
                                               0)
                                             (if (.contains (str output) "bad")
                                               -10
                                               0))})
                               outputs)
               best-output    (apply max-key :score scored-outputs)]
           {"best-index"  (:index best-output)
            "best-output" (:output best-output)
            "best-score"  (:score best-output)}))))

    (aor/declare-summary-evaluator-builder
     topology
     "accuracy-summary"
     "Calculates accuracy across multiple examples"
     (fn [params]
       (fn [fetcher example-runs]
         (let [total    (count example-runs)
               correct  (count (filter #(= (:reference-output %) (:output %))
                                example-runs))
               accuracy (if (pos? total) (/ (double correct) total) 0.0)]
           {"total-examples" total
            "correct-predictions" correct
            "accuracy"       accuracy}))))

    (->
      topology
      (aor/new-agent "EvaluatorAgent")

      ;; Node that creates and tests different evaluators
      (aor/node
       "manage-evaluators"
       nil
       (fn [agent-node _]
         (let [manager (aor/agent-manager agent-node)]

           (println "Creating different types of evaluators...")

           ;; Create a length-based evaluator
           (aor/create-evaluator! manager
                                  "length-50"
                                  "length-checker"
                                  {"maxLength" "50"}
                                  "Checks if responses are under 50 characters")

           ;; Create a conciseness evaluator (built-in)
           (aor/create-evaluator! manager
                                  "concise-30"
                                  "aor/conciseness"
                                  {"threshold" "30"}
                                  "Built-in conciseness evaluator")

           ;; Create F1-score evaluator for classification tasks
           (aor/create-evaluator! manager
                                  "f1-positive"
                                  "aor/f1-score"
                                  {"positiveValue" "positive"}
                                  "F1 score for sentiment classification")

           ;; Create comparative evaluator
           (aor/create-evaluator! manager
                                  "quality-compare"
                                  "quality-ranker"
                                  {}
                                  "Ranks outputs by quality metric")

           ;; Create summary evaluator
           (aor/create-evaluator! manager
                                  "accuracy-calc"
                                  "accuracy-summary"
                                  {}
                                  "Calculates accuracy across examples")

           (println "Testing evaluators with sample data...")

           ;; Test regular evaluators
           (let [length-result  (aor/try-evaluator manager
                                                   "length-50"
                                                   "Test input"
                                                   "Expected"
                                                   "Short response")
                 concise-result (aor/try-evaluator
                                 manager
                                 "concise-30"
                                 "Test input"
                                 "Expected"
                                 "This is a longer response for testing")]

             (println "Length evaluator result:" length-result)
             (println "Conciseness evaluator result:" concise-result)

             ;; Test comparative evaluator
             (let [comparison-result (aor/try-comparative-evaluator
                                      manager
                                      "quality-compare"
                                      "What is the weather?"
                                      "sunny"
                                      ["bad weather" "good sunny day" "okay"])]
               (println "Quality comparison result:" comparison-result)

               ;; Test summary evaluator with multiple examples
               (let [examples        [(aor/mk-example-run "input1"
                                                          "positive"
                                                          "positive")
                                      (aor/mk-example-run "input2"
                                                          "negative"
                                                          "negative")
                                      (aor/mk-example-run "input3"
                                                          "positive"
                                                          "negative")
                                      (aor/mk-example-run "input4"
                                                          "positive"
                                                          "positive")]

                     f1-result       (aor/try-summary-evaluator manager
                                                                "f1-positive"
                                                                examples)
                     accuracy-result (aor/try-summary-evaluator manager
                                                                "accuracy-calc"
                                                                examples)]

                 (println "F1 score result:" f1-result)
                 (println "Accuracy result:" accuracy-result)

                 ;; Search for evaluators
                 (let [search-results (aor/search-evaluators manager "length")]
                   (println "Evaluators matching 'length':" search-results)

                   (aor/result!
                    agent-node
                    {:action "evaluator-management-complete"
                     :evaluators-created 5
                     :length-test-result length-result
                     :conciseness-test-result concise-result
                     :comparison-result comparison-result
                     :f1-score-result f1-result
                     :accuracy-result accuracy-result
                     :search-results search-results
                     :processed-at (System/currentTimeMillis)}))))))))))

#_(defn -main
    "Run the evaluator agent example"
    [& _args]
    (with-open [ipc (rtest/create-ipc)]
      (rtest/launch-module! ipc EvaluatorAgentModule {:tasks 1 :threads 1})

      (let [manager (aor/agent-manager ipc
                                       (rama/get-module-name
                                        EvaluatorAgentModule))
            agent   (aor/agent-client manager "EvaluatorAgent")]

        (println "Evaluator Agent Example:")
        (println
         "Creating and testing different types of evaluators for agent assessment")

        (let [result (aor/agent-invoke agent {})]

          (println "\nResults:")
          (println "  Action:" (:action result))
          (println "  Evaluators created:" (:evaluators-created result))

          (println "\n  Individual Evaluator Results:")
          (println "    Length test:" (:length-test-result result))
          (println "    Conciseness test:" (:conciseness-test-result result))
          (println "    Comparison test:" (:comparison-result result))
          (println "    F1 score test:" (:f1-score-result result))
          (println "    Accuracy test:" (:accuracy-result result))

          (println "\n  Search results:" (:search-results result)))

        (println "\nNotice how:")
        (println "- Different evaluator types serve different assessment needs")
        (println "- Custom evaluators can implement domain-specific logic")
        (println "- Built-in evaluators provide common metrics like F1-score")
        (println "- Comparative evaluators help rank multiple outputs")
        (println "- Summary evaluators aggregate results across examples"))))
