package com.rpl.agent.basic;

import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for StreamingLangchain4jAgent demonstrating streaming chat with LangChain4j.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>OpenAI streaming chat model integration
 *   <li>Streaming response handling
 *   <li>Real-time token processing
 * </ul>
 */
public class StreamingLangchain4jAgentTest {

  @Test
  public void testStreamingLangchain4jAgent() throws Exception {
    // Tests streaming output from LangChain4j
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Skipping test - OPENAI_API_KEY not set");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamingLangchain4jAgent.StreamingLangChain4jModule module =
          new StreamingLangchain4jAgent.StreamingLangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingLangChain4jAgent");

      // Test with a simple question
      String result = (String) agent.invoke("What is 2+2?");

      assertNotNull("Result should not be null", result);
    }
  }
}
