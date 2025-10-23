package com.rpl.agentorama;

import java.util.Set;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.agentorama.impl.IFetchAgentClient;
import com.rpl.rama.cluster.ClusterManagerBase;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Manager for interacting with deployed agents on a Rama cluster.
 * 
 * The agent manager provides access to agent clients, dataset management,
 * and evaluation capabilities for a specific module deployed on a cluster.
 * 
 * Example:
 * <pre>{@code
 * AgentManager manager = AgentManager.create(cluster, "MyModule");
 * Set<String> agentNames = manager.getAgentNames();
 * AgentClient client = manager.getAgentClient("my-agent");
 * String result = client.invoke("Hello world");
 * }</pre>
 */
public interface AgentManager extends IFetchAgentClient, Closeable {
  /**
   * Creates an agent manager for managing and interacting with deployed agents on a Rama cluster.
   * 
   * @param cluster the Rama cluster instance (IPC or remote cluster)
   * @param moduleName the name of the deployed module
   * @return interface for managing agents and datasets
   */
  public static AgentManager create(ClusterManagerBase cluster, String moduleName) {
    return (AgentManager) AORHelpers.CREATE_AGENT_MANAGER.invoke(cluster, moduleName);
  }

  /**
   * Gets the names of all available agents in the module.
   * 
   * @return set of agent names available in the module
   */
  Set<String> getAgentNames();

  /**
   * Creates a new dataset for agent testing and evaluation.
   * 
   * Datasets are collections of input/output examples used for testing
   * agent performance, running experiments, and regression testing.
   * 
   * @param name the name of the dataset
   * @param description description of what the dataset contains
   * @param inputJsonSchema JSON schema for input validation
   * @param outputJsonSchema JSON schema for output validation
   * @return UUID of the created dataset
   */
  UUID createDataset(String name, String description, String inputJsonSchema, String outputJsonSchema);
  
  /**
   * Updates the name of an existing dataset.
   * 
   * @param datasetId UUID of the dataset
   * @param name new name for the dataset
   */
  void setDatasetName(UUID datasetId, String name);
  
  /**
   * Updates the description of an existing dataset.
   * 
   * @param datasetId UUID of the dataset
   * @param description new description for the dataset
   */
  void setDatasetDescription(UUID datasetId, String description);
  
  /**
   * Permanently deletes a dataset and all its examples.
   * 
   * @param datasetId UUID of the dataset to delete
   */
  void destroyDataset(UUID datasetId);
  
  /**
   * Asynchronously adds an example to a dataset.
   * 
   * @param datasetId UUID of the dataset
   * @param input input data for the example
   * @param options configuration options for the example
   * @return future that completes when the example is added
   */
  CompletableFuture<Void> addDatasetExampleAsync(UUID datasetId, Object input, AddDatasetExampleOptions options);
  
  /**
   * Adds an example to a dataset for testing and evaluation.
   * 
   * @param datasetId UUID of the dataset
   * @param input input data for the example
   * @param options configuration options for the example
   * @return UUID of the added example
   */
  UUID addDatasetExample(UUID datasetId, Object input, AddDatasetExampleOptions options);
  
  /**
   * Updates the input data for a specific dataset example.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot (or null for current)
   * @param exampleId UUID of the example
   * @param input new input data for the example
   */
  void setDatasetExampleInput(UUID datasetId, String snapshotName, UUID exampleId, Object input);
  
  /**
   * Updates the reference output for a specific dataset example.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot (or null for current)
   * @param exampleId UUID of the example
   * @param referenceOutput new reference output for the example
   */
  void setDatasetExampleReferenceOutput(UUID datasetId, String snapshotName, UUID exampleId, Object referenceOutput);
  
  /**
   * Removes a specific example from a dataset.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot (or null for current)
   * @param exampleId UUID of the example to remove
   */
  void removeDatasetExample(UUID datasetId, String snapshotName, UUID exampleId);
  
  /**
   * Adds a tag to a specific dataset example for categorization.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot (or null for current)
   * @param exampleId UUID of the example
   * @param tag tag to add
   */
  void addDatasetExampleTag(UUID datasetId, String snapshotName, UUID exampleId, String tag);
  
  /**
   * Removes a tag from a specific dataset example.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot (or null for current)
   * @param exampleId UUID of the example
   * @param tag tag to remove
   */
  void removeDatasetExampleTag(UUID datasetId, String snapshotName, UUID exampleId, String tag);
  
  /**
   * Creates a snapshot of a dataset at its current state.
   * 
   * @param datasetId UUID of the dataset
   * @param fromSnapshotName name of the source snapshot (or null for current)
   * @param toSnapshotName name for the new snapshot
   */
  void snapshotDataset(UUID datasetId, String fromSnapshotName, String toSnapshotName);
  
  /**
   * Removes a specific snapshot from a dataset.
   * 
   * @param datasetId UUID of the dataset
   * @param snapshotName name of the snapshot to remove
   */
  void removeDatasetSnapshot(UUID datasetId, String snapshotName);
  
  /**
   * Searches for datasets by name or description.
   * 
   * @param searchString string to search for in names and descriptions
   * @param limit maximum number of results to return
   * @return map from dataset UUID to dataset name
   */
  Map<UUID, String> searchDatasets(String searchString, int limit);

  /**
   * Creates an evaluator instance from a builder for measuring agent performance in experiments or actions.
   * 
   * @param name name for the evaluator
   * @param builderName name of the evaluator builder (declared in topology or built-in)
   * @param params map of parameters for the evaluator. Parameters are a map from parameter name to parameter value, both strings.
   * @param description description of what the evaluator measures
   * @param options configuration options for the evaluator
   */
  void createEvaluator(String name, String builderName, Map params, String description, CreateEvaluatorOptions options);
  
  /**
   * Removes an evaluator from the system.
   * 
   * @param name name of the evaluator to remove
   */
  void removeEvaluator(String name);
  
  /**
   * Searches for evaluators by name or description.
   * 
   * @param searchString string to search for in evaluator names
   * @return set of matching evaluator names
   */
  Set<String> searchEvaluators(String searchString);
  
  /**
   * Tests an evaluator on a single sample input / reference output / output.
   * 
   * @param name name of the evaluator
   * @param input input data for the evaluation
   * @param referenceOutput reference output for comparison
   * @param output actual output to evaluate
   * @return result scores from score name to score value
   */
  Map tryEvaluator(String name, Object input, Object referenceOutput, Object output);
  
  /**
   * Tests a comparative evaluator on multiple outputs.
   * 
   * @param name name of the evaluator
   * @param input input data for the evaluation
   * @param referenceOutput reference output for comparison
   * @param outputs collection of actual outputs to compare
   * @return comparative evaluation result, a map of score name to score value
   */
  Map tryComparativeEvaluator(String name, Object input, Object referenceOutput, List<Object> outputs);
  
  /**
   * Tests a summary evaluator on a collection of example runs.
   * 
   * @param name name of the evaluator
   * @param exampleRuns collection of example runs created with ExampleRun.create()
   * @return summary evaluation result with aggregate metrics, a map from score name to score value
   */
  Map trySummaryEvaluator(String name, List<ExampleRun> exampleRuns);
}
