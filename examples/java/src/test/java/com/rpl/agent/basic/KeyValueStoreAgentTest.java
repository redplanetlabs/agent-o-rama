package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.Map;
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
 *   <li>HashMap usage for request and response data structures
 * </ul>
 */
public class KeyValueStoreAgentTest {

  @Test
  public void testKeyValueStoreAgent() throws Exception {
    // Tests basic key-value store operations using HashMap
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Test SET operation
      Map<String, Object> setRequest =
          KeyValueStoreAgent.createCounterRequest(
              "test-counter", KeyValueStoreAgent.Operation.SET, 42L);
      Map<String, Object> setResult = (Map<String, Object>) agent.invoke(setRequest);

      assertNotNull("Set result should not be null", setResult);
      assertEquals("Action should be set", "set", setResult.get("action"));
      assertEquals("Counter name should match", "test-counter", setResult.get("counter"));
      assertEquals("Value should be 42", 42L, setResult.get("value"));

      // Test GET operation
      Map<String, Object> getRequest =
          KeyValueStoreAgent.createCounterRequest(
              "test-counter", KeyValueStoreAgent.Operation.GET, null);
      Map<String, Object> getResult = (Map<String, Object>) agent.invoke(getRequest);

      assertNotNull("Get result should not be null", getResult);
      assertEquals("Action should be get", "get", getResult.get("action"));
      assertEquals("Counter name should match", "test-counter", getResult.get("counter"));
      assertEquals("Value should be 42", 42L, getResult.get("value"));
    }
  }
}
