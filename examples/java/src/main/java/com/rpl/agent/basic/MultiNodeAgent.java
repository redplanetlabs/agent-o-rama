package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Java example demonstrating agent graphs with multiple nodes and inter-node emissions.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>Agent graph with multiple connected nodes
 *   <li>emit!: Send data from one node to another
 *   <li>Node chaining and data flow
 *   <li>Multi-step greeting process through graph traversal
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class MultiNodeAgent {

  /**
   * Multi-node Agent Module demonstrating greeting workflow through graph.
   *
   * <p>This nested module implements an agent with multiple nodes that process a user name through
   * a greeting workflow: receive -> personalize -> finalize.
   */
  public static class MultiNodeModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("MultiNodeAgent")
          // First node: receive user name from the invoke call and forward it
          .node("receive", "personalize", (AgentNode agentNode, String userName) -> {
            // Forward the user name to the personalize node
            agentNode.emit("personalize", userName);
          })
          // Second node: personalize the greeting message
          .node("personalize", "finalize", (AgentNode agentNode, String userName) -> {
            // Create a personalized greeting message
            String greeting = "Hello, " + userName + "!";

            // Emit both the user name and greeting to the finalize node
            agentNode.emit("finalize", userName, greeting);
          })
          // Final node: create complete welcome message
          .node("finalize", null, (AgentNode agentNode, String userName, String greeting) -> {
            // Create the complete welcome message
            String result =
                greeting + " Welcome to agent-o-rama! " + "Thanks for joining us, " + userName + ".";

            // Return the final result
            agentNode.result(result);
          });
    }
  }




  public static void main(String[] args) throws Exception {
    System.out.println("Starting Multi-Node Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      MultiNodeModule module = new MultiNodeModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiNodeAgent");

      // Invoke agent synchronously with sample user names
      System.out.println("Multi-Node Agent Results:");
      System.out.println();
      System.out.println("--- Greeting Alice ---");
      String result1 = (String) agent.invoke("Alice");
      System.out.println("Result: " + result1);

      System.out.println();
      System.out.println("--- Greeting Bob ---");
      String result2 = (String) agent.invoke("Bob");
      System.out.println("Result: " + result2);

      System.out.println();
      System.out.println("--- Greeting Charlie ---");
      String result3 = (String) agent.invoke("Charlie");
      System.out.println("Result: " + result3);
    }
  }
}
