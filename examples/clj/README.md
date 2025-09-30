# Agent-O-Rama Examples

This directory contains example implementations of AI agents using the agent-o-rama framework.

## Example Types

### App based Examples

Real-world application scenarios demonstrating multiple features working
together:

- `react.clj` - ReAct pattern implementation with tool calling
- `chatbot.clj` - Basic chatbot example
- `research_agent.clj` - Multi-step research and analysis agent
- `todo.clj` - Todo management with long-term memory
- `customer_support.clj` - Customer support agent

### Basic Examples (`com.rpl.agent.basic.*`)

Isolated examples demonstrating individual agent-o-rama features. Each
example focuses on a specific feature or small set of related features,
progressing from basic concepts to advanced patterns.

#### Foundation Examples
1. **`basic_agent`** - Agent definition, single node, sync invocation
2. **`multi_node_agent`** - Agent graph with multiple nodes and emissions
3. **`router_agent`** - Conditional routing between different processing nodes
4. **`async_agent`** - Asynchronous initiation and result handling
5. **`agent_objects_agent`** - Static and builder-based agent objects
6. **`langchain4j_agent`** - LangChain4j chat model integration

#### State Management Examples
7. **`keyvalue_store_agent`** - Key-value store operations
8. **`document_store_agent`** - Document store with field operations
9. **`pstate_store_agent`** - PState store with path operations

#### Communication Examples
10. **`streaming_agent`** - Stream chunks from nodes
11. **`stream_all_agent`** - Subscribe to streaming from multiple invocations
12. **`stream_reset_agent`** - Stream reset behavior on node retry after failure
13. **`human_input_agent`** - Request and handle human input
14. **`record_op_agent`** - Record custom operations in agent traces for debugging

#### Advanced Patterns
15. **`aggregation_agent`** - Fan-out/fan-in with agg-start-node and agg-node
16. **`multi_agg_agent`** - Custom aggregation logic with multi-agg
17. **`structured_langchain4j_agent`** - JSON structured output with LangChain4j
18. **`streaming_langchain4j_agent`** - Real-time streaming with LangChain4j models
19. **`tools_agent`** - LangChain4j tools integration

#### System Features
20. **`forking_agent`** - Agent execution branching
21. **`dataset_agent`** - Dataset lifecycle management (create, update, snapshots, destroy)
22. **`dataset_example_agent`** - Dataset example management (add, update, tag, remove examples)
23. **`evaluator_agent`** - Evaluator creation and execution
24. **`provided_evaluator_builders_agent`** - Built-in evaluator builders (aor/llm-judge, aor/conciseness, aor/f1-score)
25. **`module_update_agent`** - Module updates with aor/set-update-mode, IPC deployment and update
26. **`mirror_agent`** - Cross-module agent invocation using declare-cluster-agent
27. **`rama_module_agent`** - Direct Rama module usage (not defagentmodule), depot integration, manual topology creation

## Running Examples

### App based Examples

To run a complex example:

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

### Basic Examples

Each basic example is a self-contained namespace with a `-main` function:

```bash
# Run specific basic example
lein run -m com.rpl.agent.basic.basic-agent
lein run -m com.rpl.agent.basic.multi-node-agent
lein run -m com.rpl.agent.basic.async-agent
lein run -m com.rpl.agent.basic.router-agent
lein run -m com.rpl.agent.basic.agent-objects-agent
lein run -m com.rpl.agent.basic.keyvalue-store-agent
lein run -m com.rpl.agent.basic.document-store-agent
lein run -m com.rpl.agent.basic.pstate-store-agent
lein run -m com.rpl.agent.basic.streaming-agent
lein run -m com.rpl.agent.basic.stream-all-agent
lein run -m com.rpl.agent.basic.stream-reset-agent
lein run -m com.rpl.agent.basic.human-input-agent
lein run -m com.rpl.agent.basic.record-op-agent
lein run -m com.rpl.agent.basic.aggregation-agent
lein run -m com.rpl.agent.basic.multi-agg-agent
lein run -m com.rpl.agent.basic.structured-langchain4j-agent
lein run -m com.rpl.agent.basic.streaming-langchain4j-agent
lein run -m com.rpl.agent.basic.tools-agent
lein run -m com.rpl.agent.basic.forking-agent
lein run -m com.rpl.agent.basic.dataset-agent
lein run -m com.rpl.agent.basic.dataset-example-agent
lein run -m com.rpl.agent.basic.evaluator-agent
lein run -m com.rpl.agent.basic.provided-evaluator-builders-agent
lein run -m com.rpl.agent.basic.langchain4j-agent
lein run -m com.rpl.agent.basic.module-update-agent
lein run -m com.rpl.agent.basic.mirror-agent
lein run -m com.rpl.agent.basic.rama-module-agent
```

Or from REPL:
```clojure
lein with-profile +dev repl
(require '[com.rpl.agent.basic.basic-agent :as basic])
(basic/-main)
```

**Note**: Examples may take several minutes to start up due to Rama initialization.

### Feature Dependencies

Basic examples are ordered to build understanding progressively:

- **Foundation** (1-6): Core agent system required for all other examples
- **State Management** (7-9): Independent storage and resource patterns
- **Communication** (10-14): Real-time interaction patterns
- **Advanced Patterns** (15-19): Complex execution and integration patterns
- **System Features** (20-26): Full-system capabilities

Each example includes detailed comments explaining the demonstrated
features and their usage patterns.

## Testing

The project includes tests that don't require API keys to validate core
functionality:

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

The project is configured with reflection warnings enabled. To check for
warnings:

```bash
lein compile
```

For a clean build:

```bash
lein clean
lein compile
```
