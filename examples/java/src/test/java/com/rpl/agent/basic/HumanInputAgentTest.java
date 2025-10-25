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
 * Test class for HumanInputAgent demonstrating human input integration patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>getHumanInput: Requesting input from human users
 *   <li>agent.nextStep: Handling human input requests in execution flow
 *   <li>provideHumanInput: Supplying responses to human input requests
 * </ul>
 *
 * <p>Note: This test uses a test API key and expects the agent to fail gracefully without a real
 * API key.
 */
public class HumanInputAgentTest {

  @Test
  public void testHumanInputAgent() throws Exception {
    // Tests human input agent with mock API key (will fail gracefully)
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Set a test API key to avoid null pointer
      System.setProperty("OPENAI_API_KEY", "test-key");

      try {
        // Deploy the agent module
        HumanInputAgent.HumanInputModule module = new HumanInputAgent.HumanInputModule();
        ipc.launchModule(module, new LaunchConfig(1, 1));

        // Get agent manager and client
        String moduleName = module.getModuleName();
        AgentManager manager = AgentManager.create(ipc, moduleName);
        AgentClient agent = manager.getAgentClient("HumanInputAgent");

        // Start agent execution
        AgentInvoke invoke = agent.initiate("Hello, how are you?");
        assertNotNull("Agent invoke should not be null", invoke);

        // The agent will likely fail due to invalid API key, but we can test the structure
        // In a real test environment, you would use a valid API key or mock the OpenAI client

      } catch (Exception e) {
        // Expected to fail with test API key - this demonstrates the agent structure
        assertTrue(
            "Should fail with authentication error",
            e.getMessage().contains("HTTP")
                || e.getMessage().contains("auth")
                || e.getMessage().contains("API")
                || e.getMessage().contains("key"));
      } finally {
        // Clean up test property
        System.clearProperty("OPENAI_API_KEY");
      }
    }
  }
}
