package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
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
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Test basic execution
      Map<String, Object> input = new HashMap<>();
      input.put("numbers", Arrays.asList(4, 3, 2));
      input.put("operation", "sum");

      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.invoke(input);

      assertNotNull("Result should not be null", result);

      @SuppressWarnings("unchecked")
      List<Integer> resultNumbers = (List<Integer>) result.get("numbers");
      assertEquals("Numbers should match input", Arrays.asList(4, 3, 2), resultNumbers);
      assertEquals("Operation should be sum", "sum", result.get("operation"));
      assertEquals("Result should be 9", Integer.valueOf(9), result.get("result"));
      assertNotNull("Processing time should be set", result.get("processingTime"));
    }
  }
}
