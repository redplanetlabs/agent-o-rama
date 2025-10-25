package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for StructuredLangchain4jAgent demonstrating structured output with LangChain4j.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Structured output with JSON response format
 *   <li>OpenAI chat model integration
 *   <li>Question analysis with structured data
 * </ul>
 */
public class StructuredLangchain4jAgentTest {

  @Test
  public void testStructuredLangchain4jAgent() throws Exception {
    // Tests structured output from LangChain4j
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Skipping test - OPENAI_API_KEY not set");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StructuredLangchain4jAgent.StructuredLangChain4jModule module =
          new StructuredLangchain4jAgent.StructuredLangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StructuredLangChain4jAgent");

      // Test with a simple question
      String result = (String) agent.invoke("What is 2+2?");

      assertNotNull("Result should not be null", result);
    }
  }
}
