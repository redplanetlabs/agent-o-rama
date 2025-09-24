(ns com.rpl.agent.basic.provided-evaluator-builders-agent
  "Demonstrates the three built-in evaluator builders.

  Features demonstrated:
  - aor/llm-judge: AI-powered evaluation with customizable prompt and model
  - aor/conciseness: Length-based boolean evaluation
  - aor/f1-score: Classification metrics (F1, precision, recall)
  - create-evaluator!: Creating evaluators with provided builders
  - try-evaluator: Testing regular evaluators
  - try-summary-evaluator: Testing summary evaluators with multiple examples"
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest])
  (:import
   [dev.langchain4j.model.openai
    OpenAiChatModel]))

;;; Agent module with evaluator demonstration
(aor/defagentmodule ProvidedEvaluatorBuildersModule
  [topology]

  ;; Declare OpenAI model for LLM judge evaluator
  (aor/declare-agent-object-builder
   topology
   "test-model"
   (fn [_setup]
     ;; Use a test API key since we're just demonstrating the evaluator creation
     (-> (OpenAiChatModel/builder)
         (.apiKey "test-key")
         (.modelName "gpt-4o-mini")
         (.temperature 0.0)
         .build)))

  ;; Simple agent that generates text of different lengths for testing
  (->
    topology
    (aor/new-agent "TextGenerator")
    (aor/node
     "generate"
     nil
     (fn [agent-node input-type]
       (let
         [result
          (case input-type
            "short" "Yes"
            "medium" "This is a medium-length response"
            "long"
            "This is a much longer response that contains more detailed information and explanations to demonstrate various length thresholds"
            "positive" "positive"
            "negative" "negative"
            "default")]
         (aor/result! agent-node result))))))

(defn create-demo-evaluators
  "Creates the three demo evaluators using provided builders.
  Returns a map with evaluator names for reference."
  [manager]
  ;; Create LLM judge evaluator (aor/llm-judge)
  (aor/create-evaluator!
   manager
   "quality-judge"
   "aor/llm-judge"
   {"model"       "test-model"
    "temperature" "0.0"
    "prompt"
    "Rate the quality of this response on a scale of 1-10. Input: %input, Expected: %referenceOutput, Actual: %output"
    "outputSchema" "{\"type\": \"object\", \"properties\": {\"score\": {\"type\": \"integer\", \"minimum\": 0, \"maximum\": 10}}, \"required\": [\"score\"]}"}
   "AI-powered quality evaluation")

  ;; Create conciseness evaluator (aor/conciseness)
  (aor/create-evaluator!
   manager
   "brief-check"
   "aor/conciseness"
   {"threshold" "25"}
   "Checks if response is under 25 characters")

  ;; Create F1-score evaluator (aor/f1-score)
  (aor/create-evaluator!
   manager
   "sentiment-f1"
   "aor/f1-score"
   {"positiveValue" "positive"}
   "F1 score for sentiment classification")

  {:llm-judge "quality-judge"
   :conciseness "brief-check"
   :f1-score "sentiment-f1"})

(defn -main
  "Run the provided evaluator builders example"
  [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module!
     ipc
     ProvidedEvaluatorBuildersModule
     {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager
                   ipc
                   (rama/get-module-name ProvidedEvaluatorBuildersModule))
          agent   (aor/agent-client manager "TextGenerator")]

      (println "Provided Evaluator Builders Example")
      (println "====================================")

      (println "\n1. Creating evaluators using provided builders...")

      ;; Create all three demo evaluators
      (let [evaluator-names (create-demo-evaluators manager)]
        (println "Created 3 evaluators using provided builders:" evaluator-names))

      ;; Generate some test outputs
      (println "\n2. Generating test outputs...")
      (let [short-output  (aor/agent-invoke agent "short")
            medium-output (aor/agent-invoke agent "medium")
            long-output   (aor/agent-invoke agent "long")
            pos-output    (aor/agent-invoke agent "positive")
            neg-output    (aor/agent-invoke agent "negative")]

        (println "  Short output:" short-output)
        (println "  Medium output:" medium-output)
        (println "  Long output:" long-output)

        ;; Test aor/conciseness evaluator
        (println "\n3. Testing aor/conciseness evaluator...")
        (let [short-concise  (aor/try-evaluator manager "brief-check" "test" "brief" short-output)
              medium-concise (aor/try-evaluator manager "brief-check" "test" "brief" medium-output)
              long-concise   (aor/try-evaluator manager "brief-check" "test" "brief" long-output)]

          (println "  Short output concise?:" (get short-concise "concise?"))
          (println "  Medium output concise?:" (get medium-concise "concise?"))
          (println "  Long output concise?:" (get long-concise "concise?")))

        ;; Test aor/f1-score evaluator (summary type - requires multiple examples)
        (println "\n4. Testing aor/f1-score evaluator...")
        (let [examples  [(aor/mk-example-run "input1" "positive" pos-output)
                         (aor/mk-example-run "input2" "negative" neg-output)
                         (aor/mk-example-run "input3" "positive" "positive")
                         (aor/mk-example-run "input4" "negative" "positive")] ; This one is wrong

              f1-result (aor/try-summary-evaluator manager "sentiment-f1" examples)]

          (println "  F1 Score:" (get f1-result "score"))
          (println "  Precision:" (get f1-result "precision"))
          (println "  Recall:" (get f1-result "recall")))

        ;; Note about aor/llm-judge
        (println "\n5. About aor/llm-judge evaluator...")
        (println "  The LLM judge evaluator would normally make API calls to OpenAI.")
        (println "  It's created successfully but requires a real API key to execute.")
        (println "  It evaluates by sending input/reference/output to an AI model for scoring."))

      (println "\nKey takeaways:")
      (println "- aor/conciseness: Simple length-based boolean evaluation")
      (println "- aor/f1-score: Classification metrics across multiple examples")
      (println "- aor/llm-judge: AI-powered evaluation with customizable prompts")
      (println "- All provided builders are ready to use with create-evaluator!"))))

(comment
  (-main))
