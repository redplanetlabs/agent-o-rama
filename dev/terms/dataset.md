# Dataset

Managed collections of input/output examples used for agent testing, evaluation, and performance tracking in the agent-o-rama framework.

## Purpose

Datasets solve critical challenges in AI agent development:

- **Performance Measurement**: Systematic evaluation of agent behavior across test cases
- **Regression Testing**: Detect performance degradation during agent development
- **A/B Testing**: Compare different agent implementations or configurations
- **Training Data Management**: Organize examples for agent improvement and fine-tuning

## Structure

Datasets contain structured examples with:
- **Input Data**: Parameters and context for agent invocation
- **Expected Outputs**: Reference results for comparison
- **Metadata**: Tags, timestamps, and evaluation context
- **Evaluation Results**: Performance metrics and analysis

## Management Operations

### Creation and Population
```clojure
(datasets/create-dataset manager "customer-support-v1"
  {:description "Customer service scenarios"
   :tags ["support" "v1"]})

(datasets/add-example manager "customer-support-v1"
  {:input {:message "How do I reset my password?"
           :user-type "premium"}
   :expected-output {:action "password-reset"
                     :steps ["email-verification" "new-password"]}})
```

### Evaluation Integration
```clojure
(experiments/run-experiment manager
  {:agent-name "SupportAgent"
   :dataset-name "customer-support-v1"
   :evaluators ["accuracy" "response-time"]})
```

## Dataset Types

### Test Datasets
Static collections of validated examples for regression testing and performance benchmarking.

### Training Datasets
Dynamic collections that grow from agent interactions, used for model improvement and behavior analysis.

### Benchmark Datasets
Standardized collections for comparing agent implementations across different approaches or versions.

## Integration with Experiments

Datasets integrate with the experiment system to enable:
- **Automated Evaluation**: Run agents against dataset examples
- **Performance Tracking**: Monitor metrics over time
- **Comparative Analysis**: Compare different agent versions
- **Statistical Analysis**: Generate confidence intervals and significance tests

## Storage and Versioning

Datasets are stored in distributed PState structures with:
- **Immutable Examples**: Once added, examples cannot be modified
- **Version Control**: Track dataset evolution over time
- **Distributed Access**: Available across cluster for parallel evaluation
- **Backup and Recovery**: Automatic persistence and fault tolerance

## Example Management

```clojure
;; Add multiple examples
(datasets/bulk-add-examples manager "dataset-name" examples)

;; Query examples
(datasets/get-examples manager "dataset-name"
  {:filter {:tag "critical"}
   :limit 100})

;; Dataset statistics
(datasets/get-stats manager "dataset-name")
;; Returns: {:count 1500 :tags ["support" "billing"] :created "2024-01-15"}
```

Datasets provide the foundation for systematic agent evaluation, enabling data-driven development and continuous improvement of AI agent performance through structured testing and measurement.