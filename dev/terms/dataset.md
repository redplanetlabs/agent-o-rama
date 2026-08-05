# Dataset

## Definition
Managed collections of input/output examples used for systematic agent testing, evaluation, and performance tracking.

## Architecture Role
Serves as the foundational data layer for agent evaluation systems, providing structured test cases and performance benchmarks within the agent-o-rama framework.

## Operations
Creation and population of example collections, execution of evaluation runs against agent implementations, querying and filtering of stored examples.

## Invariants
Examples are immutable once added to a dataset. Datasets maintain version control and distributed availability across cluster nodes.

## Key Clojure API
- Primary functions: `create-dataset!`, `destroy-dataset!`, `search-datasets`, `add-dataset-example!`
- Management: `set-dataset-description!`, `set-dataset-name!`
- Creation: `create-dataset!` with manager and configuration
- Access: Through dataset manager instance

## Key Java API
- Primary functions: `createDataset`, `addDatasetExample`, `searchDatasets`
- Creation: `AgentManager.createDataset`
- Access: Via `AgentManager` interface

## Relationships
- Uses: [experiment], [agent-invoke], [pstate]
- Used by: [evaluation], [regression-testing], [performance-tracking]

## Dependency graph edges:
    experiment -> dataset
    agent-invoke -> dataset
    pstate -> dataset
    dataset -> evaluation
    dataset -> regression-testing
    dataset -> performance-tracking

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/dataset_agent.clj`