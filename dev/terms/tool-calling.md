# Tool Calling

## Definition
Integration pattern connecting AI models with external functions and APIs.

## Architecture Role
Enables AI-driven external system interaction. Provides structured interface for model-to-function communication.

## Operations
- Define tool specifications
- Execute tool functions
- Parse tool responses
- Handle errors gracefully

## Invariants
- Type-safe tool definitions
- Deterministic execution
- Error propagation

## Key Clojure API
- Primary functions: `tool-specification`, `tool-info`, `new-tools-agent`
- Creation: `(tool-specification name description params)`
- Access: `src/clj/com/rpl/agent_o_rama/tools.clj`

## Key Java API
- Primary functions: Tool interface implementations
- Creation: Via builder patterns
- Access: LangChain4j tool interfaces

## Relationships
- Uses: [LangChain4j Integration](langchain4j-integration.md), [Tools Sub Agent](tools-sub-agent.md)
- Used by: [Agent Node](agent-node.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/tools_agent.clj`
- Java: `examples/java/react/src/main/java/com/rpl/agent/react/ReActExample.java`