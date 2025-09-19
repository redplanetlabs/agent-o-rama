# Experiment

Structured test runs that systematically evaluate agent performance across datasets using specific evaluators to measure behavior and effectiveness.

## Purpose

Experiments enable scientific evaluation of AI agents:

- **Performance Measurement**: Quantify agent effectiveness using standardized metrics
- **Comparative Analysis**: Compare different agent implementations or configurations
- **Regression Detection**: Identify performance degradation during development
- **Statistical Validation**: Generate confidence intervals and significance tests

## Structure

Experiments orchestrate evaluation through:
- **Agent Selection**: Specify which agent implementation to test
- **Dataset Application**: Run agent against collection of input/output examples
- **Evaluator Execution**: Apply measurement functions to agent outputs
- **Result Aggregation**: Collect and analyze performance metrics

## Configuration

### Basic Experiment
```clojure
(experiments/run-experiment manager
  {:agent-name "CustomerSupportAgent"
   :dataset-name "support-scenarios-v2"
   :evaluators ["accuracy" "response-time" "helpfulness"]
   :config {:max-retries 3
            :timeout-ms 30000}})
```

### Comparative Experiment
```clojure
(experiments/compare-agents manager
  {:agents ["AgentV1" "AgentV2" "AgentV3"]
   :dataset "benchmark-suite"
   :evaluators ["accuracy" "latency"]
   :iterations 100})
```

## Execution Model

### Parallel Evaluation
Experiments distribute agent executions across cluster resources:
- **Dataset Partitioning**: Examples distributed across available tasks
- **Concurrent Execution**: Multiple agent instances run simultaneously
- **Result Aggregation**: Metrics collected and combined automatically

### Failure Handling
- **Retry Logic**: Failed evaluations retry with exponential backoff
- **Partial Results**: Continue evaluation despite individual failures
- **Error Analysis**: Track and categorize failure patterns

## Integration with Evaluators

Experiments apply evaluator functions to agent outputs:

```clojure
;; Built-in evaluators
["accuracy" "response-time" "memory-usage"]

;; Custom evaluators
(evaluators/register manager "custom-metric"
  (fn [expected actual context]
    {:score 0.85 :details "Custom analysis"}))
```

## Result Analysis

### Statistical Metrics
- **Mean/Median**: Central tendency measurements
- **Confidence Intervals**: Statistical significance analysis
- **Distribution Analysis**: Performance variation patterns
- **Comparative Statistics**: Statistical tests between agent versions

### Performance Tracking
```clojure
(experiments/get-results manager experiment-id)
;; Returns:
{:overall-score 0.87
 :evaluator-results {"accuracy" 0.92 "response-time" 15.2}
 :example-count 500
 :failure-rate 0.03
 :execution-time-ms 45000}
```

## Experiment Types

### Regression Testing
Automated experiments that run on code changes to detect performance regressions.

### A/B Testing
Controlled comparisons between different agent implementations with statistical analysis.

### Performance Benchmarking
Standardized evaluation against reference datasets for consistent measurement.

### Hyperparameter Tuning
Systematic exploration of configuration space to optimize agent performance.

## Monitoring and Observability

Experiments provide comprehensive monitoring:
- **Real-time Progress**: Track evaluation completion status
- **Performance Metrics**: Monitor resource utilization during evaluation
- **Error Tracking**: Detailed logging of failures and exceptions
- **Result Visualization**: Built-in dashboards for result analysis

Experiments form the foundation of data-driven agent development, enabling systematic improvement through rigorous measurement and comparison of agent behavior across diverse scenarios.