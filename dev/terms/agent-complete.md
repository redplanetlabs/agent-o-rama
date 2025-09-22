# Agent Complete

## Definition
Terminal state marker indicating successful agent execution with final result.

## Architecture Role
Signals completion of agent graph traversal. Wraps final result value returned to client after all nodes have executed.

## Operations
- Check completion status via predicates
- Extract result value
- Query completion metadata

## Invariants
- Immutable once created
- Contains exactly one result value
- Only produced by successful execution

## Key Clojure API
- Primary functions: `agent-invoke-complete?`, `agent-result`
- Creation: Implicit via `result!` in nodes
- Access: Return value from invoke operations

## Key Java API
- Primary functions: `isComplete()`, `getResult()`
- Creation: Framework-managed
- Access: `AgentComplete` class

## Relationships
- Uses: [Agent Result](agent-result.md)
- Used by: [Agent Client](agent-client.md), [Agent Invoke](agent-invoke.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`