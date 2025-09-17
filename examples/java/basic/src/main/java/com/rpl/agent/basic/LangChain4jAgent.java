package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.agentorama.langchain4j.LangChain4j;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * Java example demonstrating LangChain4j chat model integration with agent-o-rama.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>OpenAI chat model configuration as agent object
 *   <li>Message handling with SystemMessage and UserMessage
 *   <li>Chat request with temperature and token limits
 *   <li>Simple single-node chat completion
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class LangChain4jAgent {

  /** Agent Module demonstrating LangChain4j integration. */
  public static class LangChain4jModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
      // Declare OpenAI API key as agent object
      topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

      // Build OpenAI chat model with configuration
      topology.declareAgentObjectBuilder(
          "openai-model",
          setup -> {
            String apiKey = (String) setup.getAgentObject("openai-api-key");
            return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .maxTokens(500)
                .build();
          });

      topology.newAgent("LangChain4jAgent").node("chat", null, new ChatFunction());
    }
  }

  /** Node function that sends user message to OpenAI and returns response. */
  public static class ChatFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userMessage) {
      OpenAiChatModel model = (OpenAiChatModel) agentNode.getAgentObject("openai-model");

      // Prepare messages
      SystemMessage systemMessage = new SystemMessage("You are a helpful assistant.");
      UserMessage userMsg = new UserMessage(userMessage);

      // Create chat request options
      Map<String, Object> options = new HashMap<>();
      options.put("temperature", 0.7);
      options.put("max-output-tokens", 200);

      // Send chat request to OpenAI
      var response =
          LangChain4j.chat(
              model, LangChain4j.chatRequest(Arrays.asList(systemMessage, userMsg), options));
      String responseText = response.aiMessage().text();

      agentNode.result(responseText);
    }
  }

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("LangChain4j Agent Example:");
      System.out.println("OPENAI_API_KEY environment variable not set.");
      System.out.println("Please set your OpenAI API key to run this example:");
      System.out.println("  export OPENAI_API_KEY=your-api-key-here");
      return;
    }

    System.out.println("Starting LangChain4j Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      LangChain4jModule module = new LangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("LangChain4jAgent");

      System.out.println("LangChain4j Agent Example:");
      System.out.println("Sending message to OpenAI...\n");

      String result = (String) agent.invoke("What is agent-o-rama?");
      System.out.println("User: What is agent-o-rama?");
      System.out.println("\nAssistant: " + result);

      System.out.println("\nNotice how:");
      System.out.println("- OpenAI model is configured as an agent object");
      System.out.println("- Single node handles the complete chat interaction");
      System.out.println("- Temperature and token limits are customizable");
    }
  }
}
