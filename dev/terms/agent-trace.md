# Agent Trace

## Definition
Execution monitoring system capturing node transitions and data flow.

## Architecture Role
Debugging and observability tool. Records execution paths, node invocations, and data transformations for analysis.

## Operations
- Capture node entries/exits
- Record emitted values
- Track execution timing

## Invariants
- Read-only observation
- No performance impact when disabled
- Complete execution history

## Key Clojure API
- Primary functions: Framework-managed
- Creation: Automatic when enabled
- Access: Via UI or logs

## Key Java API
- Primary functions: Framework-managed
- Creation: Automatic when enabled
- Access: Via UI or logs

## Relationships
- Uses: [Agent Node](agent-node.md), [Agent Emit](agent-emit.md)
- Used by: [User Interface](user-interface.md)

## Examples
- Clojure: Visible in UI when running agents
- Java: Visible in UI when running agents