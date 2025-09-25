# Retry Mechanism

## Definition
Automatic retry logic for handling failed or stalled agent executions.

## Architecture Role
Resilience layer for agent execution. Transparently handles transient
failures and network issues.

Retries are not from the start of agent. Completed nodes do not get
retried.  if there's a failure like exception in node fn or failed
machine, it continues from the failed node.

## Operations
- Automatic retry on failure
- Configurable retry counts
- Exponential backoff
- Timeout detection

## Invariants
- Preserves execution semantics
- Limited retry attempts
- Failure propagation after exhaustion

## Key Clojure API
- Primary functions: Framework-managed
- Creation: Via agent options
- Access: Configuration in invoke

## Key Java API
- Primary functions: Framework-managed
- Creation: Via agent options
- Access: Configuration in invoke

## Relationships
- Uses: [Agent Invoke](agent-invoke.md)
- Used by: [Agent Client](agent-client.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/fail_agent.clj`
- Java: Applied automatically on failures
