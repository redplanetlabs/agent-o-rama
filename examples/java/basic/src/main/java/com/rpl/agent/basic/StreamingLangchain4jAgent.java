package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import java.util.List;

/**
 * Java example demonstrating LangChain4j streaming chat model integration.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>OpenAI streaming chat model configuration as agent object
 *   <li>Streaming chat completion
 *   <li>agent-stream subscription for real-time token reception
 * </ul>
 *
 * <p>This example requires OPENAI_API_KEY environment variable to be set.
 */
public class StreamingLangchain4jAgent {

  /** Agent Module demonstrating streaming LangChain4j integration. */
  public static class StreamingLangChain4jModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare OpenAI API key as agent object
      topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

      // Build OpenAI streaming chat model with configuration
      topology.declareAgentObjectBuilder(
          "openai-streaming-model",
          setup -> {
            String apiKey = (String) setup.getAgentObject("openai-api-key");
            return OpenAiStreamingChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.7)
                .build();
          });

      topology
          .newAgent("StreamingLangChain4jAgent")
          .node("streaming-chat", null, new StreamingChatFunction());
    }
  }

  /** Node function that sends user message to streaming OpenAI model. */
  public static class StreamingChatFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userMessage) {
      // Agent objects wrapping StreamingChatModel are returned as ChatModel
      ChatModel model = (ChatModel) agentNode.getAgentObject("openai-streaming-model");

      // Build chat request
      ChatRequest request =
          ChatRequest.builder().messages(List.<ChatMessage>of(new UserMessage(userMessage))).build();

      // Send chat request - streaming happens automatically with agent-o-rama
      ChatResponse response = model.chat(request);
      String responseText = response.aiMessage().text();

      agentNode.result(responseText);
    }
  }

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Streaming LangChain4j Agent Example:");
      System.out.println("OPENAI_API_KEY environment variable not set.");
      System.out.println("Please set your OpenAI API key to run this example:");
      System.out.println("  export OPENAI_API_KEY=your-api-key-here");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      StreamingLangChain4jModule module = new StreamingLangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingLangChain4jAgent");

      System.out.println("Streaming LangChain4j Agent Example:");
      System.out.println("Asking OpenAI a question with streaming...\n");

      System.out.println("User: Explain what machine learning is in simple terms");

      String result = (String) agent.invoke("Explain what machine learning is in simple terms");

      System.out.println("\nAssistant: " + result);

      System.out.println("\nNotice how:");
      System.out.println("- OpenAI streaming model is automatically wrapped by agent-o-rama");
      System.out.println("- Streaming chunks are emitted automatically during execution");
      System.out.println("- Final result contains the complete response");
    }
  }
}
