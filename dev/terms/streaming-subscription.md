# Streaming Subscription

## Definition
Client-side subscription receiving streaming data from agent nodes.

## Architecture Role
Real-time data pipeline from agents to clients. Enables monitoring,
progress tracking, and incremental results delivery.

## Operations
Subscribe to stream:
- `agent-stream` -  from first invoke of an agent node
- `agent-stream-all` - from all invocations of a node (chunks are grouped by invocation-id, as a map from invocation-id to sequence of chunks) for a given agent invoke
- Process chunks asynchronously

## Invariants
- Ordered delivery per node
- Auto-cleanup on completion
- Non-blocking reception

## Key Clojure API
- Primary functions: `agent-stream`, `agent-stream-all`
- Creation: `(agent-stream client invoke callback)`
- Access: Callback invocation - must handle `reset?` argument when
  stream is reset because of a retry of the node.  Use
  `agent-stream-reset-info` to get the reset count.

## Key Java API
- Primary functions: `stream()`, `streamSpecific()`, `streamAll()`
- Creation: `agentClient.stream(invoke, callback)` returns `AgentStream`.
  `agentClient.streamAll(invoke, callback)` returns `AgentStreamByInvoke`.
- Access: Via callback interface - must handle `isReset` argument of
  `AgentClient$StreamCallback.onUpdate` when stream is reset because of
  a retry of the node. Use `AgentStream.numResets` or
  `AgentStreamByInvoke.numResetsByInvoke` to get the reset count.

## Relationships
- Uses: [Agent Invoke](agent-invoke.md), [Streaming Chunk](streaming-chunk.md)
- Used by: [Agent Client](agent-client.md)

## Examples
- Clojure:
  - `examples/clj/src/com/rpl/agent/basic/streaming_agent.clj` - Basic streaming from first invocation
  - `examples/clj/src/com/rpl/agent/basic/stream_all_agent.clj` - Streaming from multiple invocations
- Java:
  - `examples/java/basic/src/main/java/com/rpl/agent/basic/StreamingAgent.java` - Basic streaming from first invocation
  - `examples/java/basic/src/main/java/com/rpl/agent/basic/StreamAllAgent.java` - Streaming from multiple invocations
