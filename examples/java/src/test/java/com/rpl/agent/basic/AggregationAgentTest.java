package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AggregationAgent");

      // Test with simple data set
      List<Integer> testData = new ArrayList<>();
      for (int i = 1; i <= 10; i++) {
        testData.add(i); // [1, 2, 3, 4, 5, 6, 7, 8, 9, 10]
      }

      Map<String, Object> request = new HashMap<>();
      request.put("data", testData);
      request.put("chunkSize", 3);

      Map<String, Object> result = (Map<String, Object>) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      assertEquals("Should process all 10 items", 10, result.get("totalItems"));
      assertEquals("Should create 4 chunks", 4, result.get("chunksProcessed"));

      // Expected: chunks [1,2,3], [4,5,6], [7,8,9], [10]
      // Squared: [1,4,9], [16,25,36], [49,64,81], [100]
      // Sums: 14 + 77 + 194 + 100 = 385
      assertEquals("Total sum should be correct", 385, result.get("totalSum"));
    }
  }
}
