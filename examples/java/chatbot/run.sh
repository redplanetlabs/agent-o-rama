#!/bin/bash

# Chatbot Example Runner Script
# 
# This script compiles and runs the Java chatbot example with memory management.
# Make sure to set your OPENAI_API_KEY environment variable before running.

set -e

# Check if OPENAI_API_KEY is set
if [ -z "$OPENAI_API_KEY" ]; then
    echo "Error: OPENAI_API_KEY environment variable is not set."
    echo "Please set your OpenAI API key:"
    echo "  export OPENAI_API_KEY=your_key_here"
    exit 1
fi

echo "Starting Chatbot Example..."
echo "Compiling project..."

# Compile the project
mvn clean compile

echo "Running chatbot with memory management..."
echo ""

# Run the example
mvn exec:java

echo ""
echo "Chatbot example completed."