package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.BuiltIn;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.ops.RamaVoidFunction3;
import com.rpl.rama.ops.RamaFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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

  /** Agent Module demonstrating aggregation functionality. */
  public static class AggregationModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
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
      implements RamaFunction2<AgentNode, Map<String, Object>, Object> {

    @Override
    @SuppressWarnings("unchecked")
    public Object invoke(AgentNode agentNode, Map<String, Object> request) {
      List<Integer> data = (List<Integer>) request.get("data");
      int chunkSize = (Integer) request.get("chunkSize");

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

      Map<String, Object> result = new HashMap<>();
      result.put("originalChunk", chunk);
      result.put("processedChunk", processedChunk);
      result.put("chunkSum", chunkSum);

      agentNode.emit("collect-results", result);
    }
  }

  /** Function that aggregates all results using built-in vector aggregator. */
  public static class CollectResultsFunction
      implements RamaVoidFunction3<AgentNode, List<Map<String, Object>>, Object> {

    @Override
    @SuppressWarnings("unchecked")
    public void invoke(
        AgentNode agentNode, List<Map<String, Object>> aggregatedResults, Object startNodeResult) {
      // Sort chunks by their first element to ensure consistent order
      List<Map<String, Object>> sortedResults = new ArrayList<>(aggregatedResults);
      sortedResults.sort(
          Comparator.comparing(result -> ((List<Integer>) result.get("originalChunk")).get(0)));

      int totalSum = 0;
      int totalItems = 0;
      for (Map<String, Object> result : sortedResults) {
        totalSum += (Integer) result.get("chunkSum");
        totalItems += ((List<Integer>) result.get("originalChunk")).size();
      }

      Map<String, Object> finalResult = new HashMap<>();
      finalResult.put("totalItems", totalItems);
      finalResult.put("totalSum", totalSum);
      finalResult.put("chunksProcessed", sortedResults.size());
      finalResult.put("chunkResults", sortedResults);

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
      Map<String, Object> request1 = new HashMap<>();
      request1.put("data", testData);
      request1.put("chunkSize", 5);

      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.invoke(request1);
      System.out.println("Result 1:");
      System.out.println("  Total items: " + result1.get("totalItems"));
      System.out.println("  Total sum: " + result1.get("totalSum"));
      System.out.println("  Chunks processed: " + result1.get("chunksProcessed"));

      System.out.println("\n--- Processing with chunk size 3 ---");
      Map<String, Object> request2 = new HashMap<>();
      request2.put("data", testData);
      request2.put("chunkSize", 3);

      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.invoke(request2);
      System.out.println("Result 2:");
      System.out.println("  Total items: " + result2.get("totalItems"));
      System.out.println("  Total sum: " + result2.get("totalSum"));
      System.out.println("  Chunks processed: " + result2.get("chunksProcessed"));

      System.out.println("\nNotice how:");
      System.out.println("- Work is distributed in parallel to multiple nodes");
      System.out.println("- Results are automatically aggregated back together");
      System.out.println("- Different chunk sizes create different parallelization");
      System.out.println("- Built-in aggregators simplify result collection");
    }
  }
}
