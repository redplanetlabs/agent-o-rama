# Aggregation

Distributed computation pattern for collecting and combining results from multiple node executions, enabling scatter-gather operations and parallel processing within agent graphs.

## Purpose

Aggregation solves parallel processing challenges in distributed agent systems:

- **Scatter-Gather Patterns**: Distribute work across multiple executions and collect results
- **Parallel Processing**: Execute multiple operations concurrently for performance
- **Result Combination**: Combine outputs using configurable aggregation functions
- **Load Distribution**: Balance computational work across cluster resources

## Aggregation Components

### Aggregation Start Node
Initiates parallel execution by emitting to aggregation target:
```clojure
(aor/agg-start-node "scatter" "gather"
  (fn [agent-node input-data]
    ;; Emit multiple items for parallel processing
    (doseq [item (partition-data input-data)]
      (aor/emit! agent-node "gather" item))
    ;; Return data available to aggregation node
    input-data))
```

### Aggregation Node
Collects and combines results from parallel executions:
```clojure
(aor/agg-node "gather" "next" aggs/+sum
  (fn [agent-node aggregated-sum scatter-data]
    ;; aggregated-sum: Combined result from all emissions
    ;; scatter-data: Original data from scatter node
    (aor/emit! agent-node "next" {:sum aggregated-sum :original scatter-data})))
```

## Built-in Aggregators

### Numeric Aggregation
```clojure
aggs/+sum        ; Sum all numeric values
aggs/+max        ; Maximum value
aggs/+min        ; Minimum value
aggs/+avg        ; Average value
aggs/+count      ; Count of items
```

### Collection Aggregation
```clojure
aggs/+vector     ; Collect into vector
aggs/+set        ; Collect into set (deduplication)
aggs/+concat     ; Concatenate sequences
```

### Custom Aggregation
```clojure
(aor/agg-node "gather" nil
  {:init-fn (fn [] {:total 0 :items []})
   :update-fn (fn [acc item]
                (-> acc
                    (update :total + (:value item))
                    (update :items conj item)))}
  (fn [agent-node result scatter-data]
    (aor/result! agent-node result)))
```

## Multi-Agg Pattern

Advanced aggregation with multiple collection phases:
```clojure
(aor/multi-agg
  :init {:totals {} :counts {}}

  :on "category-sum"
  (fn [acc category amount]
    (-> acc
        (update-in [:totals category] (fnil + 0) amount)
        (update-in [:counts category] (fnil inc 0))))

  :on "global-stats"
  (fn [acc stats]
    (assoc acc :global stats)))
```

## Execution Flow

1. **Scatter Phase**: Aggregation start node emits multiple values
2. **Parallel Processing**: Each emission triggers parallel node execution
3. **Collection Phase**: Aggregation node receives all results
4. **Combination**: Aggregator function combines results
5. **Continuation**: Combined result flows to next node

## Use Cases

### Parallel AI Tool Execution
```clojure
(aor/agg-start-node "call-tools" "collect-results"
  (fn [agent-node tool-calls]
    (doseq [tool-call tool-calls]
      (aor/emit! agent-node "collect-results" tool-call))))

(aor/agg-node "collect-results" "respond" aggs/+vector
  (fn [agent-node tool-results original-calls]
    (aor/emit! agent-node "respond"
      {:results tool-results :original original-calls})))
```

### Data Processing Pipeline
```clojure
(aor/agg-start-node "process-batches" "aggregate-stats"
  (fn [agent-node large-dataset]
    (doseq [batch (partition 1000 large-dataset)]
      (aor/emit! agent-node "aggregate-stats" (analyze-batch batch)))))

(aor/agg-node "aggregate-stats" nil aggs/+sum
  (fn [agent-node total-stats dataset-info]
    (aor/result! agent-node {:statistics total-stats :metadata dataset-info})))
```

### Multi-Source Data Collection
```clojure
(aor/agg-start-node "fetch-data" "combine-sources"
  (fn [agent-node query]
    (aor/emit! agent-node "combine-sources" [:database query])
    (aor/emit! agent-node "combine-sources" [:api query])
    (aor/emit! agent-node "combine-sources" [:cache query])))

(aor/agg-node "combine-sources" nil aggs/+vector
  (fn [agent-node all-results query]
    (aor/result! agent-node (merge-data-sources all-results))))
```

## Performance Considerations

### Parallelism Control
- Aggregation automatically distributes across available cluster tasks
- Number of parallel executions limited by cluster capacity
- Individual emissions can be processed on different machines

### Memory Management
- Large aggregations can consume significant memory
- Consider streaming aggregation for very large datasets
- Aggregation state is maintained in distributed memory

### Error Handling
- Failed emissions can cause aggregation to wait indefinitely
- Implement timeouts and partial result handling
- Consider retry mechanisms for critical aggregations

Aggregation provides powerful distributed processing capabilities, enabling agents to efficiently process large datasets and execute complex parallel operations while maintaining the simplicity of the agent graph programming model.