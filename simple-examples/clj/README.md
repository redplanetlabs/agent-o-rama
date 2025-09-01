# Agent-o-rama Simple Examples

This directory contains isolated examples demonstrating individual agent-o-rama features. Each example focuses on a specific feature or small set of related features, progressing from basic concepts to advanced patterns.

## Example Progression

### Foundation Examples
1. **`basic_agent`** - Agent definition, single node, sync invocation
2. **`multi_node_agent`** - Agent graph with multiple nodes and emissions
3. **`router_agent`** - Conditional routing between different processing nodes
4. **`async_agent`** - Asynchronous initiation and result handling
5. **`agent_objects_agent`** - Static and builder-based agent objects

### State Management Examples
6. **`keyvalue_store_agent`** - Key-value store operations
7. **`document_store_agent`** - Document store with field operations
8. **`pstate_store_agent`** - PState store with path operations

### Communication Examples
9. **`streaming_agent`** - Stream chunks from nodes
10. **`human_input_agent`** - Request and handle human input

### Advanced Patterns
11. **`aggregation_agent`** - Fan-out/fan-in with agg-start-node and agg-node
12. **`multi_agg_agent`** - Custom aggregation logic with multi-agg
13. **`forking_agent`** - Agent execution branching
14. **`tools_agent`** - LangChain4j tools integration

### System Features
15. **`dataset_agent`** - Dataset creation and management
16. **`evaluator_agent`** - Evaluator creation and execution
17. **`langchain4j_agent`** - LangChain4j chat model integration
18. **`ui_monitoring_agent`** - UI integration for monitoring
19. **`cluster_agent`** - Cross-module agent communication

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
clj -M -m com.rpl.agent.forking-agent
clj -M -m com.rpl.agent.tools-agent
clj -M -m com.rpl.agent.dataset-agent
clj -M -m com.rpl.agent.evaluator-agent
clj -M -m com.rpl.agent.langchain4j-agent
clj -M -m com.rpl.agent.ui-monitoring-agent
clj -M -m com.rpl.agent.cluster-agent

# Run tests
clj -M:test
```

### From the main project directory (alternative):

```bash
# Run specific example
lein with-profile +dev run -m com.rpl.agent.basic-agent
lein with-profile +dev run -m com.rpl.agent.multi-node-agent
lein with-profile +dev run -m com.rpl.agent.async-agent
lein with-profile +dev run -m com.rpl.agent.router-agent
lein with-profile +dev run -m com.rpl.agent.agent-objects-agent
lein with-profile +dev run -m com.rpl.agent.keyvalue-store-agent
lein with-profile +dev run -m com.rpl.agent.document-store-agent
lein with-profile +dev run -m com.rpl.agent.pstate-store-agent
lein with-profile +dev run -m com.rpl.agent.streaming-agent
lein with-profile +dev run -m com.rpl.agent.human-input-agent
lein with-profile +dev run -m com.rpl.agent.aggregation-agent
lein with-profile +dev run -m com.rpl.agent.multi-agg-agent
lein with-profile +dev run -m com.rpl.agent.forking-agent
lein with-profile +dev run -m com.rpl.agent.tools-agent
lein with-profile +dev run -m com.rpl.agent.dataset-agent
lein with-profile +dev run -m com.rpl.agent.evaluator-agent
lein with-profile +dev run -m com.rpl.agent.langchain4j-agent
lein with-profile +dev run -m com.rpl.agent.ui-monitoring-agent
lein with-profile +dev run -m com.rpl.agent.cluster-agent

# Or from REPL
lein with-profile +dev repl
(require '[com.rpl.agent.basic-agent :as basic])
(basic/-main)
```

**Note**: Examples may take several minutes to start up due to Rama initialization.

## Feature Dependencies

Examples are ordered to build understanding progressively:

- **Foundation** (1-4): Core agent system required for all other examples
- **State Management** (5-8): Independent storage and resource patterns
- **Communication** (9-10): Real-time interaction patterns
- **Advanced Patterns** (11-14): Complex execution and integration patterns
- **System Features** (15-19): Full-system capabilities and cross-module features

Each example includes detailed comments explaining the demonstrated features and their usage patterns.
