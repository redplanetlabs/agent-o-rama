# Agent-o-rama Concept Hierarchy

This document shows the dependency relationships between concepts in agent-o-rama, organized as a directed acyclic graph from foundational concepts (roots) to higher-level features (leaves).

## Tree Structure

```
red-planet-labs
├── rama
└── agent-o-rama

agent-module (declaration)
└── agent-topology
    └── agent-graph
        └── agent-node-declaration
            └── agent-node-function
                └── agent-node
                    └── agent-result

rama
├── cluster-manager
    ├── rama-module
    │   ├── agent-module
    │   └── module-lifecycle
    └── ipc

agent-module
└── agent-topology
    └── agent-manager
        └── agent-client
            └── agent-invoke
                ├── agent-complete
                │   └── agent-result
                │       └── agent-trace
                └── retry-mechanism
                    └── update-mode

agent-topology
└── agent-graph
    └── agent-node-declaration
        └── agent-node-function
            ├── agent-mode-emit
            ├── aggregation
            ├── multi-agg
            ├── human-input-request
            └── sub-agents

agent-object
├── agent-object-builder
├── rama-pstate
└── store
    ├── key-value-store
    ├── document-store
    └── pstate-store

streaming
├── streaming-subscription
└── streaming-chunk

langchain4j-integration
├── tool-calling
└── tools-sub-agent

experiments
└── agent-topology
    └── evaluator-builder
        ├── evaluator
        │   └── dataset
		│       ├── dataset-snapshot
		│       ├── dataset-example
		│       ├── dataset-example-tag
        │       └── experiment
        │           └── experiment-run
        └-- provided-evaluator-builders
fork

log-throttling
```

## Root Concepts

**rama** - The foundational distributed computing platform providing all infrastructure capabilities.

**red-planet-labs** - The organizational entity that creates and maintains the technology stack.

## Dependency Flow Analysis

### Foundation Layer
- **red-planet-labs**: Organization creating the technology ecosystem
- **rama**: Core distributed computing platform
- **agent-o-rama**: Agent framework built on Rama

### Agent Declaration (Chapter 1)
- **agent-module**: Initial declaration of agent system
- **agent-topology**: Container for agent definitions
- **agent-graph**: Directed execution flow structure
- **agent-node-declaration**: Blueprint for computation units
- **agent-node-function**: Function implementations
- **agent-node**: Instantiated computation unit
- **agent-result**: Output from agent execution

### Rama Infrastructure (Chapter 2)
- **cluster-manager**: Manages connections to Rama clusters
- **rama-module**: Rama's module system
- **module-lifecycle**: Module deployment and management
- **ipc**: Local development cluster implementation

### Client Interaction (Chapter 3)
- **agent-module**: Client-facing module interface
- **agent-topology**: Runtime topology access
- **agent-manager**: Central client interface
- **agent-client**: Interface for specific agents
- **agent-invoke**: Handle for individual executions
- **agent-complete**: Execution completion handling
- **agent-trace**: Execution history and debugging
- **retry-mechanism**: Automatic failure recovery
- **update-mode**: State update strategies

### Agent Execution (Chapter 4)
- **agent-topology**: Execution context
- **agent-graph**: Flow control structure
- **agent-node-declaration**: Node templates
- **agent-node-function**: Execution logic
- **agent-mode-emit**: Output emission modes
- **aggregation**: Result aggregation
- **multi-agg**: Multiple aggregation patterns
- **human-input-request**: Human-in-the-loop workflows
- **sub-agents**: Nested agent execution

### Storage and Objects (Chapter 5)
- **agent-object**: Shared resources container
- **agent-object-builder**: Resource construction
- **rama-pstate**: Rama persistent state
- **store**: Base storage abstraction
- **key-value-store**: Simple typed pairs
- **document-store**: Nested structures
- **pstate-store**: Rama-backed storage

### Streaming (Chapter 6)
- **streaming-subscription**: Client-side receivers
- **streaming-chunk**: Partial result delivery

### AI Integration (Chapter 7)
- **langchain4j-integration**: AI model library
- **tool-calling**: External function execution
- **tools-sub-agent**: Tool execution agents

### Advanced (Chapter 8)
- **update-mode**: Update Mode
- **define-agents**: define agents in a rama module
- **underlying-stream-topology**: extend the agent streaming topology

### Experimentation (Chapter 9)
- **experiments**: Evaluation framework
- **evaluator-builder**: Metric construction
- **evaluator**: Performance measurement
- **dataset**: Input/output collections
- **dataset-snapshot**: Input/output collections
- **dataset-example**: An example in a dataset
- **dataset-example-tag**: Tags on dataset examples
- **experiment**: Test execution
- **experiment-run**: Individual test runs
- **provided-evaluator-builders**: Provided evaluator builders

### Additional Features
- **fork**: Parallel execution branching
- **log-throttling**: Log rate limiting

## Composite Concepts

**agent** - The main abstraction that composes multiple branches:
- Uses agent-node, agent-graph, store, agent-objects
- Accessed through agent-client and agent-manager
- Evaluated by experiment framework

## Notes

- chapter 2 logically comes before chapter 1, but we want to start
  exploring agents before jumping into rama

- Dependencies flow from leaves → roots (higher-level concepts depend on lower-level ones)
- Some concepts (like `pstate-store`, `agent-result`) appear in multiple branches as shared dependencies
- The `agent` concept is a major composition point that brings together multiple hierarchy branches
- Cycles are avoided by having shared dependencies rather than circular references
