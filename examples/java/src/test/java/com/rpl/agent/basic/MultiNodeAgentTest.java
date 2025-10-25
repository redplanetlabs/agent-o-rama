package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for MultiNodeAgent demonstrating multi-step agent execution flow.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Multi-node agent graph execution
 *   <li>Data flow between agent nodes using emit
 *   <li>Processing pipeline with greeting generation
 *   <li>Sequential node execution patterns
 * </ul>
 */
public class MultiNodeAgentTest {

  @Test
  public void testMultiNodeAgent() throws Exception {
    // Tests basic multi-node execution flow
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiNodeAgent.MultiNodeModule module = new MultiNodeAgent.MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Test with a user name
      String result = (String) agent.invoke("Alice");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain welcome message", result.contains("Welcome to agent-o-rama"));
      assertTrue("Should contain user name", result.contains("Alice"));
      assertTrue(
          "Should contain greeting",
          result.contains("Hello") || result.contains("Hi") || result.contains("Good"));
      assertTrue("Should contain thanks", result.contains("Thanks for joining"));
    }
  }
}
