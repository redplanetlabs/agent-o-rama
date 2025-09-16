# Agent

A distributed, stateful computational unit that executes directed graphs of interconnected nodes to solve complex AI workflows across cluster infrastructure.

## Purpose

Agents solve fundamental challenges in building scalable AI systems:

- **Distributed State Management**: Maintain persistent state across distributed nodes using Rama's fault-tolerant infrastructure
- **Scalable AI Workflows**: Execute multi-step AI reasoning patterns (ReAct, multi-agent collaboration) across compute clusters
- **Integration Bridge**: Unite AI models (LangChain4j) with distributed systems (Rama) through unified programming model
- **Human-in-the-Loop**: Support interactive workflows requiring human input at arbitrary execution points

## Architecture

### Graph-Based Execution
Agents execute as directed graphs where nodes represent computation steps. Nodes communicate via emissions and can access shared state through stores and agent objects.

### Distributed Runtime
- **Task Distribution**: Automatic partitioning across Rama tasks for horizontal scaling
- **State Continuity**: Agent execution state persists across node failures and cluster changes
- **Fault Tolerance**: Built-in retry mechanisms and distributed error handling

### State Management
- **Persistent Stores**: Key-value, document, and PState stores for long-term memory
- **Agent Objects**: Shared resources (AI models, APIs) with lifecycle management
- **Transactional Updates**: Atomic, consistent state changes across distributed system

## Integration

### Rama Platform
Agents leverage Rama's distributed computing capabilities through stream and microbatch topologies, automatic partitioning, and event sourcing via depots.

### LangChain4j
Seamless integration with AI models through agent objects, automatic tool calling aggregation, and streaming response handling.

## Usage Patterns

**Definition:**
```clojure
(aor/defagentmodule TodoModule [topology]
  (aor/declare-key-value-store topology "store" String Object)
  (-> topology
      (aor/new-agent "TodoAgent")
      (aor/node "process" "respond" process-fn)
      (aor/node "respond" nil respond-fn)))
```

**Multi-Step Reasoning:**
```clojure
(aor/node "chat" "chat"
  (fn [agent-node messages]
    (let [response (ai-chat messages)]
      (if (needs-tools? response)
        (aor/emit! agent-node "chat" (execute-tools response))
        (aor/result! agent-node response)))))
```

**Human Interaction:**
```clojure
(let [approval (aor/get-human-input agent-node "Approve action?")]
  (when approval (execute-action)))
```

Agents provide production-ready distributed AI orchestration with comprehensive monitoring, automatic scaling, and seamless integration between AI models and distributed infrastructure.