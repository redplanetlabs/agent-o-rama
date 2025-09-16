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

## Agent Client

Client-side interface for invoking and interacting with specific
agents. Obtained from an agent manager, it provides invoke, streaming,
and human input capabilities for a particular agent type.

## Agent Complete

Final state of an agent execution indicating successful termination with result.

## Agent Graph

A directed graph structure that defines the execution flow of an agent,
consisting of interconnected nodes with specified output
relationships. Created via `new-agent` and built by chaining `node`
calls, the graph defines the agent's execution logic and control flow.

## Agent Invoke

A handle representing a specific invocation/execution instance of an
agent, used to track and interact with running agents. Created by
`agent-initiate` or `agent-invoke`, used with client functions like
`agent-result`, `agent-next-step`, and streaming operations.

## Agent Manager

Client-side interface for managing and interacting with deployed agents
in a cluster. Created via `agent-manager` function, it provides access
to agent clients and dataset management capabilities.

## Agent Module

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

## [Agent Objects](terms/agent-objects.md)

Shared resources (like AI models, databases, APIs) that agents can
access during execution. Declared via `declare-agent-object` or
`declare-agent-object-builder`, accessed via `get-agent-object` within
agent nodes.

## Agent Result

The final output value returned by an agent execution, signaling
completion of the agent graph traversal. Set via `result!` function
within agent nodes, retrieved via `agent-result` on client side.

## [Agent Step](terms/agent-step.md)

An individual execution unit returned by agent processing, can be result, human input request, or continuation.

## Agent Throttling

Rate-limiting mechanism for log messages to prevent overwhelming output in distributed execution.

## Agent Trace

Execution monitoring system that captures agent node transitions and data flow for debugging.

## Agents Topology

The top-level container for defining agents, stores, and objects within
a module. Created via `agents-topology` function, provides methods for
declaring agents and resources.

## [Aggregation](terms/aggregation.md)

A distributed computation pattern for collecting and combining results
from multiple node executions, using `agg-start-node`, `agg-node`, and
`multi-agg` constructs. Enables scatter-gather patterns, parallel
processing, and result combination across agent graph executions.

## [Dataset](terms/dataset.md)

A managed collection of input/output examples for agent testing and
evaluation. Created and managed via agent manager dataset functions for
tracking agent performance and behavior.

## Document Store

A schema-flexible store for complex nested data structures in agents.

## Evaluators

Functions for measuring agent performance against datasets.

## Example Run

A single execution instance within an experiment for tracking input/output pairs.

## [Experiment](terms/experiment.md)

A structured test run comparing agent performance across datasets with specific evaluators.

## Fork

A mechanism to create new execution branches from existing agent
invocations with modified parameters. Used via `agent-fork` and related
async functions, enables parallel execution variants.

## Human Input Request

A mechanism for agents to request input from human users during
execution. Created via `get-human-input` within agent nodes, handled via
client API to enable human-in-the-loop workflows.

## [Key-Value Store](terms/key-value-store.md)

A typed store for simple key-value pairs with specified key/value classes.

## Multi-Agg

A flexible aggregation mechanism that allows custom combination logic
for distributed computations. Defined via `multi-agg` macro with `init`
and `on` clauses for sophisticated result aggregation patterns.

## PState Store

A persistent state store backed by Rama's PState for durable agent data.

## Node Emit

The mechanism by which agent nodes send data to other nodes in the agent
graph, enabling flow control and data passing. Called via `emit!`
function with target node name and arguments, triggers execution of
downstream nodes.

## Retry Mechanism

Automatic retry logic for handling failed or stalled agent
executions. Configured via agent options, handles execution failures
transparently to improve system resilience.

## Streaming Chunk

An individual piece of streaming data emitted from an agent node during
execution. Emitted via `stream-chunk!` function within agent nodes,
received by streaming subscriptions for real-time data flow.

## Streaming Subscription

A client-side subscription to receive streaming data from specific agent
nodes during execution. Created via `agent-stream`,
`agent-stream-specific`, or `agent-stream-all` functions, used to
monitor real-time outputs from agent nodes.

## Sub Agents

Agents that run within other agents, but with limited functionality (no
async API or streaming). Referenced in error messages indicating
restricted capabilities for sub-agent contexts.

## Task Global

Distributed state containers that manage agent execution state across
the cluster. Internal implementation detail for state management across
Rama tasks in the distributed system.

## Tools Sub Agent

A specialized agent type that executes tool functions, created via
`newToolsAgent`. Built with tool specifications and automatically
handles tool execution with aggregation patterns for AI tool calling
workflows.

## Update Mode

Controls how agent graphs handle updates (continue, restart, or
drop). Set via `set-update-mode` on agent graphs to determine behavior
when graph definitions change.
