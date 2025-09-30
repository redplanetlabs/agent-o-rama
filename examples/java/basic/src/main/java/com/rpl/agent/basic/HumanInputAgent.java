package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentStep;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.HumanInputRequest;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Scanner;

/**
 * Java example demonstrating human input requests and handling within agent nodes.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>getHumanInput: Request input from human users
 *   <li>agent.nextStep: Handle human input requests in execution flow
 *   <li>provideHumanInput: Supply responses to human input requests
 *   <li>pendingHumanInputs: List all pending human input requests
 *   <li>isAgentInvokeComplete: Check if an agent invocation has completed
 *   <li>Human-in-the-loop agent execution patterns
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class HumanInputAgent {

  /** Agent Module demonstrating human input integration with AI models. */
  public static class HumanInputModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare OpenAI model builder
      topology.declareAgentObjectBuilder(
          "openai",
          setup -> {
            String apiKey = System.getenv("OPENAI_API_KEY");
            return OpenAiChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
          });

      topology.newAgent("HumanInputAgent").node("chat", null, new ChatFunction());
    }
  }

  /** Node function that chats with AI and asks for human feedback. */
  public static class ChatFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userMessage) {
      // NOTE you can not use OpenAiChatModel as the type here
      ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");

      // Get AI response
      String response = openai.chat(userMessage);

      // Ask human if response was helpful
      boolean helpful = isHumanHelpful(agentNode, response);

      // Return result as HashMap
      // Expected structure: {"response": String, "helpful": boolean}
      Map<String, Object> result = new HashMap<>();
      result.put("response", response);
      result.put("helpful", helpful);
      agentNode.result(result);
    }

    /** Ask user if the response was helpful and loop until valid y/n answer. */
    private boolean isHumanHelpful(AgentNode agentNode, String response) {
      while (true) {
        String input =
            agentNode.getHumanInput(
                String.format("AI Response: %s%n%nWas this response helpful? (y/n): ", response));

        if ("y".equals(input)) {
          return true;
        } else if ("n".equals(input)) {
          return false;
        } else {
          // Loop again with clarification
          input = agentNode.getHumanInput("Please answer 'y' or 'n'.");
        }
      }
    }
  }

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null) {
      apiKey = System.getProperty("OPENAI_API_KEY");
    }
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Human Input Agent Example:");
      System.out.println("OPENAI_API_KEY environment variable not set.");
      System.out.println("Please set your OpenAI API key to run this example:");
      System.out.println("  export OPENAI_API_KEY=your-api-key-here");
      return;
    }

    System.out.println("Starting Human Input Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create();
        Scanner scanner = new Scanner(System.in)) {

      // Launch the agent module
      HumanInputModule module = new HumanInputModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("HumanInputAgent");

      System.out.print("Enter your message: ");
      String userMessage = scanner.nextLine();

      System.out.println();
      System.out.println(userMessage);

      AgentInvoke invoke = agent.initiate(userMessage);

      System.out.println(
          String.format("\nAgent invoke complete? %s", agent.isAgentInvokeComplete(invoke)));

      // Handle execution steps including human input requests
      AgentStep step = agent.nextStep(invoke);
      while (step instanceof HumanInputRequest) {
        HumanInputRequest humanInput = (HumanInputRequest) step;

        // Check for multiple pending human inputs
        List<HumanInputRequest> pending = agent.pendingHumanInputs(invoke);
        if (pending.size() > 1) {
          System.out.println(String.format("\n[%d pending human input requests]", pending.size()));
        }

        System.out.println(humanInput.getPrompt());
        System.out.print(">> ");
        String response = scanner.nextLine();

        agent.provideHumanInput(humanInput, response);
        System.out.println();

        step = agent.nextStep(invoke);
      }

      // Get final result as HashMap
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.result(invoke);
      System.out.println("Final result:");
      System.out.println("Response: " + result.get("response"));
      System.out.println("Helpful: " + result.get("helpful"));

      System.out.println(
          String.format("\nAgent invoke complete? %s", agent.isAgentInvokeComplete(invoke)));

      System.out.println("\nNotice how:");
      System.out.println("- Agents can request human input during execution");
      System.out.println(
          "- instanceof HumanInputRequest checks if a step is a human input request");
      System.out.println("- isAgentInvokeComplete checks if an agent invocation has completed");
      System.out.println("- pendingHumanInputs lists all pending requests");
      System.out.println("- Input validation and defaults are handled gracefully");
      System.out.println("- Human responses influence the final result");
    }
  }
}
