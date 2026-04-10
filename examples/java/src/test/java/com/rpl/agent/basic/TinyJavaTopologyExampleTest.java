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
   * Wiki patterns 1 & 2: depots/stores from agent nodes + regular RamaModule with
   * AgentTopology.create / define.
   */
  @Test
  public void testWikiAccessingRamaObjectsModule() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.WikiAccessingRamaObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient agent = manager.getAgentClient("myAgent");
      assertEquals("Processed: ping", (String) agent.invoke("ping"));
    }
  }

  /**
   * Stream topology owns $$eventCounts; agents read via PStateStore and write via depots.
   */
  @Test
  public void testWikiStreamPStateModule() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.WikiStreamPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter = manager.getAgentClient("CountAgent");
      AgentClient resetter = manager.getAgentClient("ResetAgent");

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 3", (String) counter.invoke("login"));

      resetter.invoke("login");
      Thread.sleep(500);
      assertEquals("login count = 0", (String) counter.invoke("login"));
    }
  }
}
