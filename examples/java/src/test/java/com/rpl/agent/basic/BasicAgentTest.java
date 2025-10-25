package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for BasicAgent demonstrating agent testing patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Setting up an in-process cluster for testing
 *   <li>Deploying and invoking agents in tests
 *   <li>Asserting on agent results
 *   <li>Testing with different input values
 * </ul>
 */
public class BasicAgentTest {

  @Test
  public void testBasicAgent() throws Exception {
    // Tests basic agent invocation with a single input
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      BasicAgent.BasicModule module = new BasicAgent.BasicModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("BasicAgent");

      // Test with single input
      String result = (String) agent.invoke("TestUser");
      assertNotNull("Agent should return a result", result);
      assertEquals("Welcome to agent-o-rama, TestUser!", result);
    }
  }
}
