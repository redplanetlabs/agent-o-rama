package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Set;
import org.junit.Test;

/**
 * Test class for RamaModuleAgent demonstrating Rama module with depot integration.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Testing direct RamaModule implementation
 *   <li>Accessing and testing depot functionality
 *   <li>Verifying agent availability in the module
 *   <li>Testing agent invocations with structured responses
 * </ul>
 */
public class RamaModuleAgentTest {

  @Test
  public void testRamaModuleAgent() throws Exception {
    // Tests Rama module with manual agent topology creation
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the Rama module
      RamaModuleAgent.RamaModule module = new RamaModuleAgent.RamaModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      // Verify agent is available
      Set<String> agentNames = manager.getAgentNames();
      assertTrue("FeedbackAgent should be available", agentNames.contains("FeedbackAgent"));

      AgentClient agent = manager.getAgentClient("FeedbackAgent");

      // Test first feedback processing
      HashMap<String, Object> result1 = (HashMap<String, Object>) agent.invoke("Great product!");
      assertNotNull("Agent should return a result", result1);
      assertEquals("success", result1.get("status"));
      assertEquals("Processed: Great product!", result1.get("message"));
      assertEquals(14, result1.get("length"));

      // Test second feedback processing
      HashMap<String, Object> result2 = (HashMap<String, Object>) agent.invoke("Needs work");
      assertNotNull("Agent should return a result", result2);
      assertEquals("success", result2.get("status"));
      assertEquals("Processed: Needs work", result2.get("message"));
      assertEquals(10, result2.get("length"));
    }
  }
}
