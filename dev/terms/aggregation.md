# Aggregation

## Definition
Distributed computation pattern for collecting and combining results from multiple node executions, enabling scatter-gather operations and parallel processing within agent graphs.

## Architecture Role
Provides parallel processing capabilities within agent graphs by coordinating scatter-gather patterns where work is distributed across cluster resources and results are collected and combined.

## Operations
Scatter phase emitting multiple values for parallel processing, collection phase gathering results from parallel executions, combination using aggregator functions.

## Invariants
Aggregation maintains execution order independence and ensures all emitted values are collected before combination. Aggregation state is distributed across cluster memory.

## Key Clojure API
- Primary functions: `agg-start-node`, `agg-node`, `multi-agg`, `emit!`
- Creation: `agg-start-node` for scatter, `agg-node` for gather with aggregator
- Access: Built-in aggregators via Rama `aggs/+sum`, `aggs/+vector`, etc.

## Key Java API
- Primary functions: `aggStartNode`, `aggNode`, `multiAgg`, `emit`
- Creation: AgentGraph builder methods for aggregation nodes
- Access: Via AggregatorFunction interface implementations
- `aggNode` a `FinishedAgg` with `FinishedAgg.getValue()`

- Aggregators: via `BuiltIn` class, `AND_AGG`, `FIRST_AGG`, `LAST_AGG`,
`LIST_AGG`, `MAP_AGG`, `MAX_AGG`, `MERGE_MAP_AGG`, `MIN_AGG`,
`MULTI_SET_AGG`, `OR_AGG`, `SET_AGG`, `SUM_AGG`

## Relationships
- Uses: [agent-node], [agent-graph], [parallel-execution]
- Used by: [scatter-gather], [parallel-tool-execution], [batch-processing]

## Dependency graph edges:
    agent-node -> aggregation
    agent-graph -> aggregation
    parallel-execution -> aggregation
    aggregation -> scatter-gather
    aggregation -> parallel-tool-execution
    aggregation -> batch-processing

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/aggregation_example.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/AggregationExample.java`
