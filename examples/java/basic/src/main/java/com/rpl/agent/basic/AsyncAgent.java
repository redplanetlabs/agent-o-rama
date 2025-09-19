package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Java example demonstrating asynchronous agent initiation and result handling.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>agent.initiate: Start agent execution asynchronously
 *   <li>agent.result: Get result from async execution
 *   <li>AgentInvoke handle for tracking execution
 *   <li>Concurrent agent execution patterns
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class AsyncAgent {

  /** Agent Module that simulates processing time in a single node. */
  public static class AsyncAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("AsyncAgent").node("process", null, new ProcessFunction());
    }
  }

  /** Node function that simulates work with processing time. */
  public static class ProcessFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String taskName) {
      System.out.printf("Starting task '%s'%n", taskName);

      // Simulate work
      try {
        Thread.sleep(500);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        return;
      }

      System.out.printf("Completed task '%s'%n", taskName);

      agentNode.result(String.format("Task '%s' completed successfully", taskName));
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Async Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      AsyncAgentModule module = new AsyncAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AsyncAgent");

      System.out.println("Async Agent Example - Starting multiple concurrent tasks");

      // Start multiple async executions of the agent
      AgentInvoke task1Invoke = agent.initiate("Data Processing");
      AgentInvoke task2Invoke = agent.initiate("Report Generation");
      AgentInvoke task3Invoke = agent.initiate("Email Sending");

      System.out.println("All tasks initiated, waiting for completion...");

      // Get results, waiting for each one to complete
      System.out.println("\n--- Results ---");
      System.out.println("Task 3 result: " + agent.result(task3Invoke));
      System.out.println("Task 2 result: " + agent.result(task2Invoke));
      System.out.println("Task 1 result: " + agent.result(task1Invoke));

      System.out.println("\nAll tasks completed!");
    }
  }
}
