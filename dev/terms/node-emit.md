# Node Emit

## Definition
Data transmission mechanism between nodes in agent graph execution.

## Architecture Role
Core flow control for agent graphs. Enables data passing and execution chaining between nodes.

## Operations
- Send data to target nodes
- Trigger downstream execution
- Pass multiple arguments
- Chain node sequences

## Invariants
- Target node must exist
- Asynchronous execution
- Data becomes arguments

## Key Clojure API
- Primary functions: `emit!`
- Creation: N/A (operation only)
- Access: `(emit! agent-node "target" args)`

## Key Java API
- Primary functions: `emit()`
- Creation: N/A (operation only)
- Access: `agentNode.emit("target", args)`

## Relationships
- Uses: [Agent Node](agent-node.md), [Agent Graph](agent-graph.md)
- Used by: [Agent Node Function](agent-node-function.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_node_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/MultiNodeAgent.java`