# Agent Node Declaration

## Definition
Blueprint for computation units within agent graphs, defining node name, target, and function.

## Architecture Role
Structural element that defines how nodes connect and what logic they execute. Forms the building blocks of agent graph topology.

## Operations
Declare node:
- Specify node name (unique identifier)
- Specify target node (or nil for terminal)
- Provide agent node function for execution logic
- Chain declarations to build complete graphs

## Invariants
- Node names must be unique within agent graph
- Target nodes must exist or be nil
- Function must accept agent-node as first parameter
- Declarations create static graph structure

## Key Clojure API
- Primary function: `aor/node`
- Declaration: `(aor/node "node-name" "target-node" node-function)`
- Chaining: `(-> (aor/new-agent topology "Agent") (aor/node ...) (aor/node ...))`

## Key Java API
- Primary method: `.node()`
- Declaration: `topology.newAgent("Agent").node("node-name", "target-node", nodeFunction)`
- Chaining: Method chaining pattern

## Relationships
- Uses: [Agent Node Function](agent-node-function.md)
- Creates: [Agent Node](agent-node.md) instances at runtime
- Part of: [Agent Graph](agent-graph.md)
- Used by: [Agents Topology](agents-topology.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/basic_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/BasicAgent.java`