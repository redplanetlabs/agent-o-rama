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
 * Test class for AsyncAgent demonstrating asynchronous agent invocation patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>agent.initiate: Starting agent execution asynchronously
 *   <li>agent.result: Getting results from async execution
 *   <li>AgentInvoke handles for tracking execution
 *   <li>Concurrent agent execution patterns
 * </ul>
 */
public class AsyncAgentTest {

  @Test
  public void testAsyncAgentBasicFunctionality() throws Exception {
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

  @Test
  public void testMultipleConcurrentAsyncInvocations() throws Exception {
    // Tests multiple concurrent async invocations
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AsyncAgent.AsyncAgentModule module = new AsyncAgent.AsyncAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AsyncAgent");

      // Start multiple concurrent invocations
      AgentInvoke invoke1 = agent.initiate("Task1");
      AgentInvoke invoke2 = agent.initiate("Task2");
      AgentInvoke invoke3 = agent.initiate("Task3");

      // All invokes should be valid
      assertNotNull("Invoke 1 should not be null", invoke1);
      assertNotNull("Invoke 2 should not be null", invoke2);
      assertNotNull("Invoke 3 should not be null", invoke3);

      // Get results (can be in any order)
      String result1 = (String) agent.result(invoke1);
      String result2 = (String) agent.result(invoke2);
      String result3 = (String) agent.result(invoke3);

      // Verify all results
      assertNotNull("Result 1 should not be null", result1);
      assertNotNull("Result 2 should not be null", result2);
      assertNotNull("Result 3 should not be null", result3);

      assertEquals("Task 'Task1' completed successfully", result1);
      assertEquals("Task 'Task2' completed successfully", result2);
      assertEquals("Task 'Task3' completed successfully", result3);
    }
  }

  @Test
  public void testAsyncVsSyncInvocation() throws Exception {
    // Tests that async and sync invocations produce the same results
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AsyncAgent.AsyncAgentModule module = new AsyncAgent.AsyncAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AsyncAgent");

      // Async invocation
      AgentInvoke asyncInvoke = agent.initiate("SameTask");
      String asyncResult = (String) agent.result(asyncInvoke);

      // Sync invocation
      String syncResult = (String) agent.invoke("SameTask");

      // Results should be the same
      assertEquals("Async and sync results should be equal", asyncResult, syncResult);
      assertEquals("Task 'SameTask' completed successfully", asyncResult);
      assertEquals("Task 'SameTask' completed successfully", syncResult);
    }
  }

  @Test
  public void testAsyncProcessingTime() throws Exception {
    // Tests that async invocations actually process (with simulated work)
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      AsyncAgent.AsyncAgentModule module = new AsyncAgent.AsyncAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AsyncAgent");

      // Start async invocation and measure time
      long startTime = System.currentTimeMillis();
      AgentInvoke invoke = agent.initiate("LongTask");

      // The initiate call should return quickly (not wait for completion)
      long initiateTime = System.currentTimeMillis();
      assertTrue(
          "Initiate should return quickly",
          (initiateTime - startTime) < 100); // Should be much less than 500ms processing time

      // Now get the result (this will wait for completion)
      String result = (String) agent.result(invoke);
      long completionTime = System.currentTimeMillis();

      // Total time should be at least 500ms due to simulated work
      assertTrue(
          "Total time should include processing delay",
          (completionTime - startTime) >= 400); // Allow some margin

      assertEquals("Task 'LongTask' completed successfully", result);
    }
  }
}
