# Agent-O-Rama User Guide

Welcome to Agent-O-Rama (AOR): your framework for building distributed AI agents that scale.

You're about to learn how to create [agents](../glossary.md#agent) that think, remember, and collaborate. Agents that handle real-world complexity with grace. This guide takes you from your first agent to production-ready systems.

## What is Agent-O-Rama?

Agent-O-Rama (also referred to as AOR) is a distributed framework for building stateful AI agents that execute across clusters. Think of it as your bridge between AI models and distributed computing: you define intelligent workflows as graphs of connected nodes, and AOR handles the scaling, fault tolerance, and state management automatically.

### The Power of Rama

Agent-O-Rama is implemented using Rama, Red Planet Labs' (RPL) groundbreaking distributed computing platform. You don't write distributed code - you write agent logic, and Rama handles the rest. This powerful foundation means your agents automatically get:

- **Distributed execution**: Your [agents](../glossary.md#agent) run across multiple machines with automatic partitioning
- **Fault tolerance**: Built-in retry mechanisms and state recovery across node failures
- **Event sourcing**: All agent state changes are durably stored and recoverable
- **Stream processing**: Real-time data flows through your agent graphs
- **Microbatch processing**: Efficient batched operations for high-throughput scenarios

Rama is what makes Agent-O-Rama possible: it provides the robust distributed foundation that turns your agent definitions into scalable, fault-tolerant systems. While you don't need to understand Rama internals to use AOR effectively, it's Rama that ensures your agents scale seamlessly from development to production clusters.

### About Red Planet Labs

Red Planet Labs (RPL) created both Agent-O-Rama and the underlying Rama platform. RPL specializes in distributed systems that make complex infrastructure simple: you focus on your business logic, and their technology handles the distributed computing challenges. With Agent-O-Rama, RPL brings this same philosophy to AI agents - making it easy to build production-ready, distributed AI systems without wrestling with infrastructure complexity.

> **Quick Reference**: For detailed explanations of agent-o-rama terminology, see the [Glossary](../glossary.md) and [Terms Documentation](../terms/).

## What You'll Build

With Agent-O-Rama, you create:
- **[Distributed Agents](../terms/agent.md)** that execute [agent graphs](../glossary.md#agent-graph) across multiple machines
- **Stateful Workflows** using [key-value stores](../terms/key-value-store.md) and distributed state management
- **Parallel Processing** with [aggregation](../terms/aggregation.md) patterns handling thousands of requests simultaneously
- **AI Integrations** leveraging [agent objects](../terms/agent-objects.md) and LangChain4j models

Think of [agents](../terms/agent.md) as intelligent workers in your distributed system: they follow workflows defined as graphs of [nodes](../glossary.md#agent-node), remember state through stores, and coordinate via [node emissions](../glossary.md#node-emit). You focus on the business logic in your [agent modules](../glossary.md#agent-module) - AOR handles the complexity of distribution, scaling, and fault tolerance.

## Your Journey

Start here and work through in order:

1. **[Core Concepts](01-core-concepts.md)**: [Agents](../terms/agent.md), [nodes](../glossary.md#agent-node), and [graphs](../glossary.md#agent-graph) - the building blocks
2. **[State Management](02-state-management.md)**: [Key-value stores](../terms/key-value-store.md) and distributed state patterns
3. **[Communication Patterns](03-communication-patterns.md)**: [Streaming chunks](../glossary.md#streaming-chunk) and [human input](../glossary.md#human-input-request)
4. **[Advanced Patterns](04-advanced-patterns.md)**: [Aggregation](../terms/aggregation.md) and [agent object](../terms/agent-objects.md) integration
5. **[System Features](05-system-features.md)**: [Forking](../glossary.md#fork), [datasets](../terms/dataset.md), and [evaluators](../glossary.md#evaluators)

## Quick Taste

Here's your first [agent](../terms/agent.md) - a simple greeter in Clojure:

```clojure
(aor/defagentmodule HelloWorldModule
  [topology]
  (-> topology
      (aor/new-agent "Greeter")
      (aor/node "greet" nil
                (fn [agent-node name]
                  (aor/result! agent-node (str "Hello, " name "!"))))))

;; Create agent client and invoke
(def client (aor/agent-client manager "Greeter"))
(aor/agent-invoke client "World")
;; => "Hello, World!"
```

You've just created an [agent module](../glossary.md#agent-module) containing a single [agent](../terms/agent.md) with one [node](../glossary.md#agent-node). The node takes input, processes it, and returns an [agent result](../glossary.md#agent-result) using `result!`.

The same agent in Java:

```java
public class HelloWorldModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        topology.newAgent("Greeter")
            .node("greet", null, (agentNode, name) -> {
                agentNode.result("Hello, " + name + "!");
            });
    }
}

// Create agent client and invoke
AgentClient client = manager.agentClient("Greeter");
String result = client.invoke("World");
// => "Hello, World!"
```

That's it: you've defined an [agent](../terms/agent.md), deployed it across a distributed system using an [agents topology](../glossary.md#agents-topology), and invoked it through an [agent client](../glossary.md#agent-client). AOR handles all the distributed computing complexity behind the scenes.

## Next Step

Ready to dive deeper? Start with [Core Concepts](01-core-concepts.md) to understand how [agent graphs](../glossary.md#agent-graph), [node emissions](../glossary.md#node-emit), and [agent objects](../terms/agent-objects.md) really work.
