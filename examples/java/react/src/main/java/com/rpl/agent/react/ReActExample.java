package com.rpl.agent.react;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Scanner;

/**
 * Main class for running the ReAct agent example.
 *
 * <p>This example demonstrates how to create and run a ReAct (Reasoning and Acting) agent that can
 * search the web and answer questions using the agent-o-rama framework.
 *
 * <p>The agent uses: - OpenAI GPT-4o-mini for language processing - Tavily for web search
 * capabilities - ReAct pattern for alternating between reasoning and action
 *
 * <p>Required environment variables:
 * <ul>
 *   <li>OPENAI_API_KEY: Your OpenAI API key</li>
 *   <li>TAVILY_API_KEY: Your * Tavily search API key</li>
 * </ul>
 */
public class ReActExample {

  private static final String SYSTEM_PROMPT = "You are a helpful AI assistant.\n\nSystem time: %s";

  public static void main(String[] args) {
    // Validate environment variables
    validateEnvironmentVariables();

    System.out.println("Starting ReAct Agent Example...");
    System.out.println("This agent can search the web to answer your questions.");
    System.out.println();

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      ReActModule module = new ReActModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get the agent manager and client
      String moduleName = module.getModuleName();
      AgentManager agentManager = AgentManager.create(ipc, moduleName);
      AgentClient agent = agentManager.getAgentClient("ReActAgent");

      // Interactive loop
      runInteractiveSession(agent);

    } catch (Exception e) {
      System.err.println("Error running ReAct agent: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }

  /** Validates that required environment variables are set. */
  private static void validateEnvironmentVariables() {
    String openaiKey = System.getenv("OPENAI_API_KEY");
    String tavilyKey = System.getenv("TAVILY_API_KEY");

    if (openaiKey == null || openaiKey.trim().isEmpty()) {
      System.err.println("Error: OPENAI_API_KEY environment variable is not set.");
      System.err.println("Please set your OpenAI API key: export OPENAI_API_KEY=your_key_here");
      System.exit(1);
    }

    if (tavilyKey == null || tavilyKey.trim().isEmpty()) {
      System.err.println("Error: TAVILY_API_KEY environment variable is not set.");
      System.err.println("Please set your Tavily API key: export TAVILY_API_KEY=your_key_here");
      System.exit(1);
    }
  }

  /** Runs an interactive session with the user. */
  private static void runInteractiveSession(AgentClient agent) {
    Scanner scanner = new Scanner(System.in);

    while (true) {
      System.out.print("Ask your question (agent has web search access): ");
      String userInput = scanner.nextLine().trim();

      // Exit conditions
      if (userInput.equalsIgnoreCase("exit")
          || userInput.equalsIgnoreCase("quit")
          || userInput.isEmpty()) {
        System.out.println("Goodbye!");
        break;
      }

      try {
        // Create messages with system prompt and user input
        List<Object> messages =
            Arrays.asList(
                SystemMessage.from(String.format(SYSTEM_PROMPT, Instant.now())),
                UserMessage.from(userInput));

        System.out.println("\nThinking...");

        // Invoke the agent and get response
        Object result = agent.invoke(messages);

        System.out.println("\nAgent: " + result);
        System.out.println();

      } catch (Exception e) {
        System.err.println("Error processing request: " + e.getMessage());
        System.out.println();
      }
    }

    scanner.close();
  }
}
