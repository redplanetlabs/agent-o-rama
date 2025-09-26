# Dataset Example

## Definition
A single input-output pair within a dataset used for testing and
evaluation. Dataset examples contain inputs provided to an agent,
expected output, actual output, and optional metadata like tags and
descriptions.

## Architecture Role
Dataset examples serve as the atomic units of agent evaluation,
providing test cases for measuring agent performance and behavior. Each
example captures a specific scenario with defined inputs and expected
outcomes for systematic testing.

## Operations
Examples can be added to datasets, updated with new inputs or reference
outputs, tagged for categorization, and removed when no longer
needed. Examples can be evaluated by running agents and comparing actual
vs expected outputs.

## Invariants
Each dataset example must have a unique identifier within its
dataset. Examples must contain input data and may optionally include
reference output and metadata. Once created, examples maintain their
identity across dataset operations.

## Key Clojure API
- Primary functions: `add-dataset-example!`,
  `set-dataset-example-input!`, `set-dataset-example-reference-output!`
- Creation: `add-dataset-example!`, `add-dataset-example-async!`  with
   dataset, input, and optional reference output, and
   `remove-dataset-example!`
- Access: Retrieved through dataset search and evaluation operations

## Key Java API
- Primary functions: `AgentManager.addDatasetExample()`,
  `AgentManager.setDatasetExampleInput()`,
  `AgentManager.setDatasetExampleReferenceOutput()`
- Creation: `AddDatasetExampleOptions` record `referenceOutput`,
  `snapshotName`, and `tags` fields.
- Access: Through `AgentManager` dataset operations

## Relationships
- Uses: [dataset], [dataset-example-tag]
- Used by: [experiment], [example-run], [evaluators]

## Dependency graph edges:
  dataset -> dataset-example
  dataset-example-tag -> dataset-example
  dataset-example -> experiment
  dataset-example -> example-run
  dataset-example -> evaluators

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/dataset_example_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/DatasetExampleAgent.java`
