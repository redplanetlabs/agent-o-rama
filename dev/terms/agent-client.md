# Agent Client

## Definition
Client-side handle for invoking and controlling a specific agent type within a deployed module.

## Architecture Role
Bridge between client applications and deployed agents. Manages invocations, results, streaming, and human input interactions for a particular agent type.

## Operations
- `invoke`, `invoke-async` - Execute agent synchronously or asynchronously
- `initiate`, `fork` - Create new executions or branches
- `result`, `stream` - Retrieve outputs
- `provide-human-input` - Supply human responses

## Invariants
- Bound to specific agent type within module
- Stateless between invocations
- Thread-safe for concurrent operations

## Key Clojure API
- Primary functions: `agent-client`, `agent-invoke`, `agent-invoke-async`
- Creation: `(agent-client manager "AgentName")`
- Access: Via agent manager

## Key Java API
- Primary functions: `invoke()`, `invokeAsync()`, `stream()`
- Creation: `manager.getAgentClient("AgentName")`
- Access: `AgentClient` interface

## Relationships
- Uses: [Agent Invoke](agent-invoke.md), [Agent Manager](agent-manager.md)
- Used by: Client applications

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`