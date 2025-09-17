package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentNodeFunction;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Single-file Java example demonstrating basic agent definition with nested classes.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Agent module definition as nested class extending AgentsModule
 *   <li>Node function implementation as nested class
 *   <li>Single-node agent topology
 *   <li>Agent invocation and result handling
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class BasicAgentSingleFile {

  /**
   * Basic Agent Module demonstrating fundamental agent-o-rama concepts.
   *
   * <p>This nested module implements a simple agent with a single node that processes input and
   * returns a result.
   */
  public static class BasicModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
      // Create agent with single node that processes input and returns result
      topology.newAgent("BasicAgent").node("process", null, new ProcessFunction());
    }
  }

  /**
   * Node function that processes input and creates a welcome message.
   *
   * <p>This nested function demonstrates basic agent node processing logic.
   */
  public static class ProcessFunction implements AgentNodeFunction {

    @Override
    public void invoke(AgentNode agentNode, Object... args) {
      // Extract user name from arguments (corresponds to the value in agent-invoke)
      String userName = (String) args[0];

      // Create a welcome message for the user
      String result = "Welcome to agent-o-rama, " + userName + "!";

      // Return the final result
      agentNode.result(result);
    }
  }

  public static void main(String[] args) {
    System.out.println("Starting Basic Agent Single-File Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      BasicModule module = new BasicModule();
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
