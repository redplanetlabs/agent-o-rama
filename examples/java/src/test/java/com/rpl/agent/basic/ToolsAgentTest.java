package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for ToolsAgent demonstrating LangChain4j tools integration.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Tool definitions with @Tool annotation
 *   <li>OpenAI model with tool calling
 *   <li>Natural language to tool execution
 * </ul>
 */
public class ToolsAgentTest {

  @Test
  public void testToolsAgent() throws Exception {
    // Tests tools integration with LangChain4j
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Skipping test - OPENAI_API_KEY not set");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ToolsAgent.ToolsModule module = new ToolsAgent.ToolsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ToolsAgent");

      // Test with a simple math question
      String result = (String) agent.invoke("What is 2 plus 2?");

      assertNotNull("Result should not be null", result);
    }
  }
}
