# Task Global

## Definition
Distributed state containers managing agent execution state across cluster.

## Architecture Role
Internal state management for distributed agent execution. Coordinates execution state across Rama tasks.

## Operations
- Maintain execution state
- Coordinate across tasks
- Handle state transitions
- Manage lifecycle

## Invariants
- Internal implementation detail
- Distributed consistency
- Transparent to users

## Key Clojure API
- Primary functions: Framework-internal
- Creation: Automatic
- Access: Not user-accessible

## Key Java API
- Primary functions: Framework-internal
- Creation: Automatic
- Access: Not user-accessible

## Relationships
- Uses: [Rama](rama.md)
- Used by: Framework internals

## Examples
- Clojure: Internal to all agent executions
- Java: Internal to all agent executions
