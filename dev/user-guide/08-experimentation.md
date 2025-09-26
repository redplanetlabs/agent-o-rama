# Experimentation

Test and improve your agents with systematic evaluation using [datasets](../terms/dataset.md), [evaluators](../terms/evaluators.md), and [experiments](../terms/experiment.md). This chapter covers creating test data, measuring performance, and running comparative evaluations.

> **Reference**: See [Dataset](../terms/dataset.md) and [Evaluators](../terms/evaluators.md) documentation for comprehensive details.

## Dataset Management

[Datasets](../terms/dataset.md) are managed collections of input/output examples for systematic agent testing and evaluation. They provide structured test data with rich metadata, versioning capabilities, and powerful organization features.

Datasets solve critical challenges in AI agent development:
- **Performance Measurement**: Systematic evaluation across test cases
- **Regression Testing**: Detect performance degradation during development
- **A/B Testing**: Compare different agent implementations
- **Training Data Management**: Organize examples for agent improvement

### Dataset Fundamentals

#### Creating Datasets with Schema Validation

Create datasets with JSON schemas for input and output validation:

```clojure
;; Define input schema for structured validation
(def customer-input-schema
  (j/write-value-as-string
   {"type" "object"
    "properties" {"message" {"type" "string", "minLength" 1}
                  "user-type" {"type" "string", "enum" ["basic" "premium" "enterprise"]}
                  "context" {"type" "object"
                            "properties" {"logged-in" {"type" "boolean"}
                                         "previous-attempts" {"type" "number"}}}}
    "required" ["message" "user-type"]}))

;; Define output schema for response validation
(def customer-output-schema
  (j/write-value-as-string
   {"type" "object"
    "properties" {"action" {"type" "string"}
                  "response" {"type" "string", "minLength" 10}
                  "steps" {"type" "array", "items" {"type" "string"}}}
    "required" ["action" "response"]}))

;; Create dataset with comprehensive metadata
(aor/create-dataset! manager "customer-support-v2"
  {:description "Advanced customer service scenarios with validation"
   :input-json-schema customer-input-schema
   :output-json-schema customer-output-schema
   :tags ["support" "qa" "production"]
   :version "2.0"
   :created-by "ai-team"
   :domain "customer-service"})
```

#### Dataset Discovery and Management

Find and organize your datasets:

```clojure
;; Search datasets by name or description
(aor/search-datasets manager "customer" 10)
;; => [{:name "customer-support-v1" :description "..."}
;;     {:name "customer-onboarding" :description "..."}]

;; Update dataset metadata
(aor/set-dataset-name! manager dataset-id "Customer Support Production")
(aor/set-dataset-description! manager dataset-id
  "Production-ready customer service evaluation scenarios")
```

### Dataset Lifecycle Management

#### Versioning with Snapshots

[Dataset snapshots](../terms/dataset-snapshot.md) provide immutable versions for reproducible experiments:

```clojure
;; Create snapshots at key milestones
(aor/snapshot-dataset! manager dataset-id nil "baseline")
(aor/snapshot-dataset! manager dataset-id nil "v1.0-release")
(aor/snapshot-dataset! manager dataset-id nil "pre-model-update")

;; Create snapshot with metadata
(aor/snapshot-dataset! manager dataset-id
  {:notes "Snapshot before GPT-4 integration"
   :commit-hash "abc123"
   :timestamp (System/currentTimeMillis)}
  "gpt4-baseline")

;; Remove outdated snapshots
(aor/remove-dataset-snapshot! manager dataset-id "experimental")
```

#### Dataset Destruction

Clean up datasets when no longer needed:

```clojure
;; Remove entire dataset (use with caution)
(aor/destroy-dataset! manager obsolete-dataset-id)
```

### Example Operations

#### Adding Examples with Rich Metadata

Add [dataset examples](../terms/dataset-example.md) with comprehensive metadata:

```clojure
;; Add single example with full metadata
(let [example-id
      (aor/add-dataset-example! manager "customer-support-v2"
        ;; Input data
        {:message "My account is locked and I can't log in"
         :user-type "premium"
         :context {:logged-in false
                  :previous-attempts 3
                  :account-age-days 365}}
        ;; Configuration options
        {:reference-output {:action "account-unlock"
                           :response "I'll help unlock your premium account..."
                           :steps ["verify-identity" "unlock-account" "send-confirmation"]}
         :tags #{"account" "login" "premium" "security"}
         :metadata {:difficulty "medium"
                   :category "account-management"
                   :priority "high"
                   :created-by "qa-team"
                   :source "user-support-ticket-12345"}})]
  (println "Added example:" example-id))

;; Add example to specific snapshot
(aor/add-dataset-example! manager dataset-id
  input-data
  {:reference-output output-data
   :tags #{"regression-test"}
   :snapshot "v1.0-release"})
```

#### Batch Operations

Process multiple examples with individual API calls:

```clojure
;; Add examples from CSV or external source
(let [examples (load-examples-from-csv "support-tickets.csv")
      processed-examples
      (map (fn [raw-example]
             {:input (transform-to-agent-input raw-example)
              :reference-output (generate-expected-output raw-example)
              :metadata {:source "csv-import"
                        :batch-id "batch-001"
                        :imported-at (System/currentTimeMillis)}})
           examples)]
  (doseq [example processed-examples]
    (aor/add-dataset-example! manager "customer-support-v2"
      (:input example)
      {:reference-output (:reference-output example)
       :metadata (:metadata example)})))

;; Tag examples by category
(let [auth-examples (filter-examples-by-category "authentication")]
  (doseq [example-id auth-examples]
    (aor/add-dataset-example-tag! manager dataset-id example-id "auth-workflow")))
```

#### Example Modification and Tagging

Update examples and manage [dataset example tags](../terms/dataset-example-tag.md):

```clojure
;; Update example input after requirements change
(aor/set-dataset-example-input! manager dataset-id example-id
  {:message "My premium account is locked"
   :user-type "premium"
   :context {:logged-in false
            :previous-attempts 3
            :subscription-status "active"}})

;; Update reference output for improved accuracy
(aor/set-dataset-example-reference-output! manager dataset-id example-id
  {:action "premium-account-unlock"
   :response "I'll prioritize unlocking your premium account..."
   :steps ["verify-premium-status" "immediate-unlock" "premium-support-follow-up"]})

;; Add tags for organization
(aor/add-dataset-example-tag! manager dataset-id example-id "premium-support")
(aor/add-dataset-example-tag! manager dataset-id example-id "regression-critical")

;; Remove outdated tags
(aor/remove-dataset-example-tag! manager dataset-id example-id "draft")

;; Snapshot-specific operations
(aor/set-dataset-example-input! manager dataset-id example-id
  updated-input {:snapshot "v2.0-dev"})
```

#### Example Removal

Remove examples that are no longer relevant:

```clojure
;; Remove individual example
(aor/remove-dataset-example! manager dataset-id obsolete-example-id)

;; Batch remove examples by criteria
(let [draft-examples (get-examples-by-tag dataset-id "draft")]
  (doseq [example-id draft-examples]
    (aor/remove-dataset-example! manager dataset-id example-id)))
```

### Organization and Search

#### Advanced Dataset Querying

Find examples using sophisticated filters:

```clojure
;; Search with text query and filters
(aor/search-examples manager "customer-support-v2"
  {:query "password reset"
   :filters {:category "authentication"
            :difficulty ["easy" "medium"]
            :user-type "premium"}
   :tags ["verified" "production"]
   :limit 25
   :offset 0
   :sort-by "created-at"
   :sort-order "desc"})

;; Find examples by metadata patterns
(aor/search-examples manager dataset-id
  {:filters {:metadata.priority "high"
            :metadata.source "user-ticket"}
   :limit 100})

;; Get examples for specific snapshot
(aor/search-examples manager dataset-id
  {:snapshot "v1.0-release"
   :limit 1000})
```

#### Dataset Statistics and Analysis

Get insights into your dataset composition:

```clojure
;; Get basic dataset information
(let [examples (aor/search-examples manager "customer-support-v2" {:limit 10000})
      total-count (count examples)
      categories (frequencies (map #(get-in % [:metadata :category]) examples))
      tags (frequencies (mapcat :tags examples))]
  {:total-examples total-count
   :categories categories
   :tag-distribution tags})

;; Example: {:total-examples 2543
;;          :categories {"authentication" 892, "billing" 651, "technical" 1000}
;;          :tag-distribution {"verified" 1200, "production" 800, "edge-case" 156}}
```

### Advanced Dataset Patterns

#### Dynamic Dataset Building

Build datasets from agent interactions:

```clojure
;; Capture production agent interactions
(defn capture-interaction [agent-input agent-output user-feedback]
  (when (= user-feedback "positive")
    (aor/add-dataset-example! manager "production-captures"
      agent-input
      {:reference-output agent-output
       :metadata {:captured-at (System/currentTimeMillis)
                 :feedback-score user-feedback
                 :source "production-capture"}
       :tags #{"validated" "production"}})))

;; Build dataset from historical logs
(defn build-from-logs [log-entries]
  (let [successful-interactions (filter #(= (:status %) "success") log-entries)]
    (doseq [entry successful-interactions]
      (aor/add-dataset-example! manager "historical-dataset"
        (:input entry)
        {:reference-output (:output entry)
         :metadata {:log-timestamp (:timestamp entry)
                   :confidence-score (:confidence entry)}
         :tags #{"historical" "validated"}}))))
```

#### Dataset Validation and Quality Control

Ensure dataset quality with validation patterns:

```clojure
;; Validate dataset examples against schema
(defn validate-dataset-quality [dataset-id]
  (let [examples (aor/search-examples manager dataset-id {:limit 10000})
        validation-results
        (map (fn [example]
               {:example-id (:id example)
                :input-valid? (validate-against-schema (:input example) input-schema)
                :output-valid? (validate-against-schema (:reference-output example) output-schema)
                :has-tags? (seq (:tags example))
                :has-metadata? (seq (:metadata example))})
             examples)]
    {:total-examples (count examples)
     :valid-inputs (count (filter :input-valid? validation-results))
     :valid-outputs (count (filter :output-valid? validation-results))
     :tagged-examples (count (filter :has-tags? validation-results))
     :quality-score (calculate-quality-score validation-results)}))

;; Clean up dataset based on quality metrics
(defn cleanup-low-quality-examples [dataset-id quality-threshold]
  (let [examples (aor/search-examples manager dataset-id {:limit 10000})
        low-quality (filter #(< (example-quality-score %) quality-threshold) examples)]
    (doseq [example low-quality]
      (println "Removing low-quality example:" (:id example))
      (aor/remove-dataset-example! manager dataset-id (:id example)))))
```

### Dataset Integration Workflows

#### Production Dataset Pipeline

Complete workflow for production dataset management:

```clojure
(defn production-dataset-pipeline []
  ;; 1. Create dataset with versioning
  (let [dataset-id (aor/create-dataset! manager "production-eval-2024-q1"
                     {:description "Q1 2024 production evaluation dataset"
                      :input-json-schema input-schema
                      :output-json-schema output-schema
                      :tags ["production" "q1-2024"]})

        ;; 2. Import examples from multiple sources
        user-feedback-examples (load-from-user-feedback)
        qa-test-examples (load-from-qa-suite)
        regression-examples (load-from-previous-datasets)]

    ;; 3. Add examples with source tracking
    (doseq [example user-feedback-examples]
      (aor/add-dataset-example! manager dataset-id
        (:input example)
        {:reference-output (:reference-output example)
         :metadata {:source "user-feedback"}}))
    (doseq [example qa-test-examples]
      (aor/add-dataset-example! manager dataset-id
        (:input example)
        {:reference-output (:reference-output example)
         :metadata {:source "qa-suite"}}))
    (doseq [example regression-examples]
      (aor/add-dataset-example! manager dataset-id
        (:input example)
        {:reference-output (:reference-output example)
         :metadata {:source "regression"}}))

    ;; 4. Create baseline snapshot
    (aor/snapshot-dataset! manager dataset-id nil "baseline-q1-2024")

    ;; 5. Validate dataset quality
    (let [examples (aor/search-examples manager dataset-id {:limit 10000})
          example-count (count examples)]
      (println "Dataset created with" example-count "examples"))

    ;; 6. Create production snapshot
    (aor/snapshot-dataset! manager dataset-id nil "production-ready")

    dataset-id))
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

### Provided Evaluator Builders

Agent-o-rama includes [provided evaluator builders](../terms/provided-evaluator-builders.md) for common evaluation tasks. These save you from implementing standard metrics:

#### aor/llm-judge

AI-powered evaluation using large language models:

```clojure
;; Create LLM judge with custom criteria
(aor/create-evaluator! manager "quality-judge" "aor/llm-judge"
  {"model" "gpt-4o"
   "temperature" "0.2"
   "prompt" "Rate the response on helpfulness (0-10): %output"})

;; Test the evaluator
(aor/try-evaluator manager "quality-judge"
  input reference-output actual-output)
```

#### aor/conciseness

Boolean evaluator for length constraints:

```clojure
;; Create conciseness evaluator with character limit
(aor/create-evaluator! manager "brief-check" "aor/conciseness"
  {"threshold" "150"})

;; Works with strings and LangChain4j messages
(aor/try-evaluator manager "brief-check"
  input reference-output "This is a concise response")
;; => {:score true} if under 150 characters
```

#### aor/f1-score

Classification metrics for binary tasks:

```clojure
;; Create F1-score evaluator for sentiment analysis
(aor/create-evaluator! manager "sentiment-f1" "aor/f1-score"
  {"positiveValue" "positive"})

;; Returns comprehensive classification metrics
(aor/try-evaluator manager "sentiment-f1"
  input "positive" "positive")
;; => {:score 1.0 :precision 1.0 :recall 1.0}
```

#### Complete Provided Builders Example

Here's how to use all three provided builders:

```clojure
(ns com.rpl.agent.basic.provided-evaluator-builders-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule ProvidedEvaluatorBuildersModule
  [topology]

  ;; Declare OpenAI model for LLM judge
  (aor/declare-agent-object-builder topology "test-model"
    (fn [_setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (System/getenv "OPENAI_API_KEY"))
          (.modelName "gpt-4o-mini")
          .build)))

  ;; Test agent that generates different response types
  (-> topology
      (aor/new-agent "TextGenerator")
      (aor/node "generate" nil
        (fn [agent-node input-type]
          (let [result (case input-type
                         "short" "Yes"
                         "medium" "This is a medium-length response"
                         "long" "This is a much longer response that contains more detailed information..."
                         "positive" "positive"
                         "negative" "negative")]
            (aor/result! agent-node result))))))

(defn create-demo-evaluators [manager]
  ;; LLM judge evaluator
  (aor/create-evaluator! manager "llm-judge" "aor/llm-judge"
    {"model" "test-model"
     "prompt" "Rate the quality of this response (0-10): %output"})

  ;; Conciseness evaluator
  (aor/create-evaluator! manager "conciseness" "aor/conciseness"
    {"threshold" "50"})

  ;; F1-score evaluator
  (aor/create-evaluator! manager "f1-score" "aor/f1-score"
    {"positiveValue" "positive"}))
```

These provided builders cover the most common evaluation scenarios and serve as references for creating your own custom evaluators.

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
4. **[Provided Evaluator Builders](../terms/provided-evaluator-builders.md)**: Built-in builders for common evaluation tasks (aor/llm-judge, aor/conciseness, aor/f1-score)
5. **[Experiment](../terms/experiment.md)**: Structured test execution
6. **[Example Run](../terms/example-run.md)**: Individual test instances
7. **[Fork](../terms/fork.md)**: Parallel execution testing

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
