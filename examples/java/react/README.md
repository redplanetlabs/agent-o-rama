# ReAct Agent Java Example

This example demonstrates how to implement a ReAct (Reasoning and Acting) agent using the agent-o-rama framework in Java. The agent can search the web using Tavily and answer questions by reasoning about the information it finds.

## What is ReAct?

ReAct is a pattern where AI agents alternate between **Reasoning** (thinking about what to do) and **Acting** (using tools to gather information or take actions). This agent implements the ReAct pattern by:

1. Receiving a user question
2. Reasoning about what information it needs
3. Using web search tools to find relevant information
4. Reasoning about the search results
5. Providing a comprehensive answer

## Features

- **Web Search Integration**: Uses Tavily search engine to find current information
- **Interactive Chat**: Continuous conversation with the agent
- **Tool Integration**: Demonstrates how to integrate external tools with LangChain4j
- **Error Handling**: Graceful handling of API failures and invalid inputs

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- OpenAI API key
- Tavily API key (for web search)

## Setup

1. **Get API Keys**:
   - OpenAI API key from https://platform.openai.com/
   - Tavily API key from https://tavily.com/

2. **Set Environment Variables**:
   ```bash
   export OPENAI_API_KEY="your-openai-api-key"
   export TAVILY_API_KEY="your-tavily-api-key"
   ```

3. **Install Dependencies**:
   ```bash
   mvn clean install
   ```

## Running the Example

```bash
# Run the agent
mvn exec:java

# Or compile and run manually
mvn compile
mvn exec:java -Dexec.mainClass="com.rpl.agent.react.ReActExample"
```

## Usage

Once the agent starts, you can ask it questions that might require web search:

```
Ask your question (agent has web search access): What's the latest news about AI safety?

Thinking...

Agent: Based on my web search, here are the latest developments in AI safety...

Ask your question (agent has web search access): Who won the 2024 Nobel Prize in Physics?

Thinking...

Agent: According to recent search results, the 2024 Nobel Prize in Physics was awarded to...
```

Type `exit`, `quit`, or press Enter on an empty line to quit.

## Architecture

The example consists of several components:

### `ReActModule`
- Main agent module that defines the agent topology
- Configures OpenAI and Tavily integrations
- Creates the agent graph with chat nodes

### `ChatNodeFunction`
- Implements the core reasoning loop
- Handles conversation flow and tool execution
- Manages the ReAct pattern of reasoning and acting

### `ToolsFactory`
- Provides web search tools using Tavily
- Handles tool execution and result formatting
- Defines tool specifications for LangChain4j

### `ReActExample`
- Main entry point with interactive console interface
- Manages agent lifecycle and user interaction
- Provides error handling and validation

## Example Questions to Try

- "What's the weather like in Tokyo today?"
- "Tell me about the latest developments in quantum computing"
- "Who are the current leaders of G7 countries?"
- "What are the recent changes to OpenAI's API?"
- "Explain the latest research in machine learning"

## Troubleshooting

**API Key Issues**:
- Ensure environment variables are set correctly
- Check that your API keys are valid and have sufficient credits

**Connection Issues**:
- Verify internet connectivity for web search
- Check if corporate firewall blocks API calls

**Build Issues**:
- Ensure Java 21+ and Maven 3.6+ are installed
- Check that all dependencies are properly resolved

## Extending the Example

You can extend this example by:

1. **Adding More Tools**: Implement additional tools like calculators, databases, or other APIs
2. **Custom Reasoning**: Modify the chat function to implement custom reasoning patterns
3. **Memory**: Add persistent storage to remember previous conversations
4. **Streaming**: Implement streaming responses for real-time interaction

## Related Examples

- Clojure version: `examples/clj/src/com/rpl/agent/react.clj`
- Todo Agent: `examples/clj/src/com/rpl/agent/todo.clj`
- Research Agent: `examples/clj/src/com/rpl/agent/research_agent.clj`