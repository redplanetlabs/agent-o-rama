# Agent-o-rama Simple Examples

This directory contains isolated examples demonstrating individual agent-o-rama features. Each example focuses on a specific feature or small set of related features, progressing from basic concepts to advanced patterns.

## Example Progression

### Foundation Examples
1. **`basic_agent`** - Agent definition, single node, sync invocation
2. **`multi_node_agent`** - Agent graph with multiple nodes and emissions  
3. **`async_agent`** - Asynchronous initiation and result handling

### State Management Examples
4. **`agent_objects_agent`** - Static and builder-based agent objects
5. **`keyvalue_store_agent`** - Key-value store operations
6. **`document_store_agent`** - Document store with field operations
7. **`pstate_store_agent`** - PState store with path operations

### Communication Examples
8. **`streaming_agent`** - Stream chunks from nodes
9. **`human_input_agent`** - Request and handle human input

### Advanced Patterns
10. **`aggregation_agent`** - Fan-out/fan-in with agg-start-node and agg-node
11. **`multi_agg_agent`** - Custom aggregation logic with multi-agg
12. **`forking_agent`** - Agent execution branching
13. **`tools_agent`** - LangChain4j tools integration

### System Features
14. **`dataset_agent`** - Dataset creation and management
15. **`evaluator_agent`** - Evaluator creation and execution
16. **`langchain4j_agent`** - LangChain4j chat model integration
17. **`ui_monitoring_agent`** - UI integration for monitoring
18. **`cluster_agent`** - Cross-module agent communication

## Running Examples

Each example is a self-contained namespace with a `-main` function. 

### From the simple-examples/clj directory:

```bash
# Run specific example
clj -M -m com.rpl.agent.basic-agent
clj -M -m com.rpl.agent.multi-node-agent
clj -M -m com.rpl.agent.async-agent
clj -M -m com.rpl.agent.agent-objects-agent
clj -M -m com.rpl.agent.keyvalue-store-agent
clj -M -m com.rpl.agent.document-store-agent
clj -M -m com.rpl.agent.pstate-store-agent

# Run tests
clj -M:test
```

### From the main project directory (alternative):

```bash
# Run specific example
lein with-profile +dev run -m com.rpl.agent.basic-agent
lein with-profile +dev run -m com.rpl.agent.multi-node-agent
lein with-profile +dev run -m com.rpl.agent.async-agent
lein with-profile +dev run -m com.rpl.agent.agent-objects-agent
lein with-profile +dev run -m com.rpl.agent.keyvalue-store-agent
lein with-profile +dev run -m com.rpl.agent.document-store-agent
lein with-profile +dev run -m com.rpl.agent.pstate-store-agent

# Or from REPL
lein with-profile +dev repl
(require '[com.rpl.agent.basic-agent :as basic])
(basic/-main)
```

**Note**: Examples may take several minutes to start up due to Rama initialization.

## Feature Dependencies

Examples are ordered to build understanding progressively:

- **Foundation** (1-3): Core agent system required for all other examples
- **State Management** (4-7): Independent storage and resource patterns
- **Communication** (8-9): Real-time interaction patterns
- **Advanced Patterns** (10-13): Complex execution and integration patterns  
- **System Features** (14-18): Full-system capabilities and cross-module features

Each example includes detailed comments explaining the demonstrated features and their usage patterns.