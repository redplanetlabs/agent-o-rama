# Sub Agents

## Definition
Agents executing within other agents.

## Architecture Role
Nested execution context for factoring out common sub-graphs into agents.

## Operations
- Behave like any other agent
- Synchronous execution
- Propagate human-input requests to the root agent

## Invariants
- No async API access
- No streaming capabilities

## Key Clojure API
- Primary functions: `agent-client`

## Key Java API
- Primary functions: `AgentNode.agentClient`

## Relationships
- Uses: [Agent Node](agent-node.md)
- Used by: [Tools Sub Agent](tools-sub-agent.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/tools_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/ToolsAgent.java`
