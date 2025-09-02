#!/bin/bash

# Generates the javadoc from the source java files

set -eo pipefail

# Get the current Rama version
target="target/javadoc"

echo "Generating javadocs..."

javadoc -d $target \
        -classpath "$(lein classpath)" \
        -sourcepath src/java \
        -subpackages com \
        -exclude com.rpl.agentorama.impl \
        -windowTitle "Agent-o-Rama Javadocs" \
        com.rpl.agentorama
