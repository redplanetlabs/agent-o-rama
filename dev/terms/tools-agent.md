# Tools Agent

## Definition
Specialized agent executing LLM tool functions with automatic
aggregation patterns.

## Architecture Role
Dedicated execution context for AI LLM tool calling. Handles tool
orchestration and result aggregation transparently.

Run as a sub-agent.

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
- Primary functions: `tools/new-tools-agent`
- Creation: `(new-tools-agent topology name tools [options])` options
  for `:error-handler`
- Access: run as sub-agent

## Key Java API
- Primary functions: `newToolsAgent()`
- Creation: `topology.newToolsAgent(name, tools)` with
  `ToolsAgentOptions` to specify error handling.  tools is list of `ToolInfo`
  with `ToolSpecification` and an implementation function.
- Access: run as sub-agent

## Relationships
- Uses: [Tool Calling](tool-calling.md), [Sub Agents](sub-agents.md)
- Used by: [Agent Graph](agent-graph.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/tools_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/ToolsAgent.java`
