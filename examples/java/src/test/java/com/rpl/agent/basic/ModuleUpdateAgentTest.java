package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for ModuleUpdateAgent demonstrating module update testing patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Testing module update functionality
 *   <li>Verifying agent continues running after update
 *   <li>Testing state preservation across updates
 * </ul>
 */
public class ModuleUpdateAgentTest {

  @Test
  public void testModuleUpdate() throws Exception {
    // Tests that the agent continues running after module update
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ModuleUpdateAgent.CounterModule module = new ModuleUpdateAgent.CounterModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("CounterAgent");

      // Start counter with initial value 0
      AgentInvoke invokeHandle = agent.initiate(0);

      // Give it time to start counting
      Thread.sleep(1000);

      // Update the module while it's running
      ipc.updateModule(module);

      // Get final result - agent should complete counting to 50
      Integer finalCount = (Integer) agent.result(invokeHandle);
      assertNotNull("Agent should return a final count", finalCount);
      assertTrue("Final count should be 50", finalCount == 50);
    }
  }

  @Test
  public void testModuleUpdateWithHigherStartValue() throws Exception {
    // Tests module update with a different starting value
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ModuleUpdateAgent.CounterModule module = new ModuleUpdateAgent.CounterModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("CounterAgent");

      // Start counter with initial value 40
      AgentInvoke invokeHandle = agent.initiate(40);

      // Give it time to start counting
      Thread.sleep(500);

      // Update the module while it's running
      ipc.updateModule(module);

      // Get final result - should complete quickly since starting at 40
      Integer finalCount = (Integer) agent.result(invokeHandle);
      assertNotNull("Agent should return a final count", finalCount);
      assertTrue("Final count should be 50", finalCount == 50);
    }
  }
}
