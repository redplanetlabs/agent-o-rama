# Mirror Agent

## Definition
Local proxy for an agent defined in another module, enabling cross-module agent interactions within distributed systems. Provides transparent access to remote agents as if they were locally defined.

## Architecture Role
Mirror agents serve as distributed system bridges, allowing agents in one module to invoke agents defined in separate modules. They enable modular agent architectures where functionality is distributed across multiple deployable units.

## Operations
Mirror agents can be declared to reference remote agents, invoked like local agents, and used in complex multi-module workflows. They handle remote communication transparently while maintaining the same invocation patterns as local agents.

## Invariants
Mirror agents maintain referential integrity with their remote counterparts. They preserve the same interface and behavior as the original agent. Communication failures are handled through the framework's retry and error handling mechanisms.

## Key Clojure API
- Primary functions: `declare-cluster-agent`
- Creation: `declare-cluster-agent` with remote module and agent name references
- Access: Used through standard agent client interfaces

## Key Java API
- Primary functions: `AgentTopology.declareClusterAgent()`
- Creation: Declaration with module name and agent identifier
- Access: Through standard `AgentClient` interfaces

## Relationships
- Uses: [cluster-manager], [agent-module]
- Used by: [agent-client], [agent-manager]

## Dependency graph edges:
  cluster-manager -> mirror-agent
  agent-module -> mirror-agent
  mirror-agent -> agent-client
  mirror-agent -> agent-manager

## Examples
- Clojure: Example usage in multi-module test scenarios
- Java: Cross-module integration examples