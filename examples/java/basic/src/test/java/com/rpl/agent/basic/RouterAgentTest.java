package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for RouterAgent demonstrating conditional routing and branching.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Conditional routing based on input values
 *   <li>Different execution paths for different inputs
 *   <li>Router node functionality and branching logic
 * </ul>
 */
public class RouterAgentTest {

  @Test
  public void testRouterAgent() throws Exception {
    // Tests routing for high priority messages
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RouterAgent.RouterAgentModule module = new RouterAgent.RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      // Test with high priority message
      String result = (String) agent.invoke("urgent:System alert");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain HIGH priority", result.contains("[HIGH]"));
      assertTrue("Should contain the message", result.contains("System alert"));
    }
  }
}
