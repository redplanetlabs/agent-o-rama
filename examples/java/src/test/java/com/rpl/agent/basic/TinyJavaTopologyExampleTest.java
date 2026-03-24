package com.rpl.agent.basic;

import static org.junit.Assert.*;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.RamaModule;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

public class TinyJavaTopologyExampleTest {

  /**
   * Strategy 1: PState is owned by the AgentTopology.
   * Agents can read AND write the PState directly (via transform()).
   * ResetAgent demonstrates the direct write path.
   */
  @Test
  public void testStrategy1AgentOwnedPState() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.AgentOwnedPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter  = manager.getAgentClient("CountAgent");
      AgentClient resetter = manager.getAgentClient("ResetAgent");

      // Append events via depot; stream topology increments the PState.
      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 3", (String) counter.invoke("login"));

      // Direct PState write from an agent node (Strategy 1 only).
      resetter.invoke("login");
      assertEquals("login count = 0", (String) counter.invoke("login"));
    }
  }

  /**
   * Strategy 2: PState is owned by an external StreamTopology.
   * Agents access it read-only — there is no ResetAgent in this module.
   * The only way counts change is via the depot → stream topology path.
   */
  @Test
  public void testStrategy2ExternalPState() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.ExternalPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter  = manager.getAgentClient("CountAgent");

      // No ResetAgent exists in this module — agents cannot write the PState directly.
      assertFalse(manager.getAgentNames().contains("ResetAgent"));

      // Counts only change through the depot → stream topology path.
      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("purchase");
      recorder.invoke("login");
      Thread.sleep(500);

      assertEquals("login count = 3",    (String) counter.invoke("login"));
      assertEquals("purchase count = 1", (String) counter.invoke("purchase"));
      assertEquals("logout count = 0",   (String) counter.invoke("logout"));
    }
  }
}
