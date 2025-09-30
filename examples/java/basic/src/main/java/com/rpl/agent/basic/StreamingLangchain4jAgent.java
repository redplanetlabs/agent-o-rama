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
import dev.langchain4j.model.StreamingResponseHandler;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.response.StreamingChatResponseHandler;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.model.output.Response;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

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
      ChatModel model =
          (ChatModel) agentNode.getAgentObject("openai-streaming-model");

      AtomicReference<String> responseRef = new AtomicReference<>("");

      // Send chat request to streaming OpenAI model
      model.chat(
        List.<ChatMessage>of(new UserMessage(userMessage)) //,
        // new StreamingChatResponseHandler() {
        //     @Override
        //     public void onNext(String token) {
        //       responseRef.set(responseRef.get() + token);
        //     }

        //     @Override
        //     public void onComplete(Response<String> response) {
        //       agentNode.result(responseRef.get());
        //     }

        //     @Override
        //     public void onError(Throwable error) {
        //       agentNode.result("Error: " + error.getMessage());
        //     }
        //   }
                 );
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

    AtomicReference<String> responseRef = new AtomicReference<>("");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      StreamingLangChain4jModule module = new StreamingLangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingLangChain4jAgent");

      System.out.println("Streaming LangChain4j Agent Example:");
      System.out.println("Asking OpenAI a question with streaming...\n");

      System.out.println("User: Explain what machine learning is in simple terms");

      AgentInvoke invoke = agent.initiate("Explain what machine learning is in simple terms");

      agent.stream(invoke, "streaming-chat", new AgentClient.StreamCallback<String>() {
         public void onUpdate(List<String> allChunks,
                       List<String> newChunks,
                       boolean isReset,
                       boolean isComplete) {
           for (String chunk : newChunks) {
             responseRef.set(responseRef.get() + chunk);
           }

         }
        });

      agent.result(invoke);

      System.out.println("\nAssistant: " + responseRef.get());

      System.out.println("\nNotice how:");
      System.out.println("- OpenAI streaming model processes tokens in real-time");
      System.out.println("- StreamingResponseHandler manages the response flow");
      System.out.println("- Final result contains the complete response");
    }
  }
}
