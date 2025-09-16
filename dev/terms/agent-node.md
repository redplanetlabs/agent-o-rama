# Agent Node

Individual execution units within agent graphs that perform computation, access state, and control execution flow through emissions and results.

## Purpose

Agent nodes serve as computational vertices in directed agent graphs, enabling:

- **Discrete Processing Steps**: Each node represents a single logical operation in agent workflow
- **Flow Control**: Nodes direct execution through emissions to other nodes or termination via results
- **State Access**: Nodes interact with persistent stores and shared agent objects
- **Communication Hub**: Nodes emit data, stream chunks, and request human input

## Communication Mechanisms

### Inter-Node Communication
```clojure
(aor/emit! agent-node "target-node" data config)
```
Sends data to specified downstream nodes, triggering asynchronous execution with validated outputs.

### Execution Termination
```clojure
(aor/result! agent-node final-value)
```
Signals completion of agent execution, returning final result to client. Mutually exclusive with emit!.

### Real-Time Streaming
```clojure
(aor/stream-chunk! agent-node chunk-data)
```
Emits ordered streaming data for real-time consumption by client subscriptions.

### Human Interaction
```clojure
(aor/get-human-input agent-node "prompt")
```
Synchronously requests human input, pausing execution until response received.

## State Access

### Persistent Stores
```clojure
(let [store (aor/get-store agent-node "store-name")]
  (store/get store key)
  (store/put! store key value))
```
Access distributed key-value or document stores with automatic partitioning and caching.

### Shared Resources
```clojure
(let [model (aor/get-agent-object agent-node "openai-model")]
  ;; Use AI model, database, or API
  )
```
Retrieve shared resources with automatic lifecycle management and connection pooling.

## Node Types

### Basic Node
```clojure
(aor/node "process" "next"
  (fn [agent-node input]
    (aor/emit! agent-node "next" (process input))))
```

### Terminal Node
```clojure
(aor/node "final" nil
  (fn [agent-node data]
    (aor/result! agent-node (finalize data))))
```

### Aggregation Start
```clojure
(aor/agg-start-node "scatter" "gather"
  (fn [agent-node input]
    (dotimes [i 3]
      (aor/emit! agent-node "gather" i))))
```

### Aggregation Node
```clojure
(aor/agg-node "gather" nil aggs/+sum
  (fn [agent-node sum scatter-input]
    (aor/result! agent-node [sum scatter-input])))
```

## Design Patterns

**Conditional Branching:**
```clojure
(fn [agent-node input condition]
  (if condition
    (aor/emit! agent-node "path-a" input)
    (aor/emit! agent-node "path-b" input)))
```

**Iterative Processing:**
```clojure
(fn [agent-node state config]
  (if (continue? state)
    (aor/emit! agent-node "same-node" (update-state state))
    (aor/result! agent-node (finalize state))))
```

## Execution Model

Nodes execute asynchronously on distributed tasks via virtual thread pools with automatic retry mechanisms, ordered emission processing, and comprehensive instrumentation for monitoring and debugging. State changes are transactional and resources are automatically managed throughout the execution lifecycle.