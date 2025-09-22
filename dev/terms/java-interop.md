# Java Interop

## Definition
Bidirectional integration between Clojure agent definitions and Java client code.

## Architecture Role
Language bridge enabling Java applications to use Clojure-defined agents. Provides native Java interfaces for agent interaction.

## Operations
- Define agents in Clojure
- Access from Java clients
- Share Java objects in agents
- Seamless type conversion

## Invariants
- Type-safe interfaces
- No language-specific restrictions
- Shared object compatibility

## Key Clojure API
- Primary functions: Framework interop layer
- Creation: Via `defagentmodule`
- Access: Automatic Java interface generation

## Key Java API
- Primary functions: `AgentManager`, `AgentClient`, `AgentTopology`
- Creation: Direct class instantiation
- Access: Native Java interfaces

## Relationships
- Uses: [Agent Module](agent-module.md), [Agent Manager](agent-manager.md)
- Used by: Java applications

## Examples
- Clojure: All agent definitions usable from Java
- Java: `examples/java/basic/` and `examples/java/react/`