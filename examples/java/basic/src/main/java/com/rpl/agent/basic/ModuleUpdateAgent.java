package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.UpdateMode;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Demonstrates module updates with setUpdateMode.
 *
 * <p>This example shows how agents can continue running when their module is updated, using the
 * CONTINUE update mode to preserve state across updates.
 *
 * <p>Key concepts demonstrated:
 *
 * <ul>
 *   <li>Using setUpdateMode(UpdateMode.CONTINUE) to preserve agent state
 *   <li>IPC deployment with module deploy and update operations
 *   <li>Agent continues execution through module updates
 *   <li>State preservation across updates
 * </ul>
 */
public class ModuleUpdateAgent {

  /**
   * Counter module that demonstrates update mode behavior.
   *
   * <p>The agent counts from 0 to 50, sleeping between increments to allow time for module updates
   * to occur during execution.
   */
  public static class CounterModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("CounterAgent")
          .setUpdateMode(UpdateMode.CONTINUE)
          .node("count", "count", new CountFunction());
    }
  }

  /** Node function that performs counting with recursive emit. */
  public static class CountFunction implements RamaVoidFunction2<AgentNode, Integer> {
    @Override
    public void invoke(AgentNode agentNode, Integer currentCount) {
      int newCount = (currentCount == null) ? 1 : currentCount + 1;
      System.out.println("counting: " + newCount);

      try {
        Thread.sleep(200);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        agentNode.result("Interrupted at count: " + newCount);
        return;
      }

      if (newCount < 50) {
        agentNode.emit("count", newCount);
      } else {
        agentNode.result(newCount);
      }
    }
  }

  /**
   * Demonstrates module update during agent execution.
   *
   * <p>This method:
   *
   * <ol>
   *   <li>Deploys the initial module version
   *   <li>Starts a counter agent
   *   <li>Updates the module while the agent is running
   *   <li>Shows that the agent continues counting after the update
   * </ol>
   */
  public static void demonstrateModuleUpdate() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      System.out.println("\n=== Module Update Example ===\n");

      // Deploy Version 1
      System.out.println("Deploying...");
      CounterModule module = new CounterModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      // Start counter
      System.out.println("\nStarting counter agent...");
      AgentClient agent = manager.getAgentClient("CounterAgent");
      AgentInvoke invokeHandle = agent.initiate(0);

      // Give it time to start counting
      Thread.sleep(2000);

      // Update the module while it's running
      System.out.println("\nUpdating...");
      ipc.updateModule(module);
      System.out.println("Module updated! Agent continues.\n");

      // Get final result
      Integer finalCount = (Integer) agent.result(invokeHandle);
      System.out.println("\nFinal count: " + finalCount);
    }
  }

  public static void main(String[] args) throws Exception {
    demonstrateModuleUpdate();
  }
}
