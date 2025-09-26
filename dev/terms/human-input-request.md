# Human Input Request

## Definition
Suspension point where agents request input from human users.

## Architecture Role
Enables human-in-the-loop workflows. Pauses execution pending user response, maintaining state across interaction.

## Operations
- `get-human-input` - Request input
- `provide-human-input` - Supply response
- `pending-human-inputs` - List requests
- `human-input-request?` - Check type

## Invariants
- Execution suspends until response
- State preserved during wait
- One response per request

## Key Clojure API
- Primary functions: `get-human-input`, `provide-human-input`,
  `pending-human-inputs`, `pending-human-inputs-async`,
  `provide-human-input-async`
- Creation: `(get-human-input agent-node "prompt")`
- Access: Via client operations

## Key Java API
- Primary functions: `getPrompt()`, `getNode()`, `provideHumanInput()`
- Creation: Framework-managed
- Access: `HumanInputRequest` class

## Relationships
- Uses: [Agent Node](agent-node.md)
- Used by: [Agent Client](agent-client.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/human_input_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/HumanInputAgent.java`
