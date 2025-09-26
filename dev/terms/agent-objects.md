# Agent Objects

## Definition
Shared resources that agents access during execution, including AI
models, databases, APIs, and other external services with managed
lifecycles.

## Architecture Role
Provides centralized resource management within the agent topology,
enabling resource sharing across multiple agent instances while handling
initialization, connection pooling, and cleanup.

## Operations
Declaration and configuration of shared resources, runtime access by
agent nodes, lifecycle management including initialization and cleanup.

With the object builder, instances of returned objects are guaranteed to
only be made available to one consumer at a time (assumed to be
non-thread-safe). The objects are pooled for efficiency, and re-used.

## Invariants
Objects are created once per topology deployment and remain immutable
during execution. Resources are automatically cleaned up when topology
shuts down.

ObjectBuilders are instantiated on demand, and pooled.

## Key Clojure API
- Primary functions: `declare-agent-object`, `declare-agent-object-builder`, `get-agent-object`
- Creation: `declare-agent-object` for static values, `declare-agent-object-builder` for constructed resources
- Access: `get-agent-object` within agent node functions

## Key Java API
- Primary functions: `declareAgentObject`, `declareAgentObjectBuilder`,
  `getAgentObject`, `setup-object-name`
- Creation: `AgentObjectOptions` interface with `disableAutoTracing()`,
  `threadSafe()` and `workerObjectLimit()` options.
- Access: Via AgentNode interface methods

## Relationships
- Uses: [agent-topology], [agent-node], [configuration]
- Used by: [langchain4j-integration], [database-connection], [external-api]

## Dependency graph edges:
    agent-topology -> agent-objects
    agent-node -> agent-objects
    configuration -> agent-objects
    agent-objects -> langchain4j-integration
    agent-objects -> database-connection
    agent-objects -> external-api

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/langchain4j_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/LangChain4jAgent.java`
