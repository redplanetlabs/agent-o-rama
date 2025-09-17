package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
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
 *   <li>Multiple concurrent invocations using shared objects
 * </ul>
 */
public class AgentObjectsAgentTest {

  @Test
  public void testAgentObjectsBasicFunctionality() throws Exception {
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

  @Test
  public void testConcurrentInvocationsWithAgentObjects() throws Exception {
    // Tests that multiple concurrent invocations work correctly with shared objects
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AgentObjectsAgent.AgentObjectsModule module = new AgentObjectsAgent.AgentObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AgentObjectsAgent");

      // Start multiple concurrent invocations
      AgentInvoke invoke1 = agent.initiate("Message1");
      AgentInvoke invoke2 = agent.initiate("Message2");
      AgentInvoke invoke3 = agent.initiate("Message3");

      // Get results
      String result1 = (String) agent.result(invoke1);
      String result2 = (String) agent.result(invoke2);
      String result3 = (String) agent.result(invoke3);

      // Verify all results contain shared objects
      assertNotNull("Result 1 should not be null", result1);
      assertNotNull("Result 2 should not be null", result2);
      assertNotNull("Result 3 should not be null", result3);

      // All should use the same version from static agent object
      assertTrue("Result 1 should contain version v1.2.3", result1.contains("v1.2.3"));
      assertTrue("Result 2 should contain version v1.2.3", result2.contains("v1.2.3"));
      assertTrue("Result 3 should contain version v1.2.3", result3.contains("v1.2.3"));

      // All should use the same send-to from static agent object
      assertTrue("Result 1 should contain alerts", result1.contains("alerts"));
      assertTrue("Result 2 should contain alerts", result2.contains("alerts"));
      assertTrue("Result 3 should contain alerts", result3.contains("alerts"));

      // Each should have counter reset to #1 (thread-unsafe service reset per invocation)
      assertTrue("Result 1 should have counter #1", result1.contains("#1"));
      assertTrue("Result 2 should have counter #1", result2.contains("#1"));
      assertTrue("Result 3 should have counter #1", result3.contains("#1"));

      // Each should contain their specific message
      assertTrue("Result 1 should contain Message1", result1.contains("Message1"));
      assertTrue("Result 2 should contain Message2", result2.contains("Message2"));
      assertTrue("Result 3 should contain Message3", result3.contains("Message3"));
    }
  }

  @Test
  public void testAgentObjectBuilder() throws Exception {
    // Tests that agent object builders work correctly with dependencies
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AgentObjectsAgent.AgentObjectsModule module = new AgentObjectsAgent.AgentObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AgentObjectsAgent");

      // Test that the builder correctly uses the version from static object
      String result = (String) agent.invoke("BuilderTest");

      // The message service should be constructed with version from static object
      String expected = "v1.2.3: BuilderTest (#1 -> alerts)";
      assertEquals("Result should match expected format", expected, result);
    }
  }
}
