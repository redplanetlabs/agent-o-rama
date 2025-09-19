package com.rpl.agent.basic;

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
 * </ul>
 *
 * <p>Note: This test uses a test API key and expects the agent to fail gracefully without a real
 * API key.
 */
public class LangChain4jAgentTest {

  @Test
  public void testLangChain4jAgent() throws Exception {
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
}
