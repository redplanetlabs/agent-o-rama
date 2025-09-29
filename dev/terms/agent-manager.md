# Agent Manager

## Definition
Client-side coordinator for accessing and managing deployed agent modules in a cluster.

## Architecture Role
Primary entry point for client applications. Manages agent clients, datasets, and evaluators across multiple deployed modules.

## Operations
- `agent-client` - Get client for specific agent
- `agent-names` - List available agents
- Dataset CRUD operations
- Evaluator management

## Invariants
- Thread-safe for concurrent access
- Maintains connection to cluster
- Caches agent metadata

## Key Clojure API
- Primary functions: `agent-manager`, `agent-client`, `agent-names`
- Creation: `(agent-manager ipc module-name)`
- Access: Direct instantiation

## Key Java API
- Primary functions: `getAgentClient()`, `getAgentNames()`, `createDataset()`
- Creation: `AgentManager.create(ipc, moduleName)`
- Access: `AgentManager` class

## Relationships
- Uses: [Agent Module](agent-module.md), [IPC](ipc.md)
- Used by: [Agent Client](agent-client.md), [Dataset](dataset.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`
