(ns com.rpl.agent.basic.evaluator-agent
  "Demonstrates evaluator usage for agent performance assessment.

  Features demonstrated:
  - Declaring custom evaluator builders in the module
  - Creating evaluators at the manager level (outside agents)
  - Using evaluators to test agent outputs
  - Built-in evaluators: conciseness, F1-score, LLM judge
  - Custom evaluator builders with parameter configuration"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]
   [clojure.string :as str]))

;;; Agent module with evaluator builders
(aor/defagentmodule EvaluatorAgentModule
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
                                           (if (str/includes? (str output) "good")
                                             10
                                             0)
                                           (if (str/includes? (str output) "bad")
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

  ;; Simple agent that processes text
  (->
    (aor/new-agent topology "TextProcessor")

    (aor/node
     "process-text"
     nil
     (fn [agent-node input]
       (let [response (cond
                        (< (count input) 10)
                        "short"

                        (< (count input) 30)
                        "good medium length"

                        :else
                        "too long")]
         (println "Processing input:" input)
         (println "Generated response:" response)
         (aor/result! agent-node response))))))

(defn -main
  "Run the evaluator example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc EvaluatorAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name EvaluatorAgentModule))
          agent   (aor/agent-client manager "TextProcessor")]

      (println "Evaluator Example:")
      (println "====================")

      ;; Create evaluators at the manager level
      (println "\n1. Creating evaluators...")

      ;; Create a length-based evaluator
      (aor/create-evaluator!
       manager
       "length-50"
       "length-checker"
       {"maxLength" "50"}
       "Checks if responses are under 50 characters")

      ;; Create a conciseness evaluator (built-in)
      (aor/create-evaluator!
       manager
       "concise-30"
       "aor/conciseness"
       {"threshold" "30"}
       "Built-in conciseness evaluator")

      ;; Create F1-score evaluator for classification tasks
      (aor/create-evaluator!
       manager
       "f1-positive"
       "aor/f1-score"
       {"positiveValue" "positive"}
       "F1 score for sentiment classification")

      ;; Create comparative evaluator
      (aor/create-evaluator!
       manager
       "quality-compare"
       "quality-ranker"
       {}
       "Ranks outputs by quality metric")

      ;; Create summary evaluator
      (aor/create-evaluator!
       manager
       "accuracy-calc"
       "accuracy-summary"
       {}
       "Calculates accuracy across examples")

      (println "Created 5 evaluators")

      ;; Run the agent to generate some outputs
      (println "\n2. Running agent to generate outputs...")
      (let
        [output1 (aor/agent-invoke agent "Hi")
         output2 (aor/agent-invoke agent "This is a test input")
         output3
         (aor/agent-invoke
          agent
          "This is a much longer test input that should trigger different behavior")]

        (println "  Output 1:" output1)
        (println "  Output 2:" output2)
        (println "  Output 3:" output3)

        ;; Test evaluators with the outputs
        (println "\n3. Testing evaluators with agent outputs...")

        ;; Test regular evaluators
        (let [length-result  (aor/try-evaluator manager
                                                "length-50"
                                                "Hi"
                                                "short"
                                                output1)
              concise-result (aor/try-evaluator
                              manager
                              "concise-30"
                              "This is a test input"
                              "good medium length"
                              output2)]

          (println "\nLength evaluator result:" length-result)
          (println "Conciseness evaluator result:" concise-result))

        ;; Test comparative evaluator
        (println "\n4. Testing comparative evaluator...")
        (let [comparison-result (aor/try-comparative-evaluator
                                 manager
                                 "quality-compare"
                                 "What is best?"
                                 "good medium length"
                                 ["bad short" "good medium length" "okay"])]
          (println "Quality comparison result:" comparison-result))

        ;; Test summary evaluator with multiple examples
        (println "\n5. Testing summary evaluator...")
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
          (println "Accuracy result:" accuracy-result))

        ;; Search for evaluators
        (println "\n6. Searching for evaluators...")
        (let [search-results (aor/search-evaluators manager "length")]
          (println "Evaluators matching 'length':" search-results)))

      (println "\nKey takeaways:")
      (println
       "- Evaluators are created at the manager level, not inside agents")
      (println
       "- Agents focus on their core logic, evaluators assess their outputs")
      (println "- Different evaluator types serve different assessment needs")
      (println "- Custom evaluators can implement domain-specific logic")
      (println "- Built-in evaluators provide common metrics like F1-score"))))

(comment
  (-main))
