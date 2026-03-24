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
   * Strategy 1: AgentTopology owns $$eventCounts.
   * ResetAgent calls transform() directly on the PState — no depot needed.
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

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 3", (String) counter.invoke("login"));

      // Direct PState write from agent node (transform()).
      resetter.invoke("login");
      assertEquals("login count = 0", (String) counter.invoke("login"));
    }
  }

  /**
   * Strategy 2: External StreamTopology owns $$eventCounts.
   * ResetAgent cannot call transform() directly — it appends to *resetCommands
   * and the stream topology applies the write to the PState.
   */
  @Test
  public void testStrategy2ExternalPState() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.ExternalPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter  = manager.getAgentClient("CountAgent");
      AgentClient resetter = manager.getAgentClient("ResetAgent");

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 3", (String) counter.invoke("login"));

      // Write goes through *resetCommands depot → stream topology resets the PState.
      resetter.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 0", (String) counter.invoke("login"));
    }
  }
}
