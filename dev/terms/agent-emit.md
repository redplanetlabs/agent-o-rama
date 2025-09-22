# Agent Emit

## Definition
Data transmission mechanism between nodes in an agent graph during execution.

## Architecture Role
Enables control flow and data passing within agent graphs. Triggers downstream node execution with supplied arguments.

## Operations
- `emit!` - Send data to target node
- Pass multiple arguments
- Trigger node chains

## Invariants
- Target node must exist in graph
- Emitted data becomes node arguments
- Asynchronous execution of target

## Key Clojure API
- Primary functions: `emit!`
- Creation: N/A (operation only)
- Access: Via agent-node parameter

## Key Java API
- Primary functions: `emit()`
- Creation: N/A (operation only)
- Access: `AgentNode.emit(nodeName, args...)`

## Relationships
- Uses: [Agent Node](agent-node.md), [Agent Graph](agent-graph.md)
- Used by: [Agent Node Function](agent-node-function.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_node_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/MultiNodeAgent.java`