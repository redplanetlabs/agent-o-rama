#!/usr/bin/env bash

set -e

JAR_FILE=$(ls target/agent-o-rama-*.jar 2>/dev/null | head -n 1)

if [ -z "$JAR_FILE" ]; then
    echo "Error: No agent-o-rama jar file found in target/"
    exit 1
fi

lein pom
echo "Installing $(basename "$JAR_FILE") to local Maven repository..."

mvn install:install-file \
  -Dfile="$JAR_FILE" \
  -DpomFile="pom.xml"
