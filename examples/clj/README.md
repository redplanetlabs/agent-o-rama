# Agent-O-Rama Examples

This directory contains example implementations of AI agents using the agent-o-rama framework.

## Examples
- `react.clj` - ReAct pattern implementation with tool calling
- `chatbot.clj` - Basic chatbot example
- `research_agent.clj` - Multi-step research and analysis agent
- `todo.clj` - Todo management with long-term memory
- `customer_support.clj` - Customer support agent

## Running Examples

To run an example:

```bash
lein repl
```

Then in the REPL:
```clojure
;; For customer support agent
(require '[com.rpl.agent.customer-support :as cs])
(cs/run-customer-support-agent)

;; For other examples, see their respective run functions
```

## Testing

The project includes tests that don't require API keys to validate core functionality:

```bash
# Run all tests
lein test

# Run specific test suites
lein test com.rpl.agent.customer-support-test
```

### Test Structure

- **Customer Support Tests** (`customer_support_test.clj`) - Tests for mock data and tool functions

## Dependencies

The examples use:
- `agent-o-rama` - Core agent framework
- `langchain4j` - LLM integration
- `jsonista` - JSON handling
- `http-kit` - HTTP operations

## Development

The project is configured with reflection warnings enabled. To check for warnings:

```bash
lein compile
```

For a clean build:

```bash
lein clean
lein compile
```
