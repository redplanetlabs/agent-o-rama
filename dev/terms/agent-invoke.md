# Agent Invoke

## Definition
Handle representing a specific execution instance of an agent.

## Architecture Role
Tracks and controls individual agent executions. Provides access to results, streaming data, and execution state throughout agent lifecycle.

## Operations
- Query completion status
- Retrieve results
- Fork new executions
- Subscribe to streams

## Invariants
- Unique per execution instance
- Immutable identifier
- Valid until result retrieved

## Key Clojure API
- Primary functions: `agent-invoke`, `agent-invoke-async`,
  `agent-initiate`, `agent-initiate-async`
- Creation: Via client invoke operations
- Access: Returned from invoke calls

## Key Java API
- Primary functions: `getTaskId()`, `getAgentInvokeId()`
- Creation: `AgentClient.initiate()` returns
- Access: `AgentInvoke` class

## Relationships
- Uses: [Agent Client](agent-client.md), [Agent Result](agent-result.md)
- Used by: [Fork](fork.md), [Streaming Subscription](streaming-subscription.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/async_agent.clj`
- Java: `examples/java/src/main/java/com/rpl/agent/basic/AsyncAgent.java`
