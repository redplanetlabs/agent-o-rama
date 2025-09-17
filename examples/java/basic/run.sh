#!/bin/bash

# Basic Agent Example Runner
# Compiles and runs the basic agent example

echo "Building and running Basic Agent Example..."

# Compile the project
mvn clean compile

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "Compilation failed. Please check for errors."
    exit 1
fi

# Run the example
mvn exec:java

echo "Basic Agent Example completed."