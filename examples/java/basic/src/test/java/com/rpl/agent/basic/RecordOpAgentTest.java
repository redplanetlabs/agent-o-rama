package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for RecordOpAgent demonstrating trace recording patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Agent invocation with recordNestedOp calls
 *   <li>Verifying agent results when using trace recording
 * </ul>
 */
public class RecordOpAgentTest {

  @Test
  public void testRecordOpAgent() throws Exception {
    // Tests agent invocation with recorded operations
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      RecordOpAgent.RecordOpModule module = new RecordOpAgent.RecordOpModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RecordOpAgent");

      // Test with single input
      String result = (String) agent.invoke("Alice");
      assertNotNull("Agent should return a result", result);
      assertEquals("Hello, Alice!", result);

      // Test with different input
      result = (String) agent.invoke("Bob");
      assertNotNull("Agent should return a result", result);
      assertEquals("Hello, Bob!", result);
    }
  }
}
