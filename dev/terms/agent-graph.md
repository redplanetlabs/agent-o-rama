# Agent Graph

## Definition
Directed graph structure defining agent execution flow through interconnected nodes.

## Architecture Role
Core execution blueprint for agents. Defines node relationships, execution paths, and aggregation patterns within agent modules.

## Operations
- `new-agent` - Create new graph
- `node` - Add execution node
- `agg-start-node`, `agg-node` - Add aggregation nodes
- `set-update-mode` - Configure update behavior

## Invariants
- Must have at least one node
- Node names unique within graph
- Cycles allowed for iterative patterns

## Key Clojure API
- Primary functions: `new-agent`, `node`, `agg-start-node`
- Creation: `(-> topology (new-agent "Name"))`
- Access: Method chaining on topology

## Key Java API
- Primary functions: `newAgent()`, `node()`, `aggStartNode()`
- Creation: `topology.newAgent("Name")`
- Access: `AgentGraph` interface

## Relationships
- Uses: [Agent Node](agent-node.md), [Aggregation](aggregation.md)
- Used by: [Agent Module](agent-module.md), [Agents Topology](agents-topology.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_node_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/MultiNodeAgent.java`