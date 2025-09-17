package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;

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
 *   <li>State persistence across multiple agent invocations
 * </ul>
 */
public class KeyValueStoreAgentTest {

  @Test
  public void testKeyValueStoreBasicOperations() throws Exception {
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

  @Test
  public void testKeyValueStoreIncrement() throws Exception {
    // Tests increment operation and state persistence
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Test INCREMENT operation (should start at 0 and increment to 1)
      KeyValueStoreAgent.CounterRequest incRequest =
          new KeyValueStoreAgent.CounterRequest(
              "new-counter", KeyValueStoreAgent.CounterRequest.Operation.INCREMENT, null);
      KeyValueStoreAgent.CounterResponse incResult =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(incRequest);

      assertNotNull("Increment result should not be null", incResult);
      assertEquals("Action should be increment", "increment", incResult.getAction());
      assertEquals("Counter name should match", "new-counter", incResult.getCounter());
      assertEquals("Previous value should be 0", (Long) 0L, incResult.getPreviousValue());
      assertEquals("New value should be 1", (Long) 1L, incResult.getNewValue());

      // Test another INCREMENT (should go from 1 to 2)
      KeyValueStoreAgent.CounterResponse incResult2 =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(incRequest);

      assertEquals("Previous value should be 1", (Long) 1L, incResult2.getPreviousValue());
      assertEquals("New value should be 2", (Long) 2L, incResult2.getNewValue());
    }
  }

  @Test
  public void testKeyValueStoreUpdate() throws Exception {
    // Tests update operation
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Set initial value
      KeyValueStoreAgent.CounterRequest setRequest =
          new KeyValueStoreAgent.CounterRequest(
              "update-counter", KeyValueStoreAgent.CounterRequest.Operation.SET, 10L);
      agent.invoke(setRequest);

      // Test UPDATE operation (add 5 to current value)
      KeyValueStoreAgent.CounterRequest updateRequest =
          new KeyValueStoreAgent.CounterRequest(
              "update-counter", KeyValueStoreAgent.CounterRequest.Operation.UPDATE, 5L);
      KeyValueStoreAgent.CounterResponse updateResult =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(updateRequest);

      assertNotNull("Update result should not be null", updateResult);
      assertEquals("Action should be update", "update", updateResult.getAction());
      assertEquals("Counter name should match", "update-counter", updateResult.getCounter());
      assertEquals("Previous value should be 10", (Long) 10L, updateResult.getPreviousValue());
      assertEquals("Added value should be 5", (Long) 5L, updateResult.getAddedValue());
      assertEquals("New value should be 15", (Long) 15L, updateResult.getNewValue());
    }
  }

  @Test
  public void testMultipleCounters() throws Exception {
    // Tests that different counters maintain separate state
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Set up two different counters
      KeyValueStoreAgent.CounterRequest setCounter1 =
          new KeyValueStoreAgent.CounterRequest(
              "counter1", KeyValueStoreAgent.CounterRequest.Operation.SET, 100L);
      KeyValueStoreAgent.CounterRequest setCounter2 =
          new KeyValueStoreAgent.CounterRequest(
              "counter2", KeyValueStoreAgent.CounterRequest.Operation.SET, 200L);

      agent.invoke(setCounter1);
      agent.invoke(setCounter2);

      // Verify they have different values
      KeyValueStoreAgent.CounterRequest getCounter1 =
          new KeyValueStoreAgent.CounterRequest(
              "counter1", KeyValueStoreAgent.CounterRequest.Operation.GET, null);
      KeyValueStoreAgent.CounterRequest getCounter2 =
          new KeyValueStoreAgent.CounterRequest(
              "counter2", KeyValueStoreAgent.CounterRequest.Operation.GET, null);

      KeyValueStoreAgent.CounterResponse result1 =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getCounter1);
      KeyValueStoreAgent.CounterResponse result2 =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getCounter2);

      assertEquals("Counter 1 should be 100", (Long) 100L, result1.getValue());
      assertEquals("Counter 2 should be 200", (Long) 200L, result2.getValue());

      // Increment counter1 and verify counter2 is unchanged
      KeyValueStoreAgent.CounterRequest incCounter1 =
          new KeyValueStoreAgent.CounterRequest(
              "counter1", KeyValueStoreAgent.CounterRequest.Operation.INCREMENT, null);
      agent.invoke(incCounter1);

      KeyValueStoreAgent.CounterResponse newResult1 =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getCounter1);
      KeyValueStoreAgent.CounterResponse newResult2 =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getCounter2);

      assertEquals("Counter 1 should be 101", (Long) 101L, newResult1.getValue());
      assertEquals("Counter 2 should still be 200", (Long) 200L, newResult2.getValue());
    }
  }

  @Test
  public void testGetNonExistentCounter() throws Exception {
    // Tests getting a counter that doesn't exist
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      KeyValueStoreAgent.KeyValueStoreModule module = new KeyValueStoreAgent.KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      // Get non-existent counter
      KeyValueStoreAgent.CounterRequest getRequest =
          new KeyValueStoreAgent.CounterRequest(
              "non-existent", KeyValueStoreAgent.CounterRequest.Operation.GET, null);
      KeyValueStoreAgent.CounterResponse getResult =
          (KeyValueStoreAgent.CounterResponse) agent.invoke(getRequest);

      assertNotNull("Get result should not be null", getResult);
      assertEquals("Action should be get", "get", getResult.getAction());
      assertEquals("Counter name should match", "non-existent", getResult.getCounter());
      assertNull("Value should be null for non-existent counter", getResult.getValue());
    }
  }
}
