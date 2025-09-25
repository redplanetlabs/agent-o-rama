# Agent Result

## Definition
Final output value produced by agent execution.

## Architecture Role
Termination signal and return value for agent graph execution. Passed from nodes to clients through framework.

## Operations
- `result!` - Set result in node
- `agent-result` - Retrieve from client
- `agent-result-async` - Async retrieval

## Invariants
- Only one result per execution
- Immutable once set
- Terminates graph traversal

## Key Clojure API
- Primary functions: `result!`, `agent-result`, `agent-result-async`
- Creation: `(result! agent-node value)`
- Access: Via agent invoke handle

## Key Java API
- Primary functions: `result()`, `getResult()`
- Creation: `agentNode.result(value)`
- Access: `AgentInvoke.getResult()`

## Relationships
- Uses: [Agent Node](agent-node.md)
- Used by: [Agent Complete](agent-complete.md), [Agent Invoke](agent-invoke.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`
