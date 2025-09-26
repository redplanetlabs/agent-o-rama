# Recursive Agents

## Definition
Agents that can invoke themselves or other agents in mutual recursion patterns, enabling complex hierarchical processing and self-referential workflows. Supports both direct self-invocation and indirect recursive patterns through multiple agents.

## Architecture Role
Recursive agents enable sophisticated computational patterns like tree traversal, hierarchical decomposition, and iterative refinement workflows. They provide the foundation for agents that need to break down complex problems into smaller, similar subproblems.

## Operations
Recursive agents can invoke themselves with modified parameters, call other agents that may recursively call back, and manage execution depth and termination conditions. The framework handles recursive invocation management and prevents infinite recursion through built-in safeguards.

## Invariants
Recursive invocations maintain separate execution contexts to prevent state interference. Each recursive call has independent agent invoke handles and state isolation. The framework provides termination guarantees to prevent infinite recursion.

## Key Clojure API
- Primary functions: `agent-invoke`, `agent-initiate` (within agent nodes)
- Creation: Standard agent definition with self-referential invocation logic
- Access: Through `agent-client` from within agent node functions

## Key Java API
- Primary functions: `AgentClient.invoke()`, `AgentClient.initiate()` (from node functions)
- Creation: Standard agent definition with recursive invocation patterns
- Access: Through `AgentClient` instances within node implementations

## Relationships
- Uses: [agent], [agent-client], [agent-invoke]
- Used by: Complex workflow patterns and hierarchical processing systems

## Dependency graph edges:
  agent -> recursive-agents
  agent-client -> recursive-agents
  agent-invoke -> recursive-agents
  recursive-agents -> complex-workflows

## Examples
- Clojure: Tree processing and hierarchical decomposition examples
- Java: Recursive data structure processing examples