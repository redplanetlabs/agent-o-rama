# IPC (In-Process Cluster)

## Definition
Local Rama cluster instance for development and testing.

## Architecture Role
Development runtime enabling single-process agent execution. Simulates distributed cluster without network complexity.

## Operations
- `create-ipc` - Create cluster
- Deploy modules locally
- Start UI for monitoring
- Test agent behavior

## Invariants
- Single-process execution
- Full API compatibility
- Development-only usage

## Key Clojure API
- Primary functions: `create-ipc`
- Creation: `(create-ipc)`
- Access: Direct instantiation

## Key Java API
- Primary functions: `InProcessCluster.create()`
- Creation: Static factory method
- Access: `InProcessCluster` class

## Relationships
- Uses: [Rama](rama.md)
- Used by: [Agent Manager](agent-manager.md), [User Interface](user-interface.md)

## Examples
- Clojure: All basic examples use IPC
- Java: All basic examples use IPC
