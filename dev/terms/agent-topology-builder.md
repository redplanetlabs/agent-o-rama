# Agent Topology Builder

## Definition
Fluent interface for constructing agent definitions through method chaining.

## Architecture Role
Provides declarative API for building complex agent graphs. Enables readable, composable agent construction patterns.

## Operations
- Chain node additions
- Configure aggregations
- Set update modes
- Build complete graphs

## Invariants
- Methods return builder for chaining
- Terminal methods finalize graph
- Order matters for some operations

## Key Clojure API
- Primary functions: `new-agent`, `node`, `agg-start-node`, `agg-node`
- Creation: Via topology methods
- Access: Threading macros (`->`)

## Key Java API
- Primary functions: Builder pattern methods
- Creation: From `AgentTopology`
- Access: Method chaining

## Relationships
- Uses: [Agent Graph](agent-graph.md), [Agent Node](agent-node.md)
- Used by: [Agents Topology](agents-topology.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_node_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/MultiNodeAgent.java`