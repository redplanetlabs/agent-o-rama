#!/usr/bin/env bash

# Java Auto-Formatter Hook for Claude Code
# Downloads Google Java Format if needed and formats Java files

set -euo pipefail
IFS=$'\n\t'
# Configuration
FORMATTER_VERSION="1.23.0"
FORMATTER_DIR="$HOME/.local/bin"
FORMATTER_JAR="$FORMATTER_DIR/google-java-format-${FORMATTER_VERSION}.jar"
FORMATTER_URL="https://github.com/google/google-java-format/releases/download/v${FORMATTER_VERSION}/google-java-format-${FORMATTER_VERSION}-all-deps.jar"

# Create directory if it doesn't exist
mkdir -p "$FORMATTER_DIR"

# Function to download the formatter
download_formatter() {
    echo "Downloading Google Java Format v${FORMATTER_VERSION}..." >&2
    if command -v curl >/dev/null 2>&1; then
        curl -L -o "$FORMATTER_JAR" "$FORMATTER_URL"
    elif command -v wget >/dev/null 2>&1; then
        wget -O "$FORMATTER_JAR" "$FORMATTER_URL"
    else
        echo "Error: Neither curl nor wget found. Please install one of them." >&2
        exit 1
    fi
    echo "Download complete." >&2
}

# Check if formatter exists, download if not
if [[ ! -f "$FORMATTER_JAR" ]]; then
    download_formatter
fi

# Read JSON input from stdin if available
input=""
if [[ ! -t 0 ]]; then
    input=$(cat)
fi

# Function to check if we should format (only if Java files were modified)
should_format() {
    if [[ -z "$input" ]]; then
        # No input, check for any Java files
        find . -name "*.java" -type f -print -quit | grep -q .
        return $?
    fi

    # Parse JSON input to check for Java file modifications
    if command -v jq >/dev/null 2>&1; then
        echo "$input" | jq -r '
            .tool_input.path //
            .tool_input.files[]?.path //
            .tool_input.file_path //
            empty
        ' | grep -q '\.java$'
        return $?
    else
        # Fallback: simple grep for .java in the input
        echo "$input" | grep -q '\.java'
        return $?
    fi
}

# Only format if Java files are involved
if should_format; then
    echo "Formatting Java files..." >&2

    # Find and format Java files (only recently modified to be efficient)
    java_files=$(find . -name "*.java" -type f -mtime -1 2>/dev/null || find . -name "*.java" -type f 2>/dev/null | head -50)

    if [[ -n "$java_files" ]]; then
        echo "$java_files" | xargs -r java -jar "$FORMATTER_JAR" --replace
        file_count=$(echo "$java_files" | wc -l)
        echo "Formatted $file_count Java file(s)." >&2
    else
        echo "No Java files found to format." >&2
    fi
else
    echo "No Java files modified, skipping formatting." >&2
fi

exit 0
