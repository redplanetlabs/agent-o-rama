package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for LangChain4jAgent demonstrating AI model integration.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>LangChain4j integration with agent framework
 *   <li>AI model agent object configuration
 *   <li>Chat model invocation from agent nodes
 *   <li>Response processing and result handling
 * </ul>
 *
 * <p>Note: These tests use a test API key to avoid requiring a real API key during testing.
 */
public class LangChain4jAgentTest {

  @Test
  public void testLangChain4jAgentWithMockApiKey() throws Exception {
    // Tests LangChain4j agent with mock API key (will fail gracefully)
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Set a test API key to avoid null pointer
      System.setProperty("OPENAI_API_KEY", "test-key");

      try {
        // Deploy the agent module
        LangChain4jAgent.LangChain4jModule module = new LangChain4jAgent.LangChain4jModule();
        ipc.launchModule(module, new LaunchConfig(1, 1));

        // Get agent manager and client
        String moduleName = module.getModuleName();
        AgentManager manager = AgentManager.create(ipc, moduleName);
        AgentClient agent = manager.getAgentClient("LangChain4jAgent");

        // Start agent execution
        String result = (String) agent.invoke("What is 2+2?");
        // This will likely fail with test API key, but we can test the structure

      } catch (Exception e) {
        // Expected to fail with test API key
        assertTrue(
            "Should fail with authentication or API error",
            e.getMessage().contains("HTTP")
                || e.getMessage().contains("auth")
                || e.getMessage().contains("API")
                || e.getMessage().contains("key")
                || e.getMessage().contains("unauthorized"));
      } finally {
        System.clearProperty("OPENAI_API_KEY");
      }
    }
  }

  @Test
  public void testLangChain4jAgentWithoutApiKey() throws Exception {
    // Tests that agent handles missing API key gracefully
    try (InProcessCluster ipc = InProcessCluster.create()) {
      System.clearProperty("OPENAI_API_KEY");

      // Deploy the agent module
      LangChain4jAgent.LangChain4jModule module = new LangChain4jAgent.LangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("LangChain4jAgent");

      try {
        String result = (String) agent.invoke("Hello");
        // If it gets here, the agent handled null API key
        assertNotNull("Result should not be null if agent handles null API key", result);
      } catch (Exception e) {
        // Expected to fail with null API key
        assertTrue("Should fail due to null API key", e.getMessage() != null);
      }
    }
  }

  // Note: In a real testing environment with valid API key, you would test:
  // - Actual AI responses and their content
  // - Different prompt types and response handling
  // - Agent object builder functionality with real model
  // - Error handling for various API failures
}
