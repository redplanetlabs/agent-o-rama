# Agent-o-rama Glossary

This glossary defines terms that have project-specific meanings in the
agent-o-rama framework, either different from or more constrained than
their general computing definitions.

## [Agent](terms/agent.md)

A distributed, stateful computational unit that executes a directed,
possibly cyclic, graph of nodes. Agents are the primary execution
entities in the system, defined using `defagentmodule` and created with
`new-agent`. Agents execute asynchronously and can maintain state
through stores, communicate via emissions between nodes, and integrate
with AI models through LangChain4j.

## [Agent Client](terms/agent-client.md)

Client-side interface for invoking and interacting with specific
agents. Obtained from an agent manager, it provides invoke, streaming,
and human input capabilities for a particular agent type.

## [Agent Complete](terms/agent-complete.md)

Final state of an agent execution indicating successful termination with result.

## [Agent Emit](terms/agent-emit.md)

The mechanism by which agent nodes send data to other nodes in the agent
graph, enabling flow control and data passing. Called via `emit!`
function with target node name and arguments, triggers execution of
downstream nodes.

## [Agent Graph](terms/agent-graph.md)

A directed graph structure that defines the execution flow of an agent,
consisting of interconnected nodes with specified output
relationships. Created via `new-agent` and built by chaining `node`
calls, the graph defines the agent's execution logic and control flow.

## [Agent Invoke](terms/agent-invoke.md)

A handle representing a specific invocation/execution instance of an
agent, used to track and interact with running agents. Created by
`agent-initiate` or `agent-invoke`, used with client functions like
`agent-result`, `agent-next-step`, and streaming operations.

## [Agent Manager](terms/agent-manager.md)

Client-side interface for managing and interacting with deployed agents
in a cluster. Created via `agent-manager` function, it provides access
to agent clients and dataset management capabilities.

## [Agent Module](terms/agent-module.md)

A complete agent system definition that packages agents, stores, and
objects into a deployable Rama module. Defined using `defagentmodule`
macro, contains agent definitions, store declarations, and agent object
builders.

## [Agent Node](terms/agent-node.md)

An individual execution unit within an agent graph that performs
specific computation and can emit to other nodes or return
results. Defined with `aor/node` function, receives an `agent-node`
parameter that provides access to stores, agent objects, and control
functions like `emit!` and `result!`.

## [Agent Node Declaration](terms/agent-node-declaration.md)

Blueprint for computation units within agent graphs, defining node name, target, and function. Forms the building blocks of agent graph topology through `aor/node` declarations that specify unique node names, target connections, and execution logic.

## [Agent Node Function](terms/agent-node-function.md)

The user-defined function that implements the logic for a specific agent
node. Receives an `agent-node` parameter for accessing framework
services and additional arguments from the node invocation or emission.
Functions can call `emit!`, `result!`, `stream-chunk!`, and other
framework operations.

## [Agent Objects](terms/agent-objects.md)

Shared resources (like AI models, databases, APIs) that agents can
access during execution. Declared via `declare-agent-object` or
`declare-agent-object-builder`, accessed via `get-agent-object` within
agent nodes.

## [Agent Result](terms/agent-result.md)

The final output value returned by an agent execution, signaling
completion of the agent graph traversal. Set via `result!` function
within agent nodes, retrieved via `agent-result` on client side.

## [Agent Step](terms/agent-step.md)

An individual execution unit returned by agent processing, can be result, human input request, or continuation.

## [Agent Trace](terms/agent-trace.md)

Execution monitoring system that captures agent node transitions and data flow for debugging.

## [Agents Topology](terms/agents-topology.md)

The top-level container for defining agents, stores, and objects within
a module. Created via `agent-topology` function, provides methods for
declaring agents and resources.

## [Agent Topology Builder](terms/agent-topology-builder.md)

The fluent interface for constructing agent definitions through method
chaining. Provides methods like `new-agent`, `node`, `agg-start-node`,
and `agg-node` to build complex agent execution graphs declaratively.

## [Aggregation](terms/aggregation.md)

A distributed computation pattern for collecting and combining results
from multiple node executions, using `agg-start-node`, `agg-node`, and
`multi-agg` constructs. Enables scatter-gather patterns, parallel
processing, and result combination across agent graph executions.

## [Cluster Manager](terms/cluster-manager.md)

An interface for managing and connecting to Rama clusters, providing access to deployed modules and their agents through connection management and factory methods.

## [Dataset](terms/dataset.md)

A managed collection of input/output examples for agent testing and
evaluation. Created and managed via agent manager dataset functions for
tracking agent performance and behavior.

## [Document Store](terms/document-store.md)

A schema-flexible store for complex nested data structures in agents.

## [Evaluators](terms/evaluators.md)

Functions for measuring agent performance against datasets.

## [Example Run](terms/example-run.md)

A single execution instance within an experiment for tracking input/output pairs.

## [Experiment](terms/experiment.md)

A structured test run comparing agent performance across datasets with specific evaluators.

## [Fork](terms/fork.md)

A mechanism to create new execution branches from existing agent
invocations with modified parameters. Used via `agent-fork` and related
async functions, enables parallel execution variants.

## [Human Input Request](terms/human-input-request.md)

A mechanism for agents to request input from human users during
execution. Created via `get-human-input` within agent nodes, handled via
client API to enable human-in-the-loop workflows.

## [IPC (In-Process Cluster)](terms/ipc.md)

A local Rama cluster instance used for development and testing. Created
via `create-ipc` for running agents in a single process without
requiring a distributed cluster setup. It is a Cluster Manager.

## [Java Interop](terms/java-interop.md)

Bidirectional integration between Clojure agent definitions and Java
client code. Agent modules can be used from Java applications, and Java
objects can be integrated into agent execution through agent objects
and LangChain4j integration.

## [Key-Value Store](terms/key-value-store.md)

A typed store for simple key-value pairs with specified key/value classes.

## [LangChain4j Integration](terms/langchain4j-integration.md)

Integration with the LangChain4j library for AI model interactions,
providing chat models, tool calling, JSON schema generation, and
structured output parsing. Enables seamless AI integration within agent
execution flows.

## Log Throttling

Rate-limiting mechanism for log messages to prevent overwhelming output
in distributed execution.

## [Multi-Agg](terms/multi-agg.md)

A flexible aggregation mechanism that allows custom combination logic
for distributed computations. Defined via `multi-agg` macro with `init`
and `on` clauses for sophisticated result aggregation patterns.

## [Node Emit](terms/node-emit.md)

The mechanism by which agent nodes send data to other nodes in the agent
graph, enabling flow control and data passing. Called via `emit!`
function with target node name and arguments, triggers execution of
downstream nodes.

## [PState Store](terms/pstate-store.md)

Persistent store backed by Rama's PState for durable data.

## [Rama](terms/rama.md)

The distributed computing platform providing stream processing,
persistent state management, and horizontal scaling infrastructure that
serves as the foundational runtime for agent-o-rama.

The underlying distributed computing platform that agent-o-rama is built
upon. Provides the distributed runtime, persistent state management,
partitioning, and scalability features that enable agents to run across
multiple machines with high performance and fault tolerance.

## [Rama Module](terms/module.md)

A deployable unit containing depots, pstates and topologies.  It can be
launched on a Rama cluster. Rama modules are identified by name and can
reference partitioned objects from other Rama modules.

## [Red Planet Labs](terms/red-planet-labs.md)

The company that created the Rama distributed computing platform and agent-o-rama framework, developing infrastructure for building real-time, scalable distributed applications.

## [Retry Mechanism](terms/retry-mechanism.md)

Automatic retry logic for handling failed or stalled agent
executions. Configured via agent options, handles execution failures
transparently to improve system resilience.

## [Streaming Chunk](terms/streaming-chunk.md)

An individual piece of streaming data emitted from an agent node during
execution. Emitted via `stream-chunk!` function within agent nodes,
received by streaming subscriptions for real-time data flow.

## [Streaming Subscription](terms/streaming-subscription.md)

A client-side subscription to receive streaming data from specific agent
nodes during execution. Created via `agent-stream`,
`agent-stream-specific`, or `agent-stream-all` functions, used to
monitor real-time outputs from agent nodes.

## [Store](terms/store.md)

A persistent storage abstraction providing typed access to distributed data within agent execution contexts, encapsulating different storage backends with uniform access patterns.

## [Sub Agents](terms/sub-agents.md)

Agents that run within other agents, but with limited functionality (no
async API or streaming). Referenced in error messages indicating
restricted capabilities for sub-agent contexts.

## [Task Global](terms/task-global.md)

Distributed state containers that manage agent execution state across
the cluster. Internal implementation detail for state management across
Rama tasks in the distributed system.

## [Tools Sub Agent](terms/tools-sub-agent.md)

A specialized agent type that executes tool functions, created via
`newToolsAgent`. Built with tool specifications and automatically
handles tool execution with aggregation patterns for AI tool calling
workflows.

## [Update Mode](terms/update-mode.md)

Controls how agent graphs handle updates (continue, restart, or
drop). Set via `set-update-mode` on agent graphs to determine behavior
when graph definitions change.


## [Tool Calling](terms/tool-calling.md)

Integration pattern for connecting AI models with external functions and
APIs. Agents can define tool specifications using LangChain4j schemas
and execute tools based on AI model decisions, enabling sophisticated
AI-driven workflows with external system integration.

## [User Interface (UI)](terms/user-interface.md)

A ClojureScript-based web interface for monitoring and visualizing
agent execution in real-time. Provides debugging tools, state
inspection, and execution flow visualization. Started via `start-ui`
function and accessible through web browser when agents are running.
