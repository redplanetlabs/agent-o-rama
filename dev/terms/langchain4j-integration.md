# LangChain4j Integration

## Definition
Integration with LangChain4j library for AI model interactions and tool calling.

## Architecture Role
AI abstraction layer providing model interfaces, structured output, and
tool execution. Bridges agents with various LLM providers.

Provides a consistent blocking interface (as a langchain4j `ChatModel`
instance) for both provider specific `ChatModel` and
`StreamingChatModel` instances. The langchain model object can be
accessed using `IUnderlying.getUnderlying` on the returned chat model.

Provides automatic streaming (via `aor/stream-chunk!`) for
`StreamingChatModel` instances.

Provides JSON representations that are editable in the UI.

## Operations
- Chat model interactions
- Structured output parsing
- Tool calling workflows
- JSON schema generation

## Invariants
- Provider-agnostic interfaces
- Type-safe responses
- Error handling
- agent-object-builder on a ChatModel or StreamingChatModel returns a ChatModel.

## Key Clojure API
- Primary functions: `chat`, `chat-request`, LangChain4j namespace
- Creation: LangChain4j builders, via agent objects
- Access: `com.rpl.agent-o-rama/langchain4j` namespace

## Key Java API
- Primary functions: `chat()`, `generate()`, tool interfaces
- Creation: LangChain4j builders, via agent objects
- Access: Direct LangChain4j classes

## Relationships
- Uses: [Agent Objects](agent-objects.md)
- Used by: [Tool Calling](tool-calling.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/langchain4j_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/LangChain4jAgent.java`
