# Agent Trace

## Definition
Execution reporting system capturing node transitions and data flow.

## Architecture Role
Debugging and observability tool. Records execution paths, node
invocations, and data transformations.

### available information:
- latencies of nodes / overall invoke
- nested ops
- token counts
- exceptions
- feedback on overall agent run or individual nodes
- trace analytics
   - Operations

## AgentInvokeStatsImpl
- Capture node entries/exits
- Record emitted values
- Track execution timing

## Invariants
- Read-only observation
- Complete execution history

## Key Clojure API
- Primary functions: `record-nested-op!`, using keyword for op type

## Key Java API
- Primary functions: `AgentNode.recordNestedOp()` using `NestedOpType`.
- Contains `AgentRef` with `AgentRef.getAgentName()` and
  `AgentRef.getModuleName`

## Relationships
- Used by: [User Interface](user-interface.md)

## Examples
- Clojure: Visible in UI when running agents
- Java: Visible in UI when running agents
