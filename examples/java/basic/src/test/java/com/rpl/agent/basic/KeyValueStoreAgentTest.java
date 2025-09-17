package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import org.junit.Test;

/**
 * Test class for KeyValueStoreAgent demonstrating persistent state management.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>declareKeyValueStore: Creating persistent key-value storage
 *   <li>getStore: Accessing stores from agent nodes
 *   <li>Store operations: get, put, update for persistent state
 * </ul>
 */
public class KeyValueStoreAgentTest {

  @Test
  public void testKeyValueStoreAgent() throws Exception {
    // Tests basic key-value store operations
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Test SET operation
      KeyValueStoreAgent.CounterRequest setRequest =
          new KeyValueStoreAgent.CounterRequest(
              "test-counter", KeyValueStoreAgent.CounterRequest.Operation.SET, 42L);
      KeyValueStoreAgent.CounterResponse setResult =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(setRequest);

      assertNotNull("Set result should not be null", setResult);
      assertEquals("Action should be set", "set", setResult.getAction());
      assertEquals("Counter name should match", "test-counter", setResult.getCounter());
      assertEquals("Value should be 42", (Long) 42L, setResult.getValue());

      // Test GET operation
      KeyValueStoreAgent.CounterRequest getRequest =
          new KeyValueStoreAgent.CounterRequest(
              "test-counter", KeyValueStoreAgent.CounterRequest.Operation.GET, null);
      KeyValueStoreAgent.CounterResponse getResult =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getRequest);

      assertNotNull("Get result should not be null", getResult);
      assertEquals("Action should be get", "get", getResult.getAction());
      assertEquals("Counter name should match", "test-counter", getResult.getCounter());
      assertEquals("Value should be 42", (Long) 42L, getResult.getValue());
    }
  }
}
