# Agent-O-Rama User Guide

Welcome to Agent-O-Rama: your framework for building distributed AI agents that scale.

You're about to learn how to create [agents](../glossary.md#agent) that think, remember, and collaborate. Agents that handle real-world complexity with grace. This guide takes you from your first agent to production-ready systems.

> **Quick Reference**: For detailed explanations of agent-o-rama terminology, see the [Glossary](../glossary.md) and [Terms Documentation](../terms/).

## What You'll Build

Agent-O-Rama lets you create:
- **[Distributed Agents](../terms/agent.md)** that execute [agent graphs](../glossary.md#agent-graph) across multiple machines
- **Stateful Workflows** using [key-value stores](../terms/key-value-store.md) and distributed state management
- **Parallel Processing** with [aggregation](../terms/aggregation.md) patterns handling thousands of requests simultaneously
- **AI Integrations** leveraging [agent objects](../terms/agent-objects.md) and LangChain4j models

Think of [agents](../terms/agent.md) as smart workers in your distributed system. Each agent follows a graph of [nodes](../glossary.md#agent-node), maintains its own state through stores, and communicates via [node emissions](../glossary.md#node-emit). You define the logic through [agent modules](../glossary.md#agent-module); Agent-O-Rama handles the distribution, scaling, and fault tolerance.

## Your Journey

Start here and work through in order:

1. **[Core Concepts](01-core-concepts.md)**: [Agents](../terms/agent.md), [nodes](../glossary.md#agent-node), and [graphs](../glossary.md#agent-graph) - the building blocks
2. **[State Management](02-state-management.md)**: [Key-value stores](../terms/key-value-store.md) and distributed state patterns
3. **[Communication Patterns](03-communication-patterns.md)**: [Streaming chunks](../glossary.md#streaming-chunk) and [human input](../glossary.md#human-input-request)
4. **[Advanced Patterns](04-advanced-patterns.md)**: [Aggregation](../terms/aggregation.md) and [agent object](../terms/agent-objects.md) integration
5. **[System Features](05-system-features.md)**: [Forking](../glossary.md#fork), [datasets](../terms/dataset.md), and [evaluators](../glossary.md#evaluators)

## Quick Taste

Here's your first [agent](../terms/agent.md) in Clojure:

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

This creates an [agent module](../glossary.md#agent-module) with a single [agent](../terms/agent.md) containing one [node](../glossary.md#agent-node). The node receives input, processes it, and returns an [agent result](../glossary.md#agent-result) using `result!`.

And in Java:

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

That's it. You've defined an [agent](../terms/agent.md), deployed it to a distributed system using an [agents topology](../glossary.md#agents-topology), and invoked it through an [agent client](../glossary.md#agent-client). The framework handles everything else.

## Next Step

Ready to dive deeper? Start with [Core Concepts](01-core-concepts.md) to understand how [agent graphs](../glossary.md#agent-graph), [node emissions](../glossary.md#node-emit), and [agent objects](../terms/agent-objects.md) really work.
