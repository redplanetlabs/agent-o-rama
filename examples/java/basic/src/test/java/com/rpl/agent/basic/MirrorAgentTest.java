package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for MirrorAgent demonstrating cross-module agent invocation testing.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Deploying multiple modules to IPC
 *   <li>Testing cross-module agent invocation
 *   <li>Verifying mirror agent behavior
 * </ul>
 */
public class MirrorAgentTest {

  @Test
  public void testMirrorAgent() throws Exception {
    // Tests cross-module agent invocation via mirror agent
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy GreeterModule first
      MirrorAgent.GreeterModule greeterModule = new MirrorAgent.GreeterModule();
      ipc.launchModule(greeterModule, new LaunchConfig(1, 1));
      String greeterModuleName = greeterModule.getModuleName();

      // Deploy MirrorModule with reference to GreeterModule
      MirrorAgent.MirrorModule mirrorModule = new MirrorAgent.MirrorModule(greeterModuleName);
      ipc.launchModule(mirrorModule, new LaunchConfig(1, 1));

      // Get agent manager and client for MirrorModule
      String mirrorModuleName = mirrorModule.getModuleName();
      AgentManager manager = AgentManager.create(ipc, mirrorModuleName);
      AgentClient mirrorAgent = manager.getAgentClient("MirrorAgent");

      // Test cross-module invocation
      String result = (String) mirrorAgent.invoke("TestUser");
      assertNotNull("Mirror agent should return a result", result);
      assertEquals("Mirror says: Hello, TestUser!", result);
    }
  }
}
