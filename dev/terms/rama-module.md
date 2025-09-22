# Rama Module

## Definition
Deployable unit containing agent definitions, stores, and shared objects.

## Architecture Role
Deployment boundary for agent systems. Packages all resources needed for distributed execution into single deployable unit.

## Operations
- Define complete agent systems
- Deploy to clusters
- Share resources across agents
- Version and update

## Invariants
- Self-contained deployment
- Unique module names
- Resource isolation

## Key Clojure API
- Primary functions: `defagentmodule`, `agentmodule`
- Creation: `(defagentmodule MyModule [topology] ...)`
- Access: Module namespace binding

## Key Java API
- Primary functions: Module building via topology
- Creation: Through `AgentTopology.define()`
- Access: `AgentsModule` class

## Relationships
- Uses: [Agent Module](agent-module.md), [Agents Topology](agents-topology.md)
- Used by: [Agent Manager](agent-manager.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`
