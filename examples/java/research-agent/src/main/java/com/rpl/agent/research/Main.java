package com.rpl.agent.research;

import com.rpl.agent.research.model.ResearchOptions;
import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.Scanner;

/**
 * Main class for running the Research Agent example.
 *
 * <p>This is a simplified version that demonstrates basic research functionality using the
 * agent-o-rama framework with OpenAI integration.
 *
 * <p>Required environment variables:
 *
 * <ul>
 *   <li>OPENAI_API_KEY: Your OpenAI API key for language processing
 *   <li>TAVILY_API_KEY: Your Tavily search API key for web search capabilities
 * </ul>
 *
 * <p>Example usage:
 *
 * <pre>
 * export OPENAI_API_KEY=your_openai_key_here
 * export TAVILY_API_KEY=your_tavily_key_here
 * mvn exec:java
 * </pre>
 */
public class Main {

  public static void main(String[] args) {
    // Validate environment variables
    validateEnvironmentVariables();

    System.out.println("Starting Research Agent...");
    System.out.println("This agent will analyze your research topic using AI.");
    System.out.println();

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the research agent module
      ResearchAgentModule module = new ResearchAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get the agent manager and client
      String moduleName = module.getModuleName();
      AgentManager agentManager = AgentManager.create(ipc, moduleName);
      AgentClient researcher = agentManager.getAgentClient("researcher");

      // Get research topic from user
      Scanner scanner = new Scanner(System.in);
      System.out.print("Enter a topic: ");
      String topic = scanner.nextLine().trim();

      if (topic.isEmpty()) {
        System.out.println("No topic provided. Exiting...");
        return;
      }

      System.out.println("\nAnalyzing your topic...\n");

      try {
        // Create research options and invoke the agent
        ResearchOptions options = new ResearchOptions(topic);
        Object result = researcher.invoke("", options.toMap());

        System.out.println("=" + "=".repeat(60));
        System.out.println(result);
        System.out.println("=" + "=".repeat(60));

      } catch (Exception e) {
        System.err.println("Error during analysis: " + e.getMessage());
        e.printStackTrace();
      }

      scanner.close();

    } catch (Exception e) {
      System.err.println("Error running research agent: " + e.getMessage());
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
}
