package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Java example demonstrating basic agent definition with nested classes.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Agent module definition as nested class extending AgentModule
 *   <li>Node function implementation as nested class
 *   <li>Single-node agent topology
 *   <li>Querying available agent names
 *   <li>Agent invocation and result handling
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class BasicAgent {

  /**
   * Basic Agent Module demonstrating fundamental agent-o-rama concepts.
   *
   * <p>This nested module implements a simple agent with a single node that processes input and
   * returns a result.
   */
  public static class BasicModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("BasicAgent").node("process", null, new ProcessFunction());
    }
  }

  /**
   * Node function that processes input and creates a welcome message.
   *
   * <p>This nested function demonstrates basic agent node processing logic.
   */
  public static class ProcessFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userName) {
      // Extract user name from arguments (corresponds to the value in agent-invoke)

      // Create a welcome message for the user
      String result = "Welcome to agent-o-rama, " + userName + "!";

      // Return the final result
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Basic Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      BasicModule module = new BasicModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      // List all available agents in this module
      System.out.println("Available agents: " + manager.getAgentNames());

      // Get client for our specific agent
      AgentClient agent = manager.getAgentClient("BasicAgent");

      // Invoke agent synchronously with sample user names
      System.out.println("\nBasic Agent Results:");
      System.out.println("User: \"Alice\" -> Result: " + agent.invoke("Alice"));
      System.out.println("User: \"Bob\" -> Result: " + agent.invoke("Bob"));
    }
  }
}
