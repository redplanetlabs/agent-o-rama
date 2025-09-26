# Fork

## Definition
Mechanism to create new execution branches from existing agent invocations.

## Architecture Role
Enables parallel execution variants with modified parameters. Supports exploratory execution patterns and multi-path processing.

## Operations
- `agent-fork` - Create synchronous fork
- `agent-fork-async` - Create async fork
- Pass modified arguments
- Independent execution paths

## Invariants
- Parent execution continues independently
- Fork inherits parent state snapshot
- Separate result streams

## Key Clojure API
- Primary functions: `agent-fork`, `agent-fork-async`,
  `agent-initiate-fork`, `agent-initiate-fork-async`
- Creation: `(agent-fork client invoke new-args)`
- Access: Returns new invoke handle

## Key Java API
- Primary functions: `fork()`, `forkAsync()`
- Creation: `agentClient.fork(invoke, newArgs)`
- Access: Via `AgentClient`

## Relationships
- Uses: [Agent Invoke](agent-invoke.md), [Agent Client](agent-client.md)
- Used by: Parallel exploration patterns

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/forking_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/ForkingAgent.java`
