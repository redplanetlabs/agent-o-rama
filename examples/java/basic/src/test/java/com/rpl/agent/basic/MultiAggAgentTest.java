package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.List;
import java.util.Map;
import org.junit.Test;

/**
 * Test class for MultiAggAgent demonstrating custom aggregation logic.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>multiAgg: Custom aggregation with multiple tagged input streams
 *   <li>init and on clauses for state management
 *   <li>Complex aggregation patterns
 *   <li>HashMap usage for request and response data structures
 * </ul>
 */
public class MultiAggAgentTest {

  @Test
  public void testMultiAggAgent() throws Exception {
    // Tests multi-agg with different tagged inputs using HashMap
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiAggAgent.MultiAggModule module = new MultiAggAgent.MultiAggModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiAggAgent");

      // Test with mixed data types
      java.util.Map<String, Object> request = new java.util.HashMap<>();
      request.put("numbers", List.of(1, 2, 3, 4, 5));
      request.put("text", List.of("Hello", "World"));

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.invoke(request);

      assertNotNull("Result should not be null", result);
      @SuppressWarnings("unchecked")
      Map<String, Object> summary = (Map<String, Object>) result.get("summary");
      assertNotNull("Summary should not be null", summary);

      assertEquals("Should process 5 numbers", 5, summary.get("numbersProcessed"));
      assertEquals("Should process 2 text entries", 2, summary.get("textProcessed"));
      assertEquals("Sum should be 15", 15, summary.get("numberSum"));
      assertTrue("Should have word count", (Integer) summary.get("totalWords") > 0);
    }
  }
}
