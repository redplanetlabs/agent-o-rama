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
 * Test class for RouterAgent demonstrating conditional routing and branching.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Conditional routing based on input values
 *   <li>Different execution paths for different inputs
 *   <li>Router node functionality and branching logic
 *   <li>Message processing with priority routing
 * </ul>
 */
public class RouterAgentTest {

  @Test
  public void testRouterAgentHighPriorityMessage() throws Exception {
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
      String result = (String) agent.invoke("URGENT: System alert");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain HIGH priority", result.contains("[HIGH]"));
      assertTrue("Should contain the message", result.contains("URGENT: System alert"));
    }
  }

  @Test
  public void testRouterAgentLowPriorityMessage() throws Exception {
    // Tests routing for low priority messages
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RouterAgent.RouterAgentModule module = new RouterAgent.RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      // Test with low priority message
      String result = (String) agent.invoke("Info: Daily report");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain LOW priority", result.contains("[LOW]"));
      assertTrue("Should contain the message", result.contains("Info: Daily report"));
    }
  }

  @Test
  public void testRouterAgentMediumPriorityMessage() throws Exception {
    // Tests routing for medium priority messages
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RouterAgent.RouterAgentModule module = new RouterAgent.RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      // Test with medium priority message (no URGENT/Info keywords)
      String result = (String) agent.invoke("Regular update");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain MEDIUM priority", result.contains("[MEDIUM]"));
      assertTrue("Should contain the message", result.contains("Regular update"));
    }
  }

  @Test
  public void testRouterAgentMultipleMessages() throws Exception {
    // Tests router with multiple different message types
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RouterAgent.RouterAgentModule module = new RouterAgent.RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      // Test different message types
      String urgent = (String) agent.invoke("URGENT: Critical error");
      String info = (String) agent.invoke("Info: Status update");
      String normal = (String) agent.invoke("Standard message");

      // Verify routing
      assertTrue("Urgent should be HIGH priority", urgent.contains("[HIGH]"));
      assertTrue("Info should be LOW priority", info.contains("[LOW]"));
      assertTrue("Normal should be MEDIUM priority", normal.contains("[MEDIUM]"));

      assertTrue("Urgent should contain message", urgent.contains("Critical error"));
      assertTrue("Info should contain message", info.contains("Status update"));
      assertTrue("Normal should contain message", normal.contains("Standard message"));
    }
  }

  @Test
  public void testRouterAgentEmptyMessage() throws Exception {
    // Tests router with empty message
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RouterAgent.RouterAgentModule module = new RouterAgent.RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      // Test with empty message
      String result = (String) agent.invoke("");

      assertNotNull("Result should not be null", result);
      assertTrue("Should contain MEDIUM priority for empty message", result.contains("[MEDIUM]"));
    }
  }
}