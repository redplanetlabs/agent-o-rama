# LangChain4j Integration

## Definition
Integration with LangChain4j library for AI model interactions and tool calling.

## Architecture Role
AI abstraction layer providing model interfaces, structured output, and tool execution. Bridges agents with various LLM providers.

## Operations
- Chat model interactions
- Structured output parsing
- Tool calling workflows
- JSON schema generation

## Invariants
- Provider-agnostic interfaces
- Type-safe responses
- Error handling

## Key Clojure API
- Primary functions: `chat`, `chat-request`, LangChain4j namespace
- Creation: Via agent objects
- Access: `src/clj/com/rpl/agent_o_rama/langchain4j.clj`

## Key Java API
- Primary functions: `chat()`, `generate()`, tool interfaces
- Creation: LangChain4j builders
- Access: Direct LangChain4j classes

## Relationships
- Uses: [Agent Objects](agent-objects.md)
- Used by: [Tool Calling](tool-calling.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/langchain4j_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/LangChain4jAgent.java`