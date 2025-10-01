package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;

/**
 * Java example demonstrating LangChain4j tools integration with OpenAI chat models.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>Tool definition with @Tool annotation
 *   <li>OpenAI model with tool calling capabilities
 *   <li>Natural language to tool execution workflow
 *   <li>AI Services integration
 * </ul>
 *
 * <p>This example requires OPENAI_API_KEY environment variable to be set.
 */
public class ToolsAgent {

  /** Calculator tool for basic arithmetic operations. */
  public static class Calculator implements java.io.Serializable {
    private static final long serialVersionUID = 1L;

    @Tool("Adds two numbers together")
    public double add(double a, double b) {
      return a + b;
    }

    @Tool("Subtracts second number from first")
    public double subtract(double a, double b) {
      return a - b;
    }

    @Tool("Multiplies two numbers")
    public double multiply(double a, double b) {
      return a * b;
    }

    @Tool("Divides first number by second")
    public double divide(double a, double b) {
      if (b == 0) {
        throw new IllegalArgumentException("Cannot divide by zero");
      }
      return a / b;
    }
  }

  /** AI Assistant interface for tool calling. */
  public interface MathAssistant {
    String chat(String userMessage);
  }

  /** Agent Module demonstrating tools functionality. */
  public static class ToolsModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare OpenAI API key
      topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

      // Build OpenAI chat model with configuration
      topology.declareAgentObjectBuilder(
          "openai-model",
          setup -> {
            String apiKey = (String) setup.getAgentObject("openai-api-key");
            return OpenAiChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
          });

      // Declare calculator tool
      topology.declareAgentObject("calculator", new Calculator());

      topology.newAgent("ToolsAgent").node("chat-with-tools", null, new ChatWithToolsFunction());
    }
  }

  /** Node function that processes prompts using OpenAI with tools. */
  public static class ChatWithToolsFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String prompt) {
      ChatModel model = (ChatModel) agentNode.getAgentObject("openai-model");
      Calculator calculator = (Calculator) agentNode.getAgentObject("calculator");

      // Create AI service with tools
      MathAssistant assistant =
          AiServices.builder(MathAssistant.class)
              .chatModel(model)
              .tools(calculator)
              .build();

      // Process prompt and return response
      String response = assistant.chat(prompt);
      agentNode.result(response);
    }
  }

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Tools Agent Example:");
      System.out.println("OPENAI_API_KEY environment variable not set.");
      System.out.println("Please set your OpenAI API key to run this example:");
      System.out.println("  export OPENAI_API_KEY=your-api-key-here");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      ToolsModule module = new ToolsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ToolsAgent");

      System.out.println("Tools Agent Example:");
      System.out.println("Using LangChain4j tools with OpenAI...\n");

      // Test with different prompts that trigger tool usage
      String[] prompts = {
        "What is 15 plus 25?",
        "Calculate 7 times 8",
        "Divide 100 by 4",
        "What's the result of 50 minus 12?"
      };

      for (String prompt : prompts) {
        System.out.println("User: " + prompt);
        String result = (String) agent.invoke(prompt);
        System.out.println("Assistant: " + result);
        System.out.println();
      }

      System.out.println("Notice how:");
      System.out.println("- OpenAI automatically calls appropriate tools");
      System.out.println("- Natural language is translated to tool execution");
      System.out.println("- Results are formatted in natural language responses");
    }
  }
}
