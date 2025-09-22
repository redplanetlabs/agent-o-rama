# Agents Topology

## Definition
Container for defining agents, stores, and objects within a module.

## Architecture Role
Configuration layer for agent modules. Provides builder interface for declaring all module components before deployment.

## Operations
- Create agent graphs
- Declare stores
- Define agent objects
- Build final module

## Invariants
- Single topology per module
- All declarations before build
- Immutable after definition

## Key Clojure API
- Primary functions: `agents-topology`, `new-agent`, `declare-*-store`
- Creation: `(agents-topology module-name)`
- Access: Parameter in defagentmodule

## Key Java API
- Primary functions: `newAgent()`, `declareKeyValueStore()`, `define()`
- Creation: `AgentTopology.create(moduleName)`
- Access: `AgentTopology` class

## Relationships
- Uses: [Agent Graph](agent-graph.md), Stores
- Used by: [Agent Module](agent-module.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`