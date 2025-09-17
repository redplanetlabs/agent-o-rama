#!/bin/bash

# Basic Agent Examples Runner
# Compiles and runs the agent examples

EXAMPLE=${1:-BasicAgent}

case $EXAMPLE in
    "BasicAgent"|"basic")
        echo "Building and running BasicAgent Example..."
        MAIN_CLASS="com.rpl.agent.basic.BasicAgent"
        ;;
    "MultiNodeAgent"|"multi"|"multinode")
        echo "Building and running MultiNodeAgent Example..."
        MAIN_CLASS="com.rpl.agent.basic.MultiNodeAgent"
        ;;
    *)
        echo "Usage: $0 [BasicAgent|MultiNodeAgent]"
        echo "  BasicAgent (default) - Run the single-node agent example"
        echo "  MultiNodeAgent       - Run the multi-node agent example"
        echo ""
        echo "Aliases:"
        echo "  basic, BasicAgent    - BasicAgent example"
        echo "  multi, multinode, MultiNodeAgent - MultiNodeAgent example"
        exit 1
        ;;
esac

# Compile the project
mvn clean compile

# Check if compilation was successful
if [ $? -ne 0 ]; then
    echo "Compilation failed. Please check for errors."
    exit 1
fi

# Run the specified example
mvn exec:java -Dexec.mainClass="$MAIN_CLASS"

echo "$EXAMPLE example completed."