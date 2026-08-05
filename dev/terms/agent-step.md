# Agent Step

## Definition
A single unit of step-by-step client control over a running agent invocation, returned by
`agent-next-step`. It is either a human input request or the agent's completion.

## Architecture Role
`com.rpl.agentorama.AgentStep` is an empty marker interface. Exactly two sub-interfaces extend it —
`HumanInputRequest` and `AgentComplete<T>` — and each has exactly one implementation, a record in
`com.rpl.agent-o-rama.impl.types`:

- `NodeHumanInputRequest` — implements `HumanInputRequest`; fields
  `[agent-task-id agent-id node node-task-id invoke-id prompt uuid]`
- `AgentCompleteImpl` — implements `AgentComplete`; single field `[result]`

There is no third step type, no error step, and no `:type` tag on either record.

`agent-next-step` blocks until the invocation either surfaces a human input request or
terminates. On failure it throws rather than returning a step.

## Operations
- `agent-next-step` / `agent-next-step-async` - Get the next step
- `human-input-request?` - Discriminate the two step types
- `provide-human-input` - Respond, then step again
- `pending-human-inputs` - Outstanding requests, up to 1000 (a step surfaces only one)

## Invariants
- Requires an `AgentInvoke` handle — from `agent-initiate`, `agent-initiate-with-context`, or
  `agent-initiate-fork`. `agent-invoke` returns the finished result, not a handle.
- Step records reject undeclared keys: `(:type step)` throws `NoSuchFieldError`, it does not
  return `nil`. Only the declared fields above are readable.
- `provide-human-input` must be handed the request record itself, unmodified — it is
  schema-checked as a `NodeHumanInputRequest` and cleared from the pending set by exact match
- The completion step wraps the result; `(:result step)` equals `(agent-result client invoke)`

## Key Clojure API
- Primary functions: `agent-next-step`, `agent-next-step-async`, `human-input-request?`
- Creation: `(agent-next-step client agent-invoke)`
- Access: `(:prompt step)` on a request, `(:result step)` on a completion
- Async: `agent-next-step-async` returns a `java.util.concurrent.CompletableFuture`

```clojure
(loop [step (aor/agent-next-step chat-agent inv)]
  (if (aor/human-input-request? step)
    (do
      (println (:prompt step))
      (aor/provide-human-input chat-agent step (read-line))
      (recur (aor/agent-next-step chat-agent inv)))
    (println "Final result:" (:result step))))
```

## Key Java API
- Primary functions: `AgentClient.nextStep(AgentInvoke)`, `nextStepAsync(AgentInvoke)`
- Creation: Framework-managed
- Access: narrow with `instanceof` — `HumanInputRequest.getPrompt()` / `getNode()` /
  `getNodeInvokeId()`, or `((AgentComplete<?>) step).getResult()`

```java
AgentStep step = agent.nextStep(invoke);
while (step instanceof HumanInputRequest) {
  HumanInputRequest humanInput = (HumanInputRequest) step;
  System.out.println(humanInput.getPrompt());
  agent.provideHumanInput(humanInput, scanner.nextLine());
  step = agent.nextStep(invoke);
}
```

## Relationships
- Uses: [Human Input Request](human-input-request.md), [Agent Complete](agent-complete.md)
- Used by: [Agent Client](agent-client.md), [Agent Invoke](agent-invoke.md)
- See also: [Sub Agents](sub-agents.md) — calling `agent-result` on a subagent client inside a
  node runs this same loop internally, proxying its requests to the parent agent

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/human_input_agent.clj`,
  `examples/clj/src/com/rpl/agent/simple_human_loop.clj`
- Java: `examples/java/src/main/java/com/rpl/agent/basic/HumanInputAgent.java`
- Tests: `test/clj/com/rpl/human_test.clj` (LLM-free exercise of the full protocol)
