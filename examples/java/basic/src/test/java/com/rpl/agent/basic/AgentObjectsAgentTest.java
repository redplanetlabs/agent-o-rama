package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for AgentObjectsAgent demonstrating agent object functionality.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Static agent objects sharing across invocations
 *   <li>Dynamic agent object builders with dependencies
 *   <li>Thread-unsafe services working safely via pooling
 * </ul>
 */
public class AgentObjectsAgentTest {

  @Test
  public void testAgentObjectsAgent() throws Exception {
    // Tests that agent objects are properly shared and used
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AgentObjectsAgent.AgentObjectsModule module = new AgentObjectsAgent.AgentObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AgentObjectsAgent");

      // Test single invocation
      String result = (String) agent.invoke("TestMessage");
      assertNotNull("Agent should return a result", result);
      assertTrue("Result should contain version", result.contains("v1.2.3"));
      assertTrue("Result should contain message", result.contains("TestMessage"));
      assertTrue("Result should contain counter", result.contains("#1"));
      assertTrue("Result should contain send-to", result.contains("alerts"));
    }
  }
}
