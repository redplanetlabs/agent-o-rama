# Agent-o-rama Simple Examples

This directory contains isolated examples demonstrating individual
agent-o-rama features. Each example focuses on a specific feature or
small set of related features, progressing from basic concepts to
advanced patterns.

## Example Progression

### Foundation Examples
1. **`basic_agent`** - Agent definition, single node, sync invocation
2. **`multi_node_agent`** - Agent graph with multiple nodes and emissions
3. **`router_agent`** - Conditional routing between different processing nodes
4. **`async_agent`** - Asynchronous initiation and result handling
5. **`agent_objects_agent`** - Static and builder-based agent objects
6. **`langchain4j_agent`** - LangChain4j chat model integration

### State Management Examples
7. **`keyvalue_store_agent`** - Key-value store operations
8. **`document_store_agent`** - Document store with field operations
9. **`pstate_store_agent`** - PState store with path operations

### Communication Examples
10. **`streaming_agent`** - Stream chunks from nodes
11. **`human_input_agent`** - Request and handle human input

### Advanced Patterns
12. **`aggregation_agent`** - Fan-out/fan-in with agg-start-node and agg-node
13. **`multi_agg_agent`** - Custom aggregation logic with multi-agg
14. **`structured_langchain4j_agent`** - JSON structured output with LangChain4j
15. **`streaming_langchain4j_agent`** - Real-time streaming with LangChain4j models
16. **`tools_agent`** - LangChain4j tools integration

### System Features
17. **`forking_agent`** - Agent execution branching
18. **`dataset_agent`** - Dataset creation and management
19. **`evaluator_agent`** - Evaluator creation and execution
20. **`ui_monitoring_agent`** - UI integration for monitoring
21. **`cluster_agent`** - Cross-module agent communication

## Running Examples

Each example is a self-contained namespace with a `-main` function.

### From the simple-examples/clj directory:

```bash
# Run specific example
clj -M -m com.rpl.agent.basic-agent
clj -M -m com.rpl.agent.multi-node-agent
clj -M -m com.rpl.agent.async-agent
clj -M -m com.rpl.agent.router-agent
clj -M -m com.rpl.agent.agent-objects-agent
clj -M -m com.rpl.agent.keyvalue-store-agent
clj -M -m com.rpl.agent.document-store-agent
clj -M -m com.rpl.agent.pstate-store-agent
clj -M -m com.rpl.agent.streaming-agent
clj -M -m com.rpl.agent.human-input-agent
clj -M -m com.rpl.agent.aggregation-agent
clj -M -m com.rpl.agent.multi-agg-agent
clj -M -m com.rpl.agent.structured-langchain4j-agent
clj -M -m com.rpl.agent.streaming-langchain4j-agent
clj -M -m com.rpl.agent.tools-agent
clj -M -m com.rpl.agent.forking-agent
clj -M -m com.rpl.agent.dataset-agent
clj -M -m com.rpl.agent.evaluator-agent
clj -M -m com.rpl.agent.langchain4j-agent
clj -M -m com.rpl.agent.ui-monitoring-agent
clj -M -m com.rpl.agent.cluster-agent

# Run tests
clj -M:test
```

# Or from REPL
lein with-profile +dev repl
(require '[com.rpl.agent.basic-agent :as basic])
(basic/-main)
```

**Note**: Examples may take several minutes to start up due to Rama initialization.

## Feature Dependencies

Examples are ordered to build understanding progressively:

- **Foundation** (1-6): Core agent system required for all other examples
- **State Management** (7-9): Independent storage and resource patterns
- **Communication** (10-11): Real-time interaction patterns
- **Advanced Patterns** (12-16): Complex execution and integration patterns
- **System Features** (17-21): Full-system capabilities and cross-module features

Each example includes detailed comments explaining the demonstrated
features and their usage patterns.
