# Agent-O-Rama User Guide

Welcome to Agent-O-Rama: your framework for building distributed AI agents that scale.

You're about to learn how to create agents that think, remember, and collaborate. Agents that handle real-world complexity with grace. This guide takes you from your first agent to production-ready systems.

## What You'll Build

Agent-O-Rama lets you create:
- **Distributed agents** that run across multiple machines
- **Stateful workflows** that remember context between interactions
- **Parallel processing** that handles thousands of requests simultaneously
- **AI integrations** that leverage LLMs through LangChain4j

Think of agents as smart workers in your system. Each agent follows a graph of tasks, maintains its own state, and communicates with other agents. You define the logic; Agent-O-Rama handles the distribution, scaling, and fault tolerance.

## Your Journey

Start here and work through in order:

1. **[Core Concepts](01-core-concepts.md)**: Agents, nodes, and graphs - the building blocks
2. **[State Management](02-state-management.md)**: How agents remember and share data
3. **[Communication Patterns](03-communication-patterns.md)**: Streaming and human interaction
4. **[Advanced Patterns](04-advanced-patterns.md)**: Aggregation and AI model integration
5. **[System Features](05-system-features.md)**: Forking, datasets, and evaluation

## Quick Taste

Here's your first agent in Clojure:

```clojure
(aor/defagentmodule HelloWorldModule
  [topology]
  (-> topology
      (aor/new-agent "Greeter")
      (aor/node "greet" nil
                (fn [agent-node name]
                  (aor/result! agent-node (str "Hello, " name "!"))))))

;; Invoke it
(def client (aor/agent-client manager "Greeter"))
(aor/agent-invoke client "World")
;; => "Hello, World!"
```

And in Java:

```java
public class HelloWorldModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("Greeter")
            .node("greet", null, (agentNode, name) -> {
                agentNode.result("Hello, " + name + "!");
            });
    }
}

// Invoke it
AgentClient client = manager.agentClient("Greeter");
String result = client.invoke("World");
// => "Hello, World!"
```

That's it. You've defined an agent, deployed it to a distributed system, and invoked it. The framework handles everything else.

## Next Step

Ready to dive deeper? Start with [Core Concepts](01-core-concepts.md) to understand how agents really work.