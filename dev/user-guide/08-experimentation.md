# Experimentation

Test and improve your agents with systematic evaluation using [datasets](../terms/dataset.md), [evaluators](../terms/evaluators.md), and [experiments](../terms/experiment.md). This chapter covers creating test data, measuring performance, and running comparative evaluations.

> **Reference**: See [Dataset](../terms/dataset.md) and [Evaluators](../terms/evaluators.md) documentation for comprehensive details.

## Dataset Management

[Datasets](../terms/dataset.md) are managed collections of input/output examples for agent testing and evaluation. They provide structured test data with rich metadata.

### Creating Datasets

Create datasets with descriptive metadata:

```clojure
;; Create new dataset
(aor/create-dataset! manager "customer-support-v1"
  {:description "Customer service scenarios"
   :tags ["support" "qa" "troubleshooting"]
   :version "1.0"
   :created-by "dev-team"})
```

### Adding Examples

Add examples with input, expected output, and metadata:

```clojure
;; Add single example
(aor/add-dataset-example! manager "customer-support-v1"
  {:input {:message "How do I reset my password?"
           :user-type "premium"
           :context {:logged-in false :previous-attempts 2}}
   :reference-output {:action "password-reset"
                      :response "I'll help you reset your password..."
                      :steps ["verify-email" "send-reset-link"]}
   :metadata {:difficulty "easy"
              :category "authentication"
              :tags ["password" "reset"]}})

;; Bulk add examples
(aor/bulk-add-examples! manager "customer-support-v1"
  [{:input {...} :reference-output {...} :metadata {...}}
   {:input {...} :reference-output {...} :metadata {...}}])
```

### Searching Examples

Find examples with filters and queries:

```clojure
;; Search with filters
(aor/search-examples manager "customer-support-v1"
  {:query "password"
   :filters {:category "authentication" :difficulty "easy"}
   :limit 50
   :offset 0})

;; Get dataset statistics
(aor/dataset-stats manager "customer-support-v1")
;; => {:count 1500 :categories ["auth" "billing"] :avg-difficulty 3.2}
```

## Evaluators

[Evaluators](../terms/evaluators.md) measure agent performance against datasets. Define custom evaluation logic for your specific needs.

### Declare Evaluator Builders

Create [evaluator builders](../terms/evaluator-builder.md) in your agent module:

```clojure
(aor/defagentmodule EvaluationModule
  [topology]

  ;; Simple accuracy evaluator
  (aor/declare-evaluator-builder
    topology "accuracy"
    "Measures response accuracy using exact matching"
    (fn [params]
      (fn [fetcher input reference-output output]
        (let [exact-match (= reference-output output)
              similarity (calculate-similarity reference-output output)]
          {:score (if exact-match 1.0 similarity)
           :exact-match exact-match
           :details {:reference (str reference-output)
                     :actual (str output)
                     :similarity similarity}}))))

  ;; AI-powered evaluator
  (aor/declare-evaluator-builder
    topology "ai-judge"
    "AI-powered evaluation using language models"
    (fn [{:keys [model-name criteria]}]
      (fn [fetcher input reference-output output]
        (let [model (get-ai-model model-name)
              prompt (format "Evaluate response quality against criteria: %s..."
                           (str/join ", " criteria))
              evaluation (ai-evaluate model prompt reference-output output)]
          {:score (:score evaluation)
           :reasoning (:explanation evaluation)
           :criteria-met (:criteria evaluation)})))))
```

### Create Evaluator Instances

Configure evaluators with specific parameters:

```clojure
;; Create accuracy evaluator
(aor/create-evaluator! manager "accuracy-eval" "accuracy" {}
  "Basic accuracy measurement")

;; Create AI evaluator with custom criteria
(aor/create-evaluator! manager "ai-eval" "ai-judge"
  {:model-name "gpt-4o"
   :criteria ["helpfulness" "accuracy" "clarity" "completeness"]}
  "AI-powered comprehensive evaluation")
```

### Test Single Evaluations

Validate evaluators with individual examples:

```clojure
;; Test evaluator with specific input/output
(aor/try-evaluator manager "accuracy-eval"
  input expected-output actual-output)
;; => {:score 0.85 :exact-match false :details {...}}
```

## Experiments

[Experiments](../terms/experiment.md) run agents against datasets with multiple evaluators to measure performance systematically.

### Running Experiments

Execute comprehensive evaluations:

```clojure
;; Run experiment with configuration
(aor/run-experiment! manager
  {:agent-name "CustomerSupportAgent"
   :dataset-name "support-scenarios-v1"
   :evaluators ["accuracy-eval" "ai-eval" "response-time-eval"]
   :config {:max-parallel 10
            :timeout-ms 30000
            :retry-failed true}})
```

### Experiment Results

Get detailed results with metrics:

```clojure
;; Get experiment results
(let [results (aor/get-experiment-results manager experiment-id)]
  {:overall-score (:overall-score results)
   :evaluator-scores (:evaluator-scores results)
   :example-results (:example-results results)
   :summary (:summary results)})
```

## Fork Testing

Use [forking](../terms/fork.md) to test agent variations:

```clojure
;; Test different parameter combinations
(let [original-invoke (aor/agent-initiate agent input)
      ;; Fork with different model parameters
      fork1 (aor/agent-fork agent original-invoke {"model-node" ["high-creativity"]})
      fork2 (aor/agent-fork agent original-invoke {"model-node" ["low-creativity"]})
      ;; Run variations concurrently
      result1 (future (aor/agent-result agent fork1))
      result2 (future (aor/agent-result agent fork2))]

  ;; Compare results
  {:original (aor/agent-result agent original-invoke)
   :high-creativity @result1
   :low-creativity @result2})
```

## Complete Experimentation Example

Here's a complete example from evaluator_agent.clj:

```clojure
(ns com.rpl.agent.basic.evaluator-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule EvaluatorModule
  [topology]

  ;; Simple string matching evaluator
  (aor/declare-evaluator-builder
    topology "string-similarity"
    "Evaluates string similarity between expected and actual outputs"
    (fn [params]
      (fn [fetcher input reference-output output]
        (let [ref-str (str reference-output)
              out-str (str output)
              similarity (calculate-string-similarity ref-str out-str)
              exact-match (= ref-str out-str)]
          {:score similarity
           :exact-match exact-match
           :details {:reference ref-str
                     :actual out-str
                     :similarity similarity}}))))

  ;; Test agent to evaluate
  (-> topology
      (aor/new-agent "EchoAgent")
      (aor/node "echo" nil
        (fn [agent-node input]
          (aor/result! agent-node (str "Echo: " input))))))

(defn -main [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc EvaluatorModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc (rama/get-module-name EvaluatorModule))]

      ;; Create test dataset
      (aor/create-dataset! manager "echo-test"
        {:description "Simple echo testing"})

      ;; Add test examples
      (aor/add-dataset-example! manager "echo-test"
        {:input "hello"
         :reference-output "Echo: hello"
         :metadata {:category "simple"}})

      (aor/add-dataset-example! manager "echo-test"
        {:input "world"
         :reference-output "Echo: world"
         :metadata {:category "simple"}})

      ;; Create evaluator
      (aor/create-evaluator! manager "similarity-eval" "string-similarity" {}
        "String similarity evaluation")

      ;; Run experiment
      (let [experiment-result (aor/run-experiment! manager
                                {:agent-name "EchoAgent"
                                 :dataset-name "echo-test"
                                 :evaluators ["similarity-eval"]})]

        (println "Experiment completed!")
        (println "Overall score:" (:overall-score experiment-result))
        (println "Results:" (:summary experiment-result))))))
```

## Evaluation Patterns

### A/B Testing
```clojure
;; Compare two agent versions
(let [results-v1 (aor/run-experiment! manager
                   {:agent-name "AgentV1" :dataset-name "test-set"})
      results-v2 (aor/run-experiment! manager
                   {:agent-name "AgentV2" :dataset-name "test-set"})]
  (compare-experiment-results results-v1 results-v2))
```

### Performance Benchmarking
```clojure
;; Measure response time and accuracy
(aor/run-experiment! manager
  {:agent-name "ProductionAgent"
   :dataset-name "benchmark-set"
   :evaluators ["accuracy" "response-time" "token-efficiency"]
   :config {:max-parallel 1 :timeout-ms 10000}})
```

### Regression Testing
```clojure
;; Ensure agent improvements don't break existing functionality
(let [baseline-results (load-baseline-results)
      current-results (aor/run-experiment! manager test-config)]
  (assert-no-regression baseline-results current-results))
```

## Key Concepts

You've learned experimentation patterns:

1. **[Dataset](../terms/dataset.md)**: Managed test data collections
2. **[Evaluators](../terms/evaluators.md)**: Performance measurement functions
3. **[Evaluator Builder](../terms/evaluator-builder.md)**: Metric construction patterns
4. **[Experiment](../terms/experiment.md)**: Structured test execution
5. **[Example Run](../terms/example-run.md)**: Individual test instances
6. **[Fork](../terms/fork.md)**: Parallel execution testing

These patterns enable systematic agent validation and improvement.

## Production Considerations

### Continuous Evaluation
```clojure
;; Regular evaluation pipeline
(defn daily-evaluation []
  (aor/run-experiment! manager
    {:agent-name "ProductionAgent"
     :dataset-name "validation-set"
     :evaluators ["accuracy" "user-satisfaction"]
     :config {:schedule :daily}}))
```

### Quality Monitoring
```clojure
;; Monitor production agent quality
(defn quality-check [agent-outputs]
  (let [sample-results (take 100 agent-outputs)
        evaluation (aor/run-experiment! manager
                     {:inputs sample-results
                      :evaluators ["production-quality"]})]
    (when (< (:overall-score evaluation) 0.8)
      (alert-quality-degradation evaluation))))
```

You've completed your journey through Agent-O-Rama! You can now build, deploy, test, and improve distributed AI agents that scale across clusters while maintaining reliability and performance.