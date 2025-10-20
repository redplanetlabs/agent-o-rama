package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java example demonstrating conditional routing between different nodes in an agent graph.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>Conditional routing based on input
 *   <li>Multiple emit calls to different nodes
 *   <li>Branching execution paths that reconverge
 *   <li>Different processing for different input types
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class RouterAgent {

  /** Agent Module that routes messages to different processing nodes based on content. */
  public static class RouterAgentModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("RouterAgent")
          // Router node: decides which processing node to send to
          .node("route", List.of("handle-urgent", "handle-default"), new RouteFunction())
          // Urgent message handler
          .node("handle-urgent", "finalize", new HandleUrgentFunction())
          // Default message handler
          .node("handle-default", "finalize", new HandleDefaultFunction())
          // Final node: creates the result
          // Both urgent and default handlers emit to this node - reconvergence point
          .node("finalize", null, new FinalizeFunction());
    }
  }

  /** Router function that decides which processing node to send to. */
  public static class RouteFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String message) {
      if (message.startsWith("urgent:")) {
        agentNode.emit("handle-urgent", message);
      } else {
        agentNode.emit("handle-default", message);
      }
    }
  }

  /** Urgent message handler that removes prefix and marks as high priority. */
  public static class HandleUrgentFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String message) {
      String content = message.substring(7); // remove "urgent:" prefix
      Map<String, Object> processed = new HashMap<>();
      processed.put("priority", "HIGH");
      processed.put("message", content);
      agentNode.emit("finalize", processed);
    }
  }

  /** Default message handler that marks as normal priority. */
  public static class HandleDefaultFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String message) {
      Map<String, Object> processed = new HashMap<>();
      processed.put("priority", "NORMAL");
      processed.put("message", message);
      agentNode.emit("finalize", processed);
    }
  }

  /** Final node that creates the result from processed message. */
  public static class FinalizeFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> processed) {
      String priority = (String) processed.get("priority");
      String message = (String) processed.get("message");
      String result = String.format("[%s] %s", priority, message);
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Router Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      RouterAgentModule module = new RouterAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RouterAgent");

      System.out.println("Router Agent Results:");

      System.out.println("\n--- Urgent Message ---");
      String result1 = (String) agent.invoke("urgent:system failure detected");
      System.out.println("Result: " + result1);

      System.out.println("\n--- Default Message ---");
      String result2 = (String) agent.invoke("just a regular message");
      System.out.println("Result: " + result2);
    }
  }
}
