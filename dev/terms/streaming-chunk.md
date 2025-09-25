# Streaming Chunk

## Definition
Individual piece of streaming data emitted from agent nodes during execution.

## Architecture Role
Enables real-time data flow from agents. Supports incremental output for
long-running operations and progressive results.

## Operations
- `stream-chunk!` - Emit chunk
- Subscribe to receive (Filter by node, invocation)

## Invariants
- Order preserved per node and invocation
- Non-blocking emission
- Independent of result

## Key Clojure API
- Primary functions: `stream-chunk!`
- Creation: `(stream-chunk! agent-node data)`
- Access: Via streaming subscriptions

## Key Java API
- Primary functions: `streamChunk()`
- Creation: `agentNode.streamChunk(data)`
- Access: Via streaming subscriptions

## Relationships
- Uses: [Agent Node](agent-node.md)
- Used by: [Streaming Subscription](streaming-subscription.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/streaming_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/StreamingAgent.java`
