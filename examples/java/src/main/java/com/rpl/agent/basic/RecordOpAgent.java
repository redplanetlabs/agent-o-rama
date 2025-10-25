package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.NestedOpType;
import com.rpl.agentorama.UI;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Scanner;

/**
 * Java example demonstrating recording custom operations in agent traces.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>recordNestedOp: Add custom operation info to agent trace
 *   <li>UIServer.start: Launch web UI for viewing traces
 *   <li>Agent trace visualization in UI
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class RecordOpAgent {

  /**
   * RecordOp Agent Module demonstrating trace recording.
   *
   * <p>This module implements an agent that records custom operations in its trace for debugging
   * and monitoring purposes.
   */
  public static class RecordOpModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("RecordOpAgent").node("process", null, (AgentNode agentNode, String userName) -> {
        // Record timing of a custom operation
        long startTime = System.currentTimeMillis();

        // Simulate some work
        String greeting = "Hello, " + userName + "!";
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
        }

        long finishTime = System.currentTimeMillis();

        // Record the operation in the agent trace
        Map<String, Object> info = new HashMap<>();
        info.put("operation", "generate-greeting");
        info.put("input", userName);
        info.put("output", greeting);

        agentNode.recordNestedOp(NestedOpType.OTHER, startTime, finishTime, info);

        agentNode.result(greeting);
      });
    }
  }


  public static void main(String[] args) throws Exception {
    System.out.println("Starting RecordOp Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create();
        AutoCloseable ui = UI.start(ipc, UI.Options.noInputBeforeClose())) {

      // Launch the agent module
      RecordOpModule module = new RecordOpModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RecordOpAgent");

      System.out.println("RecordOp Agent Example");
      System.out.println("======================\n");

      System.out.println("Result: " + agent.invoke("Alice"));
      System.out.println("Result: " + agent.invoke("Bob"));

      System.out.println("\n✓ Agent invocations complete!");
      System.out.println("\nTo view the recorded operations in the trace:");
      System.out.println("  1. Open the UI at http://localhost:8080");
      System.out.println("  2. Click on an agent invocation");
      System.out.println("  3. Look for the 'generate-greeting' operation in the trace details");
      System.out.println("\nPress Enter to exit and shut down the UI...");

      Scanner scanner = new Scanner(System.in);
      scanner.nextLine();
    }
  }
}
