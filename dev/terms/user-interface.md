# User Interface (UI)

## Definition
ClojureScript-based web interface for monitoring and visualizing agent execution.

## Architecture Role
Real-time monitoring and debugging tool. Provides visual insight into agent state, execution flow, and performance.

## Operations
- `start-ui` - Launch web interface
- `stop-ui` - Shutdown interface
- Monitor agent traces
- Inspect state

## Invariants
- Read-only observation
- Real-time updates
- Browser-based access

## Key Clojure API
- Primary functions: `start-ui`, `stop-ui`
- Creation: `(start-ui ipc)`
- Access: Web browser at localhost:8080

## Key Java API
- Primary functions: `UI.start()`, `UI.stop()`
- Creation: Static methods
- Access: Web browser at localhost:8080

## Relationships
- Uses: [IPC](ipc.md), [Agent Trace](agent-trace.md)
- Used by: Developers and operators

## Examples
- Clojure: Available when running any agent with UI
- Java: Available when running any agent with UI