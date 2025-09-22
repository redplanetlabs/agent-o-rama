# Agent Module

## Definition
Deployable unit packaging agents, stores, and shared objects into a Rama module.

## Architecture Role
Top-level container defining complete agent systems. Encapsulates all resources needed for distributed agent execution.

## Operations
- Define agent graphs
- Declare stores
- Configure agent objects
- Deploy to cluster

## Invariants
- Module name globally unique
- All agents within module share resources
- Immutable once deployed

## Key Clojure API
- Primary functions: `defagentmodule`, `agentmodule`
- Creation: `(defagentmodule ModuleName [topology] ...)`
- Access: Module binding

## Key Java API
- Primary functions: `define()`, `build()`
- Creation: Via `AgentTopology`
- Access: `AgentsModule` class

## Relationships
- Uses: [Agents Topology](agents-topology.md), [Agent Graph](agent-graph.md)
- Used by: [Agent Manager](agent-manager.md), [IPC](ipc.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`