# UI

## Definition
Web-based monitoring and visualization interface for agent execution,
providing real-time inspection of agent state, execution flow, and
debugging capabilities. A ClojureScript-based dashboard for development
and operational monitoring.

## Architecture Role
The UI serves as the primary observability interface for the
agent-o-rama framework, enabling developers and operators to monitor
agent execution, inspect state changes, and debug complex agent
workflows in real-time.

## Operations
The UI can be started and stopped, provides real-time agent execution
monitoring, displays agent state and store contents, shows execution
flow visualization, and offers debugging tools for agent development and
troubleshooting.

## Invariants
The UI operates independently of agent execution and does not affect
agent behavior. UI state reflects the current agent system state without
interference. The interface maintains responsive updates during active
agent execution.

## Key Clojure API
- Primary functions: `start-ui`, `stop-ui`
- Creation: `start-ui` with IPC or cluster connection
- Access: Web interface accessible via HTTP

## Key Java API
- Creation: `UI.create()` with `UIOptions` for port.
- Access: Web interface accessible via HTTP

## Relationships
- Uses: `ipc`, `cluster-manager`, `agent-manager`
- Used by: Development and operational monitoring workflows

## Dependency graph edges:
  ipc -> ui
  cluster-manager -> ui
  agent-manager -> ui
  ui -> monitoring-workflows

## Examples
- Clojure: `(aor/start-ui ipc)` in example files
- Java: UI initialization in cluster setup examples
