package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Java example demonstrating cross-module agent invocation using mirror agents.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Multiple agent module definitions
 *   <li>declareClusterAgent: Create mirror reference to agent in another module
 *   <li>getAgentClient: Get client for mirror agent within agent node
 *   <li>invoke: Invoke mirror agent across modules
 *   <li>getModuleName: Get module name for cross-module references
 *   <li>Multiple module deployment to IPC
 * </ul>
 */
public class MirrorAgent {

  /**
   * Module 1: Greeter agent that creates greeting messages.
   *
   * <p>This module contains a simple agent that takes a name and returns a greeting.
   */
  public static class GreeterModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("Greeter").node("greet", null, new GreetFunction());
    }
  }

  /** Node function that creates a greeting message. */
  public static class GreetFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String name) {
      String greeting = "Hello, " + name + "!";
      agentNode.result(greeting);
    }
  }

  /**
   * Module 2: Mirror agent that invokes Greeter from Module 1.
   *
   * <p>This module declares a mirror reference to the Greeter agent and uses it to demonstrate
   * cross-module agent invocation.
   */
  public static class MirrorModule extends AgentsModule {
    private final String greeterModuleName;

    public MirrorModule(String greeterModuleName) {
      this.greeterModuleName = greeterModuleName;
    }

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare mirror reference to Greeter agent in GreeterModule
      topology.declareClusterAgent("GreeterMirror", greeterModuleName, "Greeter");

      topology.newAgent("MirrorAgent").node("process", null, new ProcessFunction());
    }
  }

  /** Node function that invokes the mirror agent and adds a prefix. */
  public static class ProcessFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String name) {
      // Get client for the mirror agent
      AgentClient greeterClient = agentNode.getAgentClient("GreeterMirror");

      // Invoke the mirror agent (cross-module call)
      String greeting = (String) greeterClient.invoke(name);

      // Add prefix to result
      String result = "Mirror says: " + greeting;
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Mirror Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch GreeterModule first
      GreeterModule greeterModule = new GreeterModule();
      ipc.launchModule(greeterModule, new LaunchConfig(1, 1));
      String greeterModuleName = greeterModule.getModuleName();

      // Launch MirrorModule with reference to GreeterModule
      MirrorModule mirrorModule = new MirrorModule(greeterModuleName);
      ipc.launchModule(mirrorModule, new LaunchConfig(1, 1));

      // Get agent manager for MirrorModule
      String mirrorModuleName = mirrorModule.getModuleName();
      AgentManager manager = AgentManager.create(ipc, mirrorModuleName);
      AgentClient mirrorAgent = manager.getAgentClient("MirrorAgent");

      // Invoke the mirror agent
      System.out.println("\nMirror Agent Results:");
      System.out.println("Input: \"Alice\" -> Result: " + mirrorAgent.invoke("Alice"));
      System.out.println("Input: \"Bob\" -> Result: " + mirrorAgent.invoke("Bob"));
    }
  }
}
