# Tools Sub Agent

## Definition
Specialized agent type executing tool functions with automatic aggregation patterns.

## Architecture Role
Dedicated execution context for AI tool calling. Handles tool orchestration and result aggregation transparently.

## Operations
- Execute tools based on AI decisions
- Aggregate tool results
- Handle tool errors
- Return structured responses

## Invariants
- No direct client access
- Automatic aggregation
- Tool-specific execution

## Key Clojure API
- Primary functions: `new-tools-agent`, `newToolsAgent`
- Creation: `(new-tools-agent topology name tools)`
- Access: Via agent topology

## Key Java API
- Primary functions: `newToolsAgent()`
- Creation: `topology.newToolsAgent(name, tools)`
- Access: Through `AgentTopology`

## Relationships
- Uses: [Tool Calling](tool-calling.md), [Sub Agents](sub-agents.md)
- Used by: [Agent Graph](agent-graph.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/tools_agent.clj`
- Java: `examples/java/react/src/main/java/com/rpl/agent/react/ReActExample.java`