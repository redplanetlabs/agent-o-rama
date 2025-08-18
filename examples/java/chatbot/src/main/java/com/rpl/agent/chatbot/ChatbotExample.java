package com.rpl.agent.chatbot;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentComplete;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;
import java.util.Map;

/**
 * Main class for running the Chatbot example with memory management.
 *
 * <p>This example demonstrates how to create and run a conversational agent with persistent memory
 * and automatic summarization using the agent-o-rama framework.
 *
 * <p>The agent: - Maintains conversation history per thread - Automatically summarizes long
 * conversations - Uses OpenAI GPT-4o-mini for natural language processing
 *
 * <p>Required environment variables:
 *
 * <ul>
 *   <li>OPENAI_API_KEY: Your OpenAI API key
 * </ul>
 */
public class ChatbotExample {

  private static final String[] CONVERSATION_INPUTS = {
    "hi! I'm Lance",
    "what's my name?",
    "I like The 49'ers",
    "Who was their greatest player of all time?",
    "which team do I like?"
  };

  public static void main(String[] args) {
    // Validate environment variables
    validateEnvironmentVariables();

    System.out.println("Starting Chatbot Example...");
    System.out.println("This agent has memory and will remember our conversation.");
    System.out.println();

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      ChatbotModule module = new ChatbotModule();
      ipc.launchModule(module, new LaunchConfig(4, 2));

      // Get the agent manager and client
      String moduleName = module.getModuleName();
      AgentManager agentManager = AgentManager.create(ipc, moduleName);
      AgentClient agent = agentManager.getAgentClient("ChatbotAgent");

      // Run conversation with predefined inputs
      runConversation(agent);

      System.out.println("Conversation completed. Memory and summarization demonstrated.");

    } catch (Exception e) {
      System.err.println("Error running chatbot: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** Validates that required environment variables are set. */
  private static void validateEnvironmentVariables() {
    String openaiKey = System.getenv("OPENAI_API_KEY");

    if (openaiKey == null || openaiKey.trim().isEmpty()) {
      System.err.println("Error: OPENAI_API_KEY environment variable is not set.");
      System.err.println("Please set your OpenAI API key: export OPENAI_API_KEY=your_key_here");
      System.exit(1);
    }
  }

  /** Runs the predefined conversation. */
  @SuppressWarnings("unchecked")
  private static void runConversation(AgentClient agent) {
    long threadId = 0L;

    for (String input : CONVERSATION_INPUTS) {
      try {
        System.out.println("User: " + input);

        // Create agent invocation with thread configuration
        AgentInvoke agentInvoke =
            agent.initiate(List.of(UserMessage.from(input)), Map.of("thread-id", threadId));

        // Get the result
        AgentComplete result = (AgentComplete) agent.nextStep(agentInvoke);
        Map<String, Object> resultData = (Map<String, Object>) result.getResult();
        List<Object> messages = (List<Object>) resultData.get("messages");

        // Print AI response
        if (messages != null && !messages.isEmpty()) {
          for (Object msg : messages) {
            System.out.println("AI: " + msg);
          }
        }

        System.out.println();

      } catch (Exception e) {
        System.err.println("Error processing input '" + input + "': " + e.getMessage());
        System.out.println();
      }
    }
  }
}
