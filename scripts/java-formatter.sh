#!/usr/bin/env bash

# Java Formatter
#
# Can be used directly with file arguments, JSON stdin, or as a Hook for Claude
# Code Downloads Google Java Format if needed and formats Java files
#
# Usage:
#   # Format specific files
#   ./java-formatter.sh file1.java file2.java
#
#   # JSON from stdin as used by claude code hooks
#   echo '{"tool_input": {"file_path": "MyFile.java"}}' |
#       ./java-formatter.sh
#
#   # Format all Java files in current directory
#   ./java-formatter.sh

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

# Function to get Java files from various input sources
get_java_files() {
    local files=()

    # If command line arguments provided, use those
    if [[ $# -gt 0 ]]; then
        for arg in "$@"; do
            if [[ "$arg" == *.java && -f "$arg" ]]; then
                files+=("$arg")
            fi
        done
        printf '%s\n' "${files[@]}"
        return
    fi

    # Read JSON input from stdin if available
    local input=""
    if [[ ! -t 0 ]]; then
        input=$(cat)
    fi

    # Parse JSON input to extract specific Java files
    if [[ -n "$input" ]]; then
        if command -v jq >/dev/null 2>&1; then
            # Extract file paths from various JSON structures
            local json_files
            json_files=$(echo "$input" | jq -r '
                (.tool_input.path // empty),
                (.tool_input.files[]?.path // empty),
                (.tool_input.file_path // empty),
                (.files[]?.path // empty),
                (.path // empty),
                (.file_path // empty)
            ' 2>/dev/null | grep '\.java$' | sort -u)

            if [[ -n "$json_files" ]]; then
                echo "$json_files"
                return
            fi
        else
            # Fallback: extract .java files with simple grep/sed
            local json_files
            json_files=$(echo "$input" | grep -o '[^"]*\.java' | sort -u)
            if [[ -n "$json_files" ]]; then
                echo "$json_files"
                return
            fi
        fi
    fi

    # Fallback: find all Java files in current directory
    find . -name "*.java" -type f 2>/dev/null
}

# Get the Java files to format
java_files=$(get_java_files "$@")

if [[ -n "$java_files" ]]; then
    echo "Formatting Java files..." >&2

    # Verify files exist and format them
    existing_files=()
    while IFS= read -r file; do
        if [[ -f "$file" ]]; then
            existing_files+=("$file")
        else
            echo "Warning: File not found: $file" >&2
        fi
    done <<< "$java_files"

    if [[ ${#existing_files[@]} -gt 0 ]]; then
        printf '%s\n' "${existing_files[@]}" | xargs -r java -jar "$FORMATTER_JAR" --replace
        echo "Formatted ${#existing_files[@]} Java file(s)." >&2
    else
        echo "No valid Java files found to format." >&2
    fi
else
    echo "No Java files to format." >&2
fi

exit 0
