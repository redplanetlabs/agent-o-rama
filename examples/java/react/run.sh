#!/bin/bash

# ReAct Agent Runner Script
# This script helps run the Java ReAct agent example

set -e

# Check if API keys are set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "Error: OPENAI_API_KEY environment variable is not set."
    echo "Please set your OpenAI API key: export OPENAI_API_KEY=your_key_here"
    exit 1
fi

if [ -z "$TAVILY_API_KEY" ]; then
    echo "Error: TAVILY_API_KEY environment variable is not set."
    echo "Please set your Tavily API key: export TAVILY_API_KEY=your_key_here"
    exit 1
fi

# Build and run the example
echo "Building ReAct agent example..."
mvn compile -q

echo "Starting ReAct agent..."
echo "You can ask questions that require web search."
echo "Type 'exit' or 'quit' to stop the agent."
echo ""

mvn exec:java -Dexec.mainClass="com.rpl.agent.react.ReActExample" -q
