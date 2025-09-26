# Multi-Agg

## Definition
Flexible aggregation mechanism with custom combination logic for distributed computations.

## Architecture Role
Enables sophisticated result aggregation patterns. Supports custom initialization and accumulation logic for complex aggregations.

## Operations
- Define init function
- Specify accumulation logic
- Handle completion
- Custom result transformation

## Invariants
- Init called once
- Accumulator updated per emission
- Final transformation optional

## Key Clojure API
- Primary functions: `multi-agg` macro
- Creation: `(multi-agg :init fn :on fn)`
- Access: In aggregation nodes

## Key Java API
- Primary functions: `MultiAgg.create()`, `MultiAgg.init()`, `MultiAgg.on()`
- Creation: Builder pattern
- Access: `MultiAgg` interface

## Relationships
- Uses: [Aggregation](aggregation.md), [Agent Node](agent-node.md)
- Used by: [Agent Graph](agent-graph.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/multi_agg_agent.clj`
- Java: Not available in Java examples
