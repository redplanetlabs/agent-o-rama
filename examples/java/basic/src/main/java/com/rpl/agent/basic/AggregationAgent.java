package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.agentorama.BuiltIn;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.ops.RamaVoidFunction3;
import com.rpl.rama.ops.RamaFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

/**
 * Java example demonstrating fan-out/fan-in aggregation patterns with aggStartNode and aggNode.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>aggStartNode: Start aggregation by emitting to multiple targets
 *   <li>aggNode: Collect and combine results from multiple executions
 *   <li>Fan-out/fan-in execution patterns
 *   <li>Built-in aggregators for common operations
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class AggregationAgent {

  /** Input request for aggregation processing. */
  public static class AggregationRequest {
    private final List<Integer> data;
    private final int chunkSize;

    public AggregationRequest(List<Integer> data, int chunkSize) {
      this.data = data;
      this.chunkSize = chunkSize;
    }

    public List<Integer> getData() {
      return data;
    }

    public int getChunkSize() {
      return chunkSize;
    }
  }

  /** Result of processing a single chunk. */
  public static class ChunkResult {
    private final List<Integer> originalChunk;
    private final List<Integer> processedChunk;
    private final int chunkSum;

    public ChunkResult(List<Integer> originalChunk, List<Integer> processedChunk, int chunkSum) {
      this.originalChunk = originalChunk;
      this.processedChunk = processedChunk;
      this.chunkSum = chunkSum;
    }

    public List<Integer> getOriginalChunk() {
      return originalChunk;
    }

    public List<Integer> getProcessedChunk() {
      return processedChunk;
    }

    public int getChunkSum() {
      return chunkSum;
    }
  }

  /** Final aggregated result. */
  public static class AggregationResult {
    private final int totalItems;
    private final int totalSum;
    private final int chunksProcessed;
    private final List<ChunkResult> chunkResults;

    public AggregationResult(
        int totalItems, int totalSum, int chunksProcessed, List<ChunkResult> chunkResults) {
      this.totalItems = totalItems;
      this.totalSum = totalSum;
      this.chunksProcessed = chunksProcessed;
      this.chunkResults = chunkResults;
    }

    public int getTotalItems() {
      return totalItems;
    }

    public int getTotalSum() {
      return totalSum;
    }

    public int getChunksProcessed() {
      return chunksProcessed;
    }

    public List<ChunkResult> getChunkResults() {
      return chunkResults;
    }
  }

  /** Agent Module demonstrating aggregation functionality. */
  public static class AggregationModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
      topology
          .newAgent("AggregationAgent")
          // Start aggregation by distributing work to parallel processors
          .aggStartNode("distribute-work", "process-chunk", new DistributeWorkFunction())
          // Process individual chunks in parallel
          .node("process-chunk", "collect-results", new ProcessChunkFunction())
          // Aggregate all results using built-in vector aggregator
          .aggNode("collect-results", null, BuiltIn.LIST_AGG, new CollectResultsFunction());
    }
  }

  /** Aggregation start function that distributes work to parallel processors. */
  public static class DistributeWorkFunction
      implements RamaFunction2<AgentNode, AggregationRequest, Object> {

    @Override
    public Object invoke(AgentNode agentNode, AggregationRequest request) {
      List<Integer> data = request.getData();
      int chunkSize = request.getChunkSize();

      // Create chunks from the data
      List<List<Integer>> chunks = new ArrayList<>();
      for (int i = 0; i < data.size(); i += chunkSize) {
        int end = Math.min(i + chunkSize, data.size());
        chunks.add(new ArrayList<>(data.subList(i, end)));
      }

      // Emit each chunk for parallel processing
      for (List<Integer> chunk : chunks) {
        agentNode.emit("process-chunk", chunk);
      }

      return null; // aggStartNode doesn't need to return meaningful data
    }
  }

  /** Function that processes individual chunks in parallel. */
  public static class ProcessChunkFunction implements RamaVoidFunction2<AgentNode, List<Integer>> {

    @Override
    public void invoke(AgentNode agentNode, List<Integer> chunk) {
      // Transform the chunk data (square each value)
      List<Integer> processedChunk = new ArrayList<>();
      int chunkSum = 0;
      for (Integer value : chunk) {
        int squared = value * value;
        processedChunk.add(squared);
        chunkSum += squared;
      }

      ChunkResult result = new ChunkResult(chunk, processedChunk, chunkSum);
      agentNode.emit("collect-results", result);
    }
  }

  /** Function that aggregates all results using built-in vector aggregator. */
  public static class CollectResultsFunction
      implements RamaVoidFunction3<AgentNode, List<ChunkResult>, Object> {

    @Override
    public void invoke(
        AgentNode agentNode, List<ChunkResult> aggregatedResults, Object startNodeResult) {
      // Sort chunks by their first element to ensure consistent order
      List<ChunkResult> sortedResults = new ArrayList<>(aggregatedResults);
      sortedResults.sort(Comparator.comparing(result -> result.getOriginalChunk().get(0)));

      int totalSum = 0;
      int totalItems = 0;
      for (ChunkResult result : sortedResults) {
        totalSum += result.getChunkSum();
        totalItems += result.getOriginalChunk().size();
      }

      AggregationResult finalResult =
          new AggregationResult(totalItems, totalSum, sortedResults.size(), sortedResults);

      agentNode.result(finalResult);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Aggregation Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      AggregationModule module = new AggregationModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      System.out.println("Aggregation Agent Example:");
      System.out.println("Processing data in parallel chunks with result aggregation");

      // Process data with different chunk sizes
      List<Integer> testData = new ArrayList<>();
      for (int i = 1; i <= 20; i++) {
        testData.add(i); // [1, 2, 3, ..., 20]
      }

      System.out.println("\n--- Processing with chunk size 5 ---");
      AggregationResult result1 =
          (AggregationResult) agent.invoke(new AggregationRequest(testData, 5));
      System.out.println("Result 1:");
      System.out.println("  Total items: " + result1.getTotalItems());
      System.out.println("  Total sum: " + result1.getTotalSum());
      System.out.println("  Chunks processed: " + result1.getChunksProcessed());

      System.out.println("\n--- Processing with chunk size 3 ---");
      AggregationResult result2 =
          (AggregationResult) agent.invoke(new AggregationRequest(testData, 3));
      System.out.println("Result 2:");
      System.out.println("  Total items: " + result2.getTotalItems());
      System.out.println("  Total sum: " + result2.getTotalSum());
      System.out.println("  Chunks processed: " + result2.getChunksProcessed());

      System.out.println("\nNotice how:");
      System.out.println("- Work is distributed in parallel to multiple nodes");
      System.out.println("- Results are automatically aggregated back together");
      System.out.println("- Different chunk sizes create different parallelization");
      System.out.println("- Built-in aggregators simplify result collection");
    }
  }
}
