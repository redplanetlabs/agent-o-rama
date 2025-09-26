# Dataset Snapshot

## Definition
A versioned state of a dataset at a specific point in time, capturing the exact examples and metadata. Snapshots ensure reproducible experiments and track dataset evolution over time.

## Architecture Role
Snapshots provide immutable versions of datasets for consistent evaluation across time. They enable reproducible experiments by freezing dataset state and allow tracking of dataset changes during development cycles.

## Operations
Snapshots can be created from current dataset state, removed when no longer needed, and referenced in experiments to ensure consistent evaluation baselines. Snapshots preserve complete dataset state including examples and metadata.

## Invariants
Each snapshot represents a frozen point-in-time view of a dataset. Snapshots are immutable once created and maintain referential integrity with their source dataset. Snapshot identifiers are unique within the system.

## Key Clojure API
- Primary functions: `snapshot-dataset!`, `remove-dataset-snapshot!`
- Creation: `snapshot-dataset!` with dataset identifier and optional metadata
- Access: Snapshots are referenced by ID in experiment configurations

## Key Java API
- Primary functions: `AgentManager.snapshotDataset()`, `AgentManager.removeDatasetSnapshot()`
- Creation: Through `AgentManager` with dataset reference
- Access: Snapshot IDs used in experiment setup

## Relationships
- Uses: [dataset]
- Used by: [experiment], [evaluators]

## Dependency graph edges:
  dataset -> dataset-snapshot
  dataset-snapshot -> experiment
  dataset-snapshot -> evaluators

## Examples
- Clojure: `examples/clj/src/com/rpl/agent/basic/dataset_agent.clj`
- Java: `examples/java/basic/src/main/java/com/rpl/agent/basic/DatasetAgent.java`
