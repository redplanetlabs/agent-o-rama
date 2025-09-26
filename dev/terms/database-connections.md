# Database Connections

## Definition
External database connections that can be declared as agent objects for shared access across agent executions. Enables agents to interact with persistent storage systems external to the Rama platform.

## Architecture Role
Database connections serve as bridge components between the agent-o-rama framework and external data persistence layers. They provide standardized access to traditional databases while maintaining the distributed execution model.

## Operations
Database connections can be declared as agent objects, accessed within agent nodes, and used for standard database operations like queries, inserts, updates, and transactions. Connection lifecycle is managed by the framework.

## Invariants
Database connections are shared resources that must be thread-safe for concurrent agent access. Connection state is managed independently of agent execution state. Connections maintain their configuration throughout the agent module lifecycle.

## Key Clojure API
- Primary functions: `declare-agent-object`, `declare-agent-object-builder`, `get-agent-object`
- Creation: Via `declare-agent-object-builder` with database connection factory
- Access: `get-agent-object` within agent node functions

## Key Java API
- Primary functions: `AgentTopology.declareAgentObjectBuilder()`, `AgentNode.getAgentObject()`
- Creation: Builder pattern with database-specific connection objects
- Access: Through `AgentNode` interface in node functions

## Relationships
- Uses: [agent-objects], [agent-topology]
- Used by: [agent-node], [agent]

## Dependency graph edges:
  agent-objects -> database-connections
  agent-topology -> database-connections
  database-connections -> agent-node
  database-connections -> agent

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/agent_objects_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/AgentObjectsAgent.java`