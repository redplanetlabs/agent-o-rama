# Dataset Example Tag

## Definition
Labels attached to dataset examples for categorization, filtering, and organization. Tags enable grouping examples by type, complexity, domain, or other characteristics for targeted evaluation.

## Architecture Role
Tags provide a flexible metadata system for organizing and filtering dataset examples. They enable selective evaluation, batch operations on example subsets, and systematic organization of test cases across different categories.

## Operations
Tags can be added to dataset examples, removed from examples, and used as filters in dataset operations. Multiple tags can be applied to single examples to support multi-dimensional categorization.

## Invariants
Tags are string identifiers that can be applied to multiple examples. Tag operations are idempotent - adding an existing tag or removing a non-existent tag has no effect. Tags persist with examples across dataset operations.

## Key Clojure API
- Primary functions: `add-dataset-example-tag!`, `remove-dataset-example-tag!`
- Creation: Applied via `add-dataset-example-tag!` with dataset, example ID, and tag string
- Access: Tags are retrieved as part of example metadata in dataset operations

## Key Java API
- Primary functions: `AgentManager.addDatasetExampleTag()`, `AgentManager.removeDatasetExampleTag()`
- Creation: String-based tags applied through `AgentManager` methods
- Access: Retrieved through dataset example metadata

## Relationships
- Uses: [dataset-example]
- Used by: [experiment], [evaluators] (for filtering)

## Dependency graph edges:
  dataset-example -> dataset-example-tag
  dataset-example-tag -> experiment
  dataset-example-tag -> evaluators

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/dataset_example_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/DatasetExampleAgent.java`
