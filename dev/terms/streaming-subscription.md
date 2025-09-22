# Streaming Subscription

## Definition
Client-side subscription receiving streaming data from agent nodes.

## Architecture Role
Real-time data pipeline from agents to clients. Enables monitoring, progress tracking, and incremental results delivery.

## Operations
- `agent-stream` - Subscribe to streams
- `agent-stream-specific` - Filter by nodes
- `agent-stream-all` - All nodes
- Process chunks asynchronously

## Invariants
- Ordered delivery per node
- Auto-cleanup on completion
- Non-blocking reception

## Key Clojure API
- Primary functions: `agent-stream`, `agent-stream-specific`, `agent-stream-all`
- Creation: `(agent-stream client invoke callback)`
- Access: Callback invocation

## Key Java API
- Primary functions: `stream()`, `streamSpecific()`, `streamAll()`
- Creation: `agentClient.stream(invoke, callback)`
- Access: Via callback interface

## Relationships
- Uses: [Agent Invoke](agent-invoke.md), [Streaming Chunk](streaming-chunk.md)
- Used by: [Agent Client](agent-client.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/streaming_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/StreamingAgent.java`