# Sub Agents

## Definition
Agents executing within other agents with restricted functionality.

## Architecture Role
Nested execution context for specialized operations. Provides limited agent capabilities within parent agent scope.

## Operations
- Execute within parent context
- Access parent state
- Limited API surface
- Synchronous execution

## Invariants
- No async API access
- No streaming capabilities
- Parent context dependency

## Key Clojure API
- Primary functions: Framework-managed
- Creation: Automatic in nested contexts
- Access: Limited agent-node operations

## Key Java API
- Primary functions: Framework-managed
- Creation: Automatic in nested contexts
- Access: Limited AgentNode operations

## Relationships
- Uses: [Agent Node](agent-node.md)
- Used by: [Tools Sub Agent](tools-sub-agent.md)

## Examples
- Clojure: Automatically created in tool execution
- Java: Automatically created in tool execution