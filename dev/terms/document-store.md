# Document Store

## Definition
Schema-flexible store for complex nested data structures.

## Architecture Role
Persistent storage for unstructured or semi-structured data. Supports hierarchical documents without rigid schemas.

## Operations
- Store nested objects
- Query by paths
- Update subdocuments
- Schema evolution

## Invariants
- Document atomicity
- Path-based access
- Type flexibility

## Key Clojure API
- Primary functions: `declare-document-store`, `get-store`
- Creation: `(declare-document-store topology "name")`
- Access: `(get-store agent-node "name")`

## Key Java API
- Primary functions: `get()`, `put()`, `update()`
- Creation: `declareDocumentStore()`
- Access: `DocumentStore` interface

## Relationships
- Uses: [Key-Value Store](key-value-store.md)
- Used by: [Agent Node](agent-node.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/document_store_agent.clj`
- Java: Not available in Java examples