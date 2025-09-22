# Agent-o-rama Concept Hierarchy

This document shows the dependency relationships between concepts in agent-o-rama, organized as a directed acyclic graph from foundational concepts (roots) to higher-level features (leaves).

## Tree Structure

```
rama/
├── rama-platform/
│   ├── pstate-store
│   ├── task-global
│   └── ipc/
│       ├── cluster-manager/
│       │   └── agent-manager/
│       │       ├── agent-client/
│       │       │   ├── agent-invoke/
│       │       │   │   ├── fork
│       │       │   │   ├── agent-complete/
│       │       │   │   │   └── agent-result
│       │       │   │   ├── streaming-subscription/
│       │       │   │   │   └── streaming-chunk
│       │       │   │   ├── retry-mechanism
│       │       │   │   └── example-run/
│       │       │   │       └── experiment/
│       │       │   │           └── evaluators
│       │       │   ├── human-input-request
│       │       │   └── java-interop
│       │       └── dataset
│       └── user-interface/
│           └── agent-trace
├── agent-module/
│   └── agents-topology/
│       ├── store/
│       │   ├── key-value-store/
│       │   │   └── document-store
│       │   └── pstate-store (shared)
│       ├── agent-objects/
│       │   └── langchain4j-integration/
│       │       └── tool-calling/
│       │           └── tools-sub-agent/
│       │               └── sub-agents
│       └── agent-topology-builder/
│           └── agent-graph/
│               ├── agent-node/
│               │   ├── agent-node-function/
│               │   │   ├── agent-emit
│               │   │   ├── node-emit (alias)
│               │   │   └── agent-result (shared)
│               │   ├── streaming-chunk (shared)
│               │   ├── human-input-request (shared)
│               │   └── agent-throttling
│               ├── aggregation/
│               │   └── multi-agg
│               └── update-mode
└── agent (composed concept using multiple branches)

red-planet-labs/
└── (creates rama and agent-o-rama ecosystem)
```

## Root Concepts

**rama** - The foundational distributed computing platform providing all infrastructure capabilities.

**red-planet-labs** - The organizational entity that creates and maintains the technology stack.

## Dependency Flow Analysis

### Infrastructure Layer (Built on Rama)
- **rama-platform**: Direct abstraction over Rama capabilities
- **ipc**: Local development cluster implementation
- **pstate-store**: Persistent state management
- **task-global**: Distributed state containers

### Client Access Layer
- **cluster-manager**: Manages connections to Rama clusters
- **agent-manager**: Central client interface for deployed modules
- **agent-client**: Interface for specific agent interactions
- **agent-invoke**: Handle for individual agent executions

### Agent Definition Layer
- **agent-module**: Deployable agent system package
- **agents-topology**: Container for agent definitions
- **agent-graph**: Directed execution flow structure
- **agent-node**: Individual computation units

### Storage Abstractions
- **store**: Base persistent storage abstraction
- **key-value-store**: Simple typed key-value pairs
- **document-store**: Schema-flexible nested structures (builds on key-value)
- **pstate-store**: Rama-backed durable storage

### AI Integration
- **agent-objects**: Shared resources like AI models
- **langchain4j-integration**: AI model interaction library
- **tool-calling**: External function execution
- **tools-sub-agent**: Specialized tool execution agent

### Monitoring and Analysis
- **dataset**: Collections of input/output examples
- **evaluators**: Performance measurement functions
- **experiment**: Systematic evaluation framework
- **user-interface**: Real-time monitoring and visualization

### Execution Features
- **streaming-chunk**: Real-time partial results
- **streaming-subscription**: Client-side streaming receivers
- **human-input-request**: Human-in-the-loop workflows
- **fork**: Parallel execution branching
- **retry-mechanism**: Automatic failure recovery

## Composite Concepts

**agent** - The main abstraction that composes multiple branches:
- Uses agent-node, agent-graph, store, agent-objects
- Accessed through agent-client and agent-manager
- Evaluated by experiment framework

## Notes

- Dependencies flow from leaves → roots (higher-level concepts depend on lower-level ones)
- Some concepts (like `pstate-store`, `agent-result`) appear in multiple branches as shared dependencies
- The `agent` concept is a major composition point that brings together multiple hierarchy branches
- Cycles are avoided by having shared dependencies rather than circular references