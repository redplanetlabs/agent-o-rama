# Update Mode

## Definition
Configuration controlling agent graph behavior during module updates.

## Architecture Role
Control of executing agent invocation behavior on module
update. Determines how running agents handle graph definition changes.

## Operations
- `set-update-mode` - Configure behavior
- CONTINUE - Suspend invocation on old graph, and continue it on new graph
- RESTART - Restart invocation with new graph
- DROP - Terminate and drop invocation

## Invariants
- Set per agent graph
- Affects all instances
- Immutable during execution

## Key Clojure API
- Primary functions: `set-update-mode`
- Creation: `(set-update-mode graph :restart)`
- Access: Via agent graph builder

## Key Java API
- Primary functions: `setUpdateMode()`
- Creation: `graph.setUpdateMode(UpdateMode.RESTART)`
- Access: `UpdateMode` enum

## Relationships
- Uses: [Agent Graph](agent-graph.md)
- Used by: Graph deployment

## Examples
- Clojure: Used in development for hot reloading
- Java: Used in development for hot reloading
