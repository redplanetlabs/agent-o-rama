package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.List;
import org.junit.Test;

/**
 * Test class for AggregationAgent demonstrating fan-out/fan-in aggregation patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>aggStartNode distributing work to multiple parallel processors
 *   <li>aggNode collecting and combining results from multiple executions
 *   <li>Built-in LIST_AGG aggregator functionality
 *   <li>Fan-out/fan-in execution patterns with different chunk sizes
 * </ul>
 */
public class AggregationAgentTest {

  @Test
  public void testBasicAggregation() throws Exception {
    // Tests basic aggregation functionality with simple data
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AggregationAgent.AggregationModule module = new AggregationAgent.AggregationModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      // Test with simple data set
      List<Integer> testData = new ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        testData.add(i); // [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
      }

      AggregationAgent.AggregationRequest request =
          new AggregationAgent.AggregationRequest(testData, 3);
      AggregationAgent.AggregationResult result =
          (AggregationAgent.AggregationResult) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("Should process all 10 items", 10, result.getTotalItems());
      assertEquals("Should create 4 chunks", 4, result.getChunksProcessed());

      // Expected: chunks [1,2,3], [4,5,6], [7,8,9], [10]
      // Squared: [1,4,9], [16,25,36], [49,64,81], [100]
      // Sums: 14 + 77 + 194 + 100 = 385
      assertEquals("Total sum should be correct", 385, result.getTotalSum());
    }
  }

  @Test
  public void testDifferentChunkSizes() throws Exception {
    // Tests aggregation with different chunk sizes
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AggregationAgent.AggregationModule module = new AggregationAgent.AggregationModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      List<Integer> testData = new ArrayList<>();
      for (int i = 1; i <= 12; i++) {
        testData.add(i); // [1, 2, 3, ..., 12]
      }

      // Test with chunk size 4
      AggregationAgent.AggregationRequest request1 =
          new AggregationAgent.AggregationRequest(testData, 4);
      AggregationAgent.AggregationResult result1 =
          (AggregationAgent.AggregationResult) agent.invoke(request1);

      assertEquals("Should process all 12 items", 12, result1.getTotalItems());
      assertEquals("Should create 3 chunks with size 4", 3, result1.getChunksProcessed());

      // Test with chunk size 5
      AggregationAgent.AggregationRequest request2 =
          new AggregationAgent.AggregationRequest(testData, 5);
      AggregationAgent.AggregationResult result2 =
          (AggregationAgent.AggregationResult) agent.invoke(request2);

      assertEquals("Should process all 12 items", 12, result2.getTotalItems());
      assertEquals("Should create 3 chunks with size 5", 3, result2.getChunksProcessed());

      // Both should have the same total sum (processing is deterministic)
      assertEquals("Total sums should be equal", result1.getTotalSum(), result2.getTotalSum());
    }
  }

  @Test
  public void testChunkProcessing() throws Exception {
    // Tests that chunk processing correctly squares values and maintains order
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AggregationAgent.AggregationModule module = new AggregationAgent.AggregationModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      // Test with simple data that's easy to verify
      List<Integer> testData = List.of(2, 3, 4); // Small dataset for easy verification

      AggregationAgent.AggregationRequest request =
          new AggregationAgent.AggregationRequest(testData, 2);
      AggregationAgent.AggregationResult result =
          (AggregationAgent.AggregationResult) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("Should process all 3 items", 3, result.getTotalItems());
      assertEquals("Should create 2 chunks", 2, result.getChunksProcessed());

      // Expected: chunks [2,3], [4]
      // Squared: [4,9], [16]
      // Sums: 13 + 16 = 29
      assertEquals("Total sum should be 29", 29, result.getTotalSum());

      // Verify chunk results are included
      List<AggregationAgent.ChunkResult> chunkResults = result.getChunkResults();
      assertNotNull("Chunk results should not be null", chunkResults);
      assertEquals("Should have 2 chunk results", 2, chunkResults.size());

      // Results should be sorted by first element of original chunk
      assertTrue(
          "First chunk should start with 2", chunkResults.get(0).getOriginalChunk().get(0) == 2);
      assertTrue(
          "Second chunk should start with 4", chunkResults.get(1).getOriginalChunk().get(0) == 4);
    }
  }

  @Test
  public void testSingleChunk() throws Exception {
    // Tests aggregation when data fits in a single chunk
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AggregationAgent.AggregationModule module = new AggregationAgent.AggregationModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      // Test with data that fits in one chunk
      List<Integer> testData = List.of(1, 2, 3);

      AggregationAgent.AggregationRequest request =
          new AggregationAgent.AggregationRequest(testData, 5); // Chunk size larger than data
      AggregationAgent.AggregationResult result =
          (AggregationAgent.AggregationResult) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("Should process all 3 items", 3, result.getTotalItems());
      assertEquals("Should create 1 chunk", 1, result.getChunksProcessed());

      // Expected: chunk [1,2,3]
      // Squared: [1,4,9]
      // Sum: 14
      assertEquals("Total sum should be 14", 14, result.getTotalSum());
    }
  }
}
