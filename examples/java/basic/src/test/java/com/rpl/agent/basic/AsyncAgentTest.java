package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for AsyncAgent demonstrating asynchronous agent invocation patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>agent.initiate: Starting agent execution asynchronously
 *   <li>agent.result: Getting results from async execution
 *   <li>AgentInvoke handles for tracking execution
 * </ul>
 */
public class AsyncAgentTest {

  @Test
  public void testAsyncAgent() throws Exception {
    // Tests basic async agent invocation and result retrieval
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AsyncAgent.AsyncAgentModule module = new AsyncAgent.AsyncAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AsyncAgent");

      // Test single async invocation
      AgentInvoke invoke = agent.initiate("TestTask");
      assertNotNull("AgentInvoke should not be null", invoke);

      String result = (String) agent.result(invoke);
      assertNotNull("Result should not be null", result);
      assertEquals("Task 'TestTask' completed successfully", result);
    }
  }
}
