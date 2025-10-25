package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.rama.Depot;
import com.rpl.rama.RamaModule;
import com.rpl.rama.module.StreamTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;

/**
 * Java example demonstrating direct Rama module usage instead of AgentModule.
 *
 * <p>This example demonstrates:
 *
 * <ul>
 *   <li>Implementing RamaModule directly (not extending AgentModule)
 *   <li>Creating AgentTopology manually via AgentTopology.create()
 *   <li>Accessing StreamTopology via getStreamTopology()
 *   <li>Declaring Rama depots alongside agents
 *   <li>Using Rama's stream processing with agents
 *   <li>Explicitly calling topology.define()
 * </ul>
 *
 * <p>This approach allows integration of agent-o-rama with full Rama features when you need access
 * to depots, stream processing, or other Rama primitives.
 */
public class RamaModuleAgent {

  /**
   * Direct Rama Module implementation showing integration with full Rama features.
   *
   * <p>This module demonstrates manual topology creation and depot integration, providing access to
   * the complete Rama feature set alongside agent-o-rama agents.
   */
  public static class RamaModule implements com.rpl.rama.RamaModule {

    public RamaModule() {
      super();
    }

    @Override
    public String getModuleName() {
      return "RamaModuleAgent";
    }

    @Override
    public void define(Setup setup, Topologies topologies) {
      // Declare a depot to demonstrate Rama feature integration
      setup.declareDepot("*example-depot", Depot.random());

      // Create agents topology manually
      AgentTopology topology = AgentTopology.create(setup, topologies);

      // Access underlying stream topology for Rama features (available for custom processing)
      StreamTopology streamTopology = topology.getStreamTopology();

      // Note: Stream topology can be used here for custom Rama stream processing
      // For simplicity, this example focuses on the agent topology integration

      // Define a simple feedback agent
      topology
          .newAgent("FeedbackAgent")
          .node("process-feedback", null, (AgentNode agentNode, String feedbackText) -> {
            // Process feedback and return success response
            HashMap<String, Object> response = new HashMap<>();
            response.put("status", "success");
            response.put("message", "Processed: " + feedbackText);
            response.put("length", feedbackText.length());

            agentNode.result(response);
          });

      // Explicitly finalize agent definitions
      topology.define();
    }
  }


  public static void main(String[] args) throws Exception {
    System.out.println("Starting Rama Module Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the Rama module
      RamaModule module = new RamaModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      System.out.println("\n=== Agent Results ===");
      System.out.println("Available agents: " + manager.getAgentNames());

      // Note: The depot "*example-depot" is declared and available for use
      // For demonstration purposes, we focus on the agent functionality

      // Get client for our agent
      AgentClient agent = manager.getAgentClient("FeedbackAgent");

      // Invoke agent with sample feedback
      Object result1 = agent.invoke("Great product!");
      Object result2 = agent.invoke("Needs improvement");

      System.out.println("Feedback 1: " + result1);
      System.out.println("Feedback 2: " + result2);
    }
  }
}
