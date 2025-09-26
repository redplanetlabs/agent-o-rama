# Evaluator

## Definition
Functions for measuring agent performance against datasets.

## Architecture Role
Quality assessment layer for agent outputs. Enables systematic
evaluation and performance tracking across multiple criteria.

## Operations
- Define evaluation metrics
- Apply to agent outputs
- Aggregate scores
- Track performance trends

## Invariants
- Deterministic scoring
- Comparable metrics
- Input/output correlation

## Key Clojure API
- Primary functions: `create-evaluator!`, `search-evaluators`,
  `remove-evaluator!`, `try-evaluator`, `try-comparative-evaluator`,
  `try-summary-evaluator`, `mk-example-run`
- Creation: User-defined functions
- Access: Via agent manager

## Key Java API
- Primary functions: `evaluate()`, `getScore()`, `tryEvaluator()`,
`tryComparativeEvaluator()`, `trySummaryEvaluator()`, `removeEvaluator()`,

- Creation: Implement evaluator interface. `CreateEvaluatorOptions`
  class with `inputJsonPath`, `outputJsonPath`, and
  `referenceOutputJsonPath` fields.
- Access: Via `AgentManager`

## Relationships
- Uses: [Dataset](dataset.md), [Agent Result](agent-result.md)
- Used by: [Experiment](experiment.md)

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/evaluator_agent.clj`
- Java: Not available in Java examples
