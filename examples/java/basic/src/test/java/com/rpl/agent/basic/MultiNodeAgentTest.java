package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
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
  public void testMultiNodeAgentBasicFlow() throws Exception {
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
      assertTrue("Should contain greeting", result.contains("Hello") || result.contains("Hi") || result.contains("Good"));
      assertTrue("Should contain thanks", result.contains("Thanks for joining"));
    }
  }

  @Test
  public void testMultiNodeAgentWithDifferentNames() throws Exception {
    // Tests multi-node execution with different user names
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiNodeAgent.MultiNodeModule module = new MultiNodeAgent.MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Test with different names
      String result1 = (String) agent.invoke("Bob");
      String result2 = (String) agent.invoke("Charlie");
      String result3 = (String) agent.invoke("Diana");

      // All should contain personalized greetings
      assertNotNull("Result 1 should not be null", result1);
      assertNotNull("Result 2 should not be null", result2);
      assertNotNull("Result 3 should not be null", result3);

      assertTrue("Result 1 should contain Bob", result1.contains("Bob"));
      assertTrue("Result 2 should contain Charlie", result2.contains("Charlie"));
      assertTrue("Result 3 should contain Diana", result3.contains("Diana"));

      // All should contain the core welcome message
      assertTrue("Result 1 should contain welcome", result1.contains("Welcome to agent-o-rama"));
      assertTrue("Result 2 should contain welcome", result2.contains("Welcome to agent-o-rama"));
      assertTrue("Result 3 should contain welcome", result3.contains("Welcome to agent-o-rama"));
    }
  }

  @Test
  public void testMultiNodeAgentWithEmptyName() throws Exception {
    // Tests multi-node execution with empty name
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiNodeAgent.MultiNodeModule module = new MultiNodeAgent.MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Test with empty name
      String result = (String) agent.invoke("");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain welcome message", result.contains("Welcome to agent-o-rama"));
      // Should still generate some greeting even with empty name
      assertTrue("Should contain thanks", result.contains("Thanks for joining"));
    }
  }

  @Test
  public void testMultiNodeAgentWithLongName() throws Exception {
    // Tests multi-node execution with long name
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiNodeAgent.MultiNodeModule module = new MultiNodeAgent.MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Test with long name
      String longName = "Alexander Maximilian Rodriguez-Thompson";
      String result = (String) agent.invoke(longName);

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain welcome message", result.contains("Welcome to agent-o-rama"));
      assertTrue("Should contain full name", result.contains(longName));
      assertTrue("Should contain thanks", result.contains("Thanks for joining"));
    }
  }

  @Test
  public void testMultiNodeAgentConsistentStructure() throws Exception {
    // Tests that all results have consistent structure
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      MultiNodeAgent.MultiNodeModule module = new MultiNodeAgent.MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Test multiple invocations
      String result1 = (String) agent.invoke("User1");
      String result2 = (String) agent.invoke("User2");

      // Both should have consistent structure
      assertNotNull("Result 1 should not be null", result1);
      assertNotNull("Result 2 should not be null", result2);

      // Both should contain the core elements processed by different nodes
      assertTrue("Result 1 should have greeting + welcome + thanks",
          result1.contains("Welcome to agent-o-rama") && result1.contains("Thanks for joining"));
      assertTrue("Result 2 should have greeting + welcome + thanks",
          result2.contains("Welcome to agent-o-rama") && result2.contains("Thanks for joining"));

      // Each should be personalized
      assertTrue("Result 1 should contain User1", result1.contains("User1"));
      assertTrue("Result 2 should contain User2", result2.contains("User2"));
    }
  }
}