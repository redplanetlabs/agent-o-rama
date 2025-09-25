# Agent Node Function

## Definition
User-defined function implementing logic for a specific node in an agent graph.

## Architecture Role
Executes business logic within agent nodes. Receives agent-node context
and arguments, performs computation, and controls flow.

Runs on a virtual thread, so all code should be in simple blocking
style.

## Operations
- Access stores and objects via agent-node
- Emit to other nodes
- Return results or stream chunks
- Request human input

## Invariants
- First argument always agent-node
- Other argument correspond to the arguments in the agent invoke (first
  node) or the emitted values from other nodes.
- Must emit or result (not both)
- Synchronous execution model

## Key Clojure API
- Primary functions: User-defined
- Creation: Lambda in `node` call
- Access: `(fn [agent-node & args] ...)`

## Key Java API
- Primary functions: `apply()`
- Creation: Implement `Function` interface
- Access: Pass to `node()` method

## Relationships
- Uses: [Agent Node](agent-node.md), [Agent Emit](agent-emit.md)
- Used by: [Agent Graph](agent-graph.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_node_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/MultiNodeAgent.java`
