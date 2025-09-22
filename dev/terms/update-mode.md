# Update Mode

## Definition
Configuration controlling agent graph behavior during definition updates.

## Architecture Role
Version control for agent graphs. Determines how running agents handle graph definition changes.

## Operations
- `set-update-mode` - Configure behavior
- CONTINUE - Keep running with old graph
- RESTART - Restart with new graph
- DROP - Terminate execution

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