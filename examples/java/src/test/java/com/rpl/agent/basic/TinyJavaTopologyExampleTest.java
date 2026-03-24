package com.rpl.agent.basic;

import static org.junit.Assert.*;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.RamaModule;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

public class TinyJavaTopologyExampleTest {

  private void assertCounts(AgentClient recorder, AgentClient counter) throws Exception {
    recorder.invoke("login");
    recorder.invoke("login");
    recorder.invoke("purchase");
    recorder.invoke("login");
    Thread.sleep(500);
    assertEquals("login count = 3",    (String) counter.invoke("login"));
    assertEquals("purchase count = 1", (String) counter.invoke("purchase"));
    assertEquals("logout count = 0",   (String) counter.invoke("logout"));
  }

  @Test
  public void testStrategy1AgentOwnedPState() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.AgentOwnedPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));
      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      assertCounts(manager.getAgentClient("RecordAgent"),
                   manager.getAgentClient("CountAgent"));
    }
  }

  @Test
  public void testStrategy2ExternalPState() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new TinyJavaTopologyExample.ExternalPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));
      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      assertCounts(manager.getAgentClient("RecordAgent"),
                   manager.getAgentClient("CountAgent"));
    }
  }
}
