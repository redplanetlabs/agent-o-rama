package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Main class demonstrating basic agent definition with a single node and invocation.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Creating an in-process cluster for testing
 *   <li>Launching an agent module
 *   <li>Creating an agent manager and client
 *   <li>Synchronously invoking agents with sample input
 *   <li>Processing and displaying results
 * </ul>
 *
 * <p>The agent processes user names and returns personalized welcome messages, showcasing the
 * fundamental agent-o-rama workflow from input to output.
 */
public class BasicAgentExample {

  public static void main(String[] args) {
    System.out.println("Starting Basic Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      BasicAgentModule module = new BasicAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("BasicAgent");

      // Invoke agent synchronously with sample user names
      System.out.println("Basic Agent Results:");
      System.out.println("User: \"Alice\" -> Result: " + agent.invoke("Alice"));
      System.out.println("User: \"Bob\" -> Result: " + agent.invoke("Bob"));

    } catch (Exception e) {
      System.err.println("Error running basic agent: " + e.getMessage());
      e.printStackTrace();
      System.exit(1);
    }
  }
}
