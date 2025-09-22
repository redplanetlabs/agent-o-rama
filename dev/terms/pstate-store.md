# PState Store

## Definition
Persistent state store backed by Rama's PState for durable data.

## Architecture Role
High-performance distributed storage with complex querying. Provides ACID properties and advanced query capabilities.

## Operations
- `select` - Query data
- `selectOne` - Single result
- `transform` - Atomic updates
- Index-based access

## Invariants
- ACID transactions
- Distributed consistency
- Indexed queries

## Key Clojure API
- Primary functions: `declare-pstate-store`, `get-store`
- Creation: `(declare-pstate-store topology "name")`
- Access: `(get-store agent-node "name")`

## Key Java API
- Primary functions: `select()`, `selectOne()`, `transform()`
- Creation: `declarePStateStore()`
- Access: `PStateStore` interface

## Relationships
- Uses: Rama PStates
- Used by: [Agent Node](agent-node.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/pstate_store_agent.clj`
- Java: Not available in Java examples
