# Agent Topology

## Definition
Interface representing the configuration context for defining agents,
stores, and objects within an agent module. Provides the foundational
structure for declaring system components and their relationships.

## Architecture Role
Agent Topology serves as the configuration interface passed to agent
modules during definition. It provides methods for declaring agents,
stores, and shared objects, establishing the structural foundation for
the entire agent system.

## Operations
Topologies support declaration of key-value stores, document stores,
PState stores, agent objects, and agents themselves. Configuration
methods enable setting up the complete agent execution environment
before deployment.

An agent-topology can be declared in an ordinary Rama module, using the
low level API, should you need to create your own depots, and topologies
along side the agent.  This, for example could allow you to use a
tick-depot to trigger cleanup of agent stores.

## Invariants
Each topology maintains a consistent view of declared
components. Component names must be unique within their type
scope. Topology configuration is immutable once the module is defined
and deployed.

## Key Clojure API
- Primary functions: `agents-topology`, `new-agent`, `declare-key-value-store`, `declare-agent-object`
- Creation: Created via `agents-topology` function in module definition
- Low level: - `define-agents!` `underlying-stream-topology`
- Access: Passed as parameter to `defagentmodule` functions

## Key Java API
- Primary functions: `AgentTopology.newAgent()`, `AgentTopology.declareKeyValueStore()`
- Creation: Provided by framework during module initialization
- Access: Passed to module setup methods

## Relationships
- Uses: [agents-topology]
- Used by: [agent-module], [agent], [store], [agent-objects]

## Dependency graph edges:
  agents-topology -> agent-topology
  agent-topology -> agent-module
  agent-topology -> agent
  agent-topology -> store
  agent-topology -> agent-objects

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`
