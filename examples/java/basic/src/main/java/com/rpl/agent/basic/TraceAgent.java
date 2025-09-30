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
 * Java example demonstrating agent traces.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>agent-invoke: Synchronously invoke agent
 *   <li>Single-node agent execution
 *   <li>Basic agent tracing
 * </ul>
 *
 * <p>Uses String for request and response data.
 */
public class TraceAgent {

  /** Basic agent module with single node. */
  public static class TraceAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("TraceAgent").node("process", null, new ProcessFunction());
    }
  }

  /** Function that processes input and returns result. */
  public static class ProcessFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userName) {
      // Create a welcome message for the user
      String result = "Welcome to agent-o-rama, " + userName + "!";
      // Return the final result
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      TraceAgentModule module = new TraceAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TraceAgent");

      System.out.println("Trace Agent Example:");

      // Invoke agent synchronously with sample user names
      String result1 = (String) agent.invoke("Alice");
      System.out.println("Result 1: " + result1);

      String result2 = (String) agent.invoke("Bob");
      System.out.println("Result 2: " + result2);

      String result3 = (String) agent.invoke("Charlie");
      System.out.println("Result 3: " + result3);

      System.out.println("\nNotice how:");
      System.out.println("- Agent processes each invocation");
      System.out.println("- Results are returned synchronously");
      System.out.println("- Agent traces can be viewed in the UI");
    }
  }
}
