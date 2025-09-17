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
 * Test class for ForkingAgent demonstrating agent execution forking and branching patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>agent.initiateFork: Creating execution branches from existing invocations
 *   <li>agent.fork: Synchronous forking with modified parameters
 *   <li>Branching execution paths with different inputs
 * </ul>
 */
public class ForkingAgentTest {

  @Test
  public void testForkingAgent() throws Exception {
    // Tests basic forking agent functionality
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Test basic execution
      ForkingAgent.ForkingInput input = new ForkingAgent.ForkingInput(4, 3);
      ForkingAgent.ForkingResult result = (ForkingAgent.ForkingResult) agent.invoke(input);

      assertNotNull("Result should not be null", result);
      assertEquals(
          "Action should be calculation-complete", "calculation-complete", result.getAction());
      assertEquals("Processed value should be 12", 12, result.getProcessedValue());
      assertEquals("Squared should be 144", 144, result.getSquared());
      assertEquals("Halved should be 6.0", 6.0, result.getHalved(), 0.01);
      assertTrue("Result should be valid", result.isValid());
    }
  }
}
