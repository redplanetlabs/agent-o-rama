# Agent Execution

Your agents run by executing their [graph](../terms/agent-graph.md) through connected [nodes](../terms/agent-node.md). This chapter covers [node emissions](../terms/node-emit.md), routing patterns, and [aggregation](../terms/aggregation.md) for parallel processing.

> **Reference**: See [Agent Emit](../terms/agent-emit.md) and [Aggregation](../terms/aggregation.md) documentation for comprehensive details.

## Node Emissions: Flow Control

[Node emit](../terms/node-emit.md) is how nodes send data to other nodes in your [agent graph](../terms/agent-graph.md). It's the mechanism that drives execution flow:

```clojure
;; Send data to next node
(aor/emit! agent-node "target-node" data1 data2 data3)
```

The target node receives all emitted values as function arguments.

## Multi-Node Execution

Here's the complete multi_node_agent.clj showing connected nodes:

```clojure
(ns com.rpl.agent.basic.multi-node-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule MultiNodeAgentModule
  [topology]

  ;; Thread the agent through multiple node declarations
  (-> (aor/new-agent topology "MultiNodeAgent")

      ;; First node: receive input and forward it
      (aor/node
       "receive"
       "personalize" ; target node for emission
       (fn [agent-node user-name]
         (aor/emit! agent-node "personalize" user-name)))

      ;; Second node: transform the data
      (aor/node
       "personalize"
       "finalize"
       (fn [agent-node user-name]
         (let [greeting (str "Hello, " user-name "!")]
           ;; Emit multiple values to next node
           (aor/emit! agent-node "finalize" user-name greeting))))

      ;; Final node: combine results and return
      (aor/node
       "finalize"
       nil ; terminal node
       (fn [agent-node user-name greeting] ; receives both values
         (let [result (str greeting
                           " Welcome to agent-o-rama! "
                           "Thanks for joining us, "
                           user-name
                           ".")]
           (aor/result! agent-node result))))))
```

Execution flow:
1. `receive` node gets initial input
2. Emits to `personalize` node
3. `personalize` transforms data and emits to `finalize`
4. `finalize` creates final result

## Emission Patterns

### Single Value
```clojure
;; Emit one value
(aor/emit! agent-node "next-node" data)
```

### Multiple Values
```clojure
;; Emit multiple values - next node receives all as arguments
(aor/emit! agent-node "next-node" arg1 arg2 arg3)
```

### Conditional Emission
```clojure
;; Emit based on conditions
(if condition
  (aor/emit! agent-node "success-node" result)
  (aor/emit! agent-node "failure-node" error))
```

## Aggregation: Parallel Processing

[Aggregation](../terms/aggregation.md) enables scatter-gather patterns for parallel processing. Use `agg-start-node` to fan out work and `agg-node` to collect results.

Here's the complete aggregation_agent.clj example:

```clojure
(ns com.rpl.agent.basic.aggregation-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule AggregationAgentModule
  [topology]

  (-> (aor/new-agent topology "AggregationAgent")

      ;; Start aggregation by distributing work
      (aor/agg-start-node
       "distribute-work"
       "process-chunk"
       (fn [agent-node {:keys [data chunk-size]}]
         (let [chunks (partition-all chunk-size data)]
           ;; Emit each chunk for parallel processing
           (doseq [chunk chunks]
             (aor/emit! agent-node "process-chunk" chunk)))))

      ;; Process individual chunks in parallel
      (aor/node
       "process-chunk"
       "collect-results"
       (fn [agent-node chunk]
         ;; Transform the chunk data
         (let [processed-chunk (mapv #(* % %) chunk)
               chunk-sum (reduce + processed-chunk)]
           (aor/emit! agent-node "collect-results"
                     {:original-chunk chunk
                      :processed-chunk processed-chunk
                      :chunk-sum chunk-sum}))))

      ;; Aggregate all results
      (aor/agg-node
       "collect-results"
       nil
       aggs/+vec-agg ; built-in vector aggregator
       (fn [agent-node aggregated-results _]
         (let [sorted-results (sort-by #(first (:original-chunk %)) aggregated-results)
               total-sum (reduce + (map :chunk-sum sorted-results))
               total-items (reduce + (map #(count (:original-chunk %)) sorted-results))]
           (aor/result! agent-node
                       {:total-items total-items
                        :total-sum total-sum
                        :chunks-processed (count sorted-results)
                        :chunk-results sorted-results}))))))
```

### Aggregation Start Node

`agg-start-node` initiates parallel processing:

```clojure
(aor/agg-start-node
 "start-node"
 "target-node"
 (fn [agent-node input]
   ;; Emit to target-node multiple times for parallel processing
   (doseq [item items]
     (aor/emit! agent-node "target-node" item))))
```

### Aggregation Node

`agg-node` collects and combines results:

```clojure
(aor/agg-node
 "collect-node"
 "next-node"
 aggregator-function    ; how to combine results
 (fn [agent-node combined-results scatter-data]
   ;; Process the aggregated results
   (aor/emit! agent-node "next-node" combined-results)))
```

Built-in aggregators include:
- `aggs/+vec-agg` - Collect into vector
- `aggs/+count-agg` - Count items
- `aggs/+sum-agg` - Sum numeric values

## Multi-Agg: Custom Aggregation

[Multi-agg](../terms/multi-agg.md) enables custom aggregation logic with multiple accumulation patterns:

```clojure
(aor/multi-agg
 :init {:sum 0 :count 0 :max Integer/MIN_VALUE}

 :on "data-node"
 (fn [acc value]
   (-> acc
       (update :sum + value)
       (update :count inc)
       (update :max max value)))

 :on "finalize"
 (fn [acc]
   (assoc acc :average (/ (:sum acc) (:count acc)))))
```

## Human Input Requests

Agents can request human input during execution using [human input request](../terms/human-input-request.md):

```clojure
(aor/node "approval" "execute"
  (fn [agent-node action-data]
    (let [approval (aor/get-human-input agent-node
                     (str "Approve action: " (:description action-data) "?"))]
      (if (= "yes" (str/lower-case approval))
        (aor/emit! agent-node "execute" action-data)
        (aor/result! agent-node "Action cancelled")))))
```

The agent execution pauses until human input is provided via the client API.

## Sub-Agents

[Sub-agents](../terms/sub-agents.md) enable agents to invoke other agents within their execution:

```clojure
(aor/node "invoke-helper" "process-result"
  (fn [agent-node data]
    (let [helper-client (aor/agent-client agent-node "HelperAgent")
          helper-result (aor/agent-invoke helper-client data)]
      (aor/emit! agent-node "process-result" helper-result))))
```

Sub-agents run with limited functionality - no async API or streaming.

## Execution Examples

### Simple Chain
```clojure
input → process → transform → result
```

### Conditional Routing
```clojure
input → validate → [success-node | failure-node] → result
```

### Aggregation Pattern
```clojure
input → distribute → [process1, process2, process3] → collect → result
```

### Human-in-Loop
```clojure
input → analyze → request-approval → [execute | cancel] → result
```

## Complete Execution Example

Here's how to run the multi-node agent:

```clojure
(defn -main [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MultiNodeAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                    (rama/get-module-name MultiNodeAgentModule))
          agent   (aor/agent-client manager "MultiNodeAgent")]

      (println "Result:" (aor/agent-invoke agent "Alice"))
      ;; => Result: Hello, Alice! Welcome to agent-o-rama! Thanks for joining us, Alice.
      )))
```

## Key Concepts

You've learned agent execution patterns:

1. **[Node Emit](../terms/node-emit.md)**: Flow control between nodes
2. **[Agent Graph](../terms/agent-graph.md)**: Directed execution structure
3. **[Agent Node Declaration](../terms/agent-node-declaration.md)**: Blueprint for computation units
4. **[Aggregation](../terms/aggregation.md)**: Parallel scatter-gather processing
5. **[Multi-Agg](../terms/multi-agg.md)**: Custom aggregation logic
6. **[Human Input Request](../terms/human-input-request.md)**: Human-in-the-loop workflows
7. **[Sub-Agents](../terms/sub-agents.md)**: Agent-to-agent invocation

These patterns enable complex distributed workflows.

## What's Next

You can execute complex agent graphs. Next, learn [Storage and Objects](05-storage-and-objects.md) to add persistent state and shared resources to your agents.