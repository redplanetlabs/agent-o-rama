package com.rpl.agentorama;

import java.util.Set;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.agentorama.impl.IFetchAgentClient;
import com.rpl.rama.cluster.ClusterManagerBase;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface AgentManager extends IFetchAgentClient, Closeable {
  public static AgentManager create(ClusterManagerBase cluster, String moduleName) {
    return (AgentManager) AORHelpers.CREATE_AGENT_MANAGER.invoke(cluster, moduleName);
  }

  Set<String> getAgentNames();

  // datasets
  UUID createDataset(String name, String description, String inputJsonSchema, String outputJsonSchema);
  void setDatasetName(UUID datasetId, String name);
  void setDatasetDescription(UUID datasetId, String description);
  void destroyDataset(UUID datasetId);
  CompletableFuture<Void> addDatasetExampleAsync(UUID datasetId, Object input, AddDatasetExampleOptions options);
  UUID addDatasetExample(UUID datasetId, Object input, AddDatasetExampleOptions options);
  void setDatasetExampleInput(UUID datasetId, String snapshotName, UUID exampleId, Object input);
  void setDatasetExampleReferenceOutput(UUID datasetId, String snapshotName, UUID exampleId, Object referenceOutput);
  void removeDatasetExample(UUID datasetId, String snapshotName, UUID exampleId);
  void addDatasetExampleTag(UUID datasetId, String snapshotName, UUID exampleId, String tag);
  void removeDatasetExampleTag(UUID datasetId, String snapshotName, UUID exampleId, String tag);
  void snapshotDataset(UUID datasetId, String fromSnapshotName, String toSnapshotName);
  void removeDatasetSnapshot(UUID datasetId, String snapshotName);
  Map<UUID, String> searchDatasets(String searchString, int limit);

  void createEvaluator(String name, String builderName, Map params, String description, CreateEvaluatorOptions options);
  void removeEvaluator(String name);
  Set<String> searchEvaluators(String searchString);
  Map tryEvaluator(String name, Object input, Object referenceOutput, Object output);
  Map tryComparativeEvaluator(String name, Object input, Object referenceOutput, List<Object> outputs);
  Map trySummaryEvaluator(String name, List<ExampleRun> exampleRuns);
}
