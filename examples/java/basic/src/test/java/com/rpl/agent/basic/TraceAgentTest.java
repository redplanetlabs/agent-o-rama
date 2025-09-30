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
 * Test class for TraceAgent demonstrating basic agent traces.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>agent-invoke: Synchronous agent invocation
 *   <li>Single-node agent execution
 *   <li>Basic agent tracing
 * </ul>
 */
public class TraceAgentTest {

  @Test
  public void testTraceAgent() throws Exception {
    // Tests basic agent invocation and traces
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      TraceAgent.TraceAgentModule module = new TraceAgent.TraceAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TraceAgent");

      // Test agent invocation
      String result = (String) agent.invoke("TestUser");

      assertNotNull("Result should not be null", result);
      assertTrue("Result should contain user name", result.contains("TestUser"));
      assertEquals(
          "Result should match expected format", "Welcome to agent-o-rama, TestUser!", result);
    }
  }
}
