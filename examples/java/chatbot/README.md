# Chatbot Example

A Java implementation of a conversational chatbot with persistent memory management using the agent-o-rama framework.

## Overview

This example demonstrates how to build a conversational agent that:

- **Maintains conversation history** across multiple turns
- **Automatically summarizes** long conversations when they exceed 6 messages
- **Persists memory** using key-value stores for thread-based conversations
- **Uses OpenAI GPT-4o-mini** for natural language processing

## Architecture

The chatbot consists of three main components:

### ChatbotModule
- Defines the agent topology with OpenAI model and key-value store
- Sets up the conversation flow with `chat` and `summarize` nodes

### ChatNodeFunction  
- Handles individual conversation turns
- Retrieves conversation state from memory store
- Constructs full conversation context (including summary if present)
- Decides whether to summarize or continue based on message count

### SummarizeNodeFunction
- Creates or updates conversation summaries when messages exceed threshold
- Reduces message history to keep only recent messages
- Stores updated conversation state with new summary

## Prerequisites

- Java 21 or higher
- Maven 3.6 or higher
- OpenAI API key

## Setup

1. **Set your OpenAI API key:**
   ```bash
   export OPENAI_API_KEY=your_openai_api_key_here
   ```

2. **Install dependencies:**
   ```bash
   mvn clean compile
   ```

## Running the Example

### Option 1: Using the run script
```bash
./run.sh
```

### Option 2: Using Maven directly
```bash
mvn exec:java
```

### Option 3: Manual compilation and execution
```bash
mvn clean compile
mvn exec:java -Dexec.mainClass="com.rpl.agent.chatbot.ChatbotExample"
```

## Example Conversation

The chatbot runs through a predefined conversation sequence:

```
User: hi! I'm Lance
AI: Hello Lance! Nice to meet you...

User: what's my name?
AI: Your name is Lance...

User: I like The 49'ers
AI: That's great! The 49ers are...

User: Who was their greatest player of all time?
AI: Many consider Joe Montana or Jerry Rice...

User: which team do I like?
AI: You mentioned that you like The 49ers...
```

## Memory Management

The agent demonstrates intelligent memory management:

- **Short conversations** (≤6 messages): Full history is maintained
- **Long conversations** (>6 messages): Automatically creates/updates summary and keeps only recent messages
- **Thread-based memory**: Each conversation thread maintains separate state
- **Persistent storage**: Conversation state survives across agent restarts

## Key Features

### Conversation Flow
1. User provides input
2. Agent retrieves conversation history from store
3. Agent constructs full context (summary + stored messages + current input)  
4. Agent calls OpenAI to generate response
5. If conversation is long, agent summarizes and stores reduced history
6. If conversation is short, agent stores full history

### Memory Architecture
```
Thread 0: {
  "summary": "Lance introduced himself and mentioned he likes the 49ers...",
  "messages": [recent 2 messages]
}
```

## Dependencies

- **agent-o-rama**: Core agent framework
- **Rama**: Distributed computing platform  
- **LangChain4j**: AI model integration
- **OpenAI**: Language model provider

## Customization

To modify the conversation or behavior:

1. **Change conversation inputs**: Edit `CONVERSATION_INPUTS` array in `ChatbotExample.java`
2. **Adjust summarization threshold**: Modify the `> 6` condition in `ChatNodeFunction.java`  
3. **Customize system prompts**: Add system messages in the conversation flow
4. **Change model**: Update model name in `ChatbotModule.java`

## Troubleshooting

### Common Issues

**Error: OPENAI_API_KEY not set**
- Solution: Set your OpenAI API key environment variable

**Maven compilation errors**  
- Solution: Ensure Java 21 and Maven 3.6+ are installed
- Check that agent-o-rama dependencies are available

**OutOfMemoryError**
- Solution: Increase JVM heap size with `-Xmx2g` flag

**Connection timeout to OpenAI**
- Solution: Check internet connection and API key validity

For additional help, see the main agent-o-rama documentation.