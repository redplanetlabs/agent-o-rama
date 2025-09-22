# Example Run

## Definition
Single execution instance within an experiment tracking input/output pairs.

## Architecture Role
Atomic unit of evaluation within experiments. Captures complete execution trace for performance analysis.

## Operations
- Record inputs
- Capture outputs
- Track execution time
- Store evaluation scores

## Invariants
- One input per run
- One output per run
- Immutable after completion

## Key Clojure API
- Primary functions: Framework-managed
- Creation: Automatic in experiments
- Access: Via experiment results

## Key Java API
- Primary functions: Framework-managed
- Creation: Automatic in experiments
- Access: Via experiment results

## Relationships
- Uses: [Agent Invoke](agent-invoke.md), [Dataset](dataset.md)
- Used by: [Experiment](experiment.md)

## Examples
- Clojure: Generated during evaluation runs
- Java: Generated during evaluation runs