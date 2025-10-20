package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.store.KeyValueStore;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Java example demonstrating key-value store operations for persistent agent state.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>declareKeyValueStore: Create a key-value store
 *   <li>getStore: Access stores from agent nodes
 *   <li>Store.get: Retrieve values from store
 *   <li>Store.put: Store values in store
 *   <li>Store.update: Update existing values in store
 *   <li>Persistent state across agent invocations
 * </ul>
 *
 * <p>Uses HashMap for request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "operation" (String), "counterName" (String), "value" (Long)
 *   <li>Response: "action" (String), "counter" (String), "value" (Long), "previousValue" (Long),
 *       "newValue" (Long), "addedValue" (Long), "timestamp" (Long)
 * </ul>
 */
public class KeyValueStoreAgent {

  /** Available counter operations. */
  public enum Operation {
    GET,
    INCREMENT,
    SET,
    UPDATE
  }

  /** Helper method to create a counter request HashMap. */
  public static Map<String, Object> createCounterRequest(
      String counterName, Operation operation, Long value) {
    Map<String, Object> request = new HashMap<>();
    request.put("counterName", counterName);
    request.put("operation", operation.toString());
    if (value != null) {
      request.put("value", value);
    }
    return request;
  }

  /** Helper method to create a counter response HashMap with common fields. */
  public static Map<String, Object> createCounterResponse(String action, String counterName) {
    Map<String, Object> response = new HashMap<>();
    response.put("action", action);
    response.put("counter", counterName);
    response.put("timestamp", Instant.now().toEpochMilli());
    return response;
  }

  /** Agent Module demonstrating key-value store usage. */
  public static class KeyValueStoreModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare a key-value store for counters (String -> Long)
      topology.declareKeyValueStore("$$counters", String.class, Long.class);

      topology
          .newAgent("KeyValueStoreAgent")
          .node("manage-counter", null, new ManageCounterFunction());
    }
  }

  /** Node function that manages counter operations using the key-value store. */
  public static class ManageCounterFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      KeyValueStore<String, Long> countersStore = agentNode.getStore("$$counters");
      String counterName = (String) request.get("counterName");
      String operationStr = (String) request.get("operation");
      Operation operation = Operation.valueOf(operationStr);
      Long value = (Long) request.get("value");

      Map<String, Object> result;

      switch (operation) {
        case GET:
          Long currentValue = countersStore.get(counterName);
          result = createCounterResponse("get", counterName);
          result.put("value", currentValue);
          break;

        case INCREMENT:
          Long current = countersStore.get(counterName);
          if (current == null) current = 0L;
          Long newValue = current + 1;
          countersStore.put(counterName, newValue);
          result = createCounterResponse("increment", counterName);
          result.put("previousValue", current);
          result.put("newValue", newValue);
          break;

        case SET:
          countersStore.put(counterName, value);
          result = createCounterResponse("set", counterName);
          result.put("value", value);
          break;

        case UPDATE:
          Long currentVal = countersStore.get(counterName);
          if (currentVal == null) currentVal = 0L;
          Long updatedValue = currentVal + value;
          countersStore.update(counterName, v -> (v == null ? 0L : v) + value);
          result = createCounterResponse("update", counterName);
          result.put("previousValue", currentVal);
          result.put("addedValue", value);
          result.put("newValue", updatedValue);
          break;

        default:
          throw new IllegalArgumentException("Unknown operation: " + operation);
      }

      System.out.printf(
          "Counter '%s' %s: %s%n",
          counterName,
          operation.toString().toLowerCase(),
          result.get("value") != null
              ? result.get("value")
              : result.get("newValue") != null ? result.get("newValue") : "completed");

      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Key-Value Store Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      KeyValueStoreModule module = new KeyValueStoreModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("KeyValueStoreAgent");

      System.out.println("Key-Value Store Agent Example:");

      // Demonstrate different counter operations
      System.out.println("\n--- Setting initial counter value ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result1 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("page-views", Operation.SET, 10L));
      System.out.printf(
          "Result: action=%s, counter=%s, value=%d%n",
          result1.get("action"), result1.get("counter"), result1.get("value"));

      System.out.println("\n--- Getting current counter value ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result2 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("page-views", Operation.GET, null));
      System.out.printf(
          "Result: action=%s, counter=%s, value=%d%n",
          result2.get("action"), result2.get("counter"), result2.get("value"));

      System.out.println("\n--- Incrementing counter ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result3 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("page-views", Operation.INCREMENT, null));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, new-value=%d%n",
          result3.get("action"),
          result3.get("counter"),
          result3.get("previousValue"),
          result3.get("newValue"));

      System.out.println("\n--- Updating counter by adding value ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result4 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("page-views", Operation.UPDATE, 5L));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, added-value=%d, new-value=%d%n",
          result4.get("action"),
          result4.get("counter"),
          result4.get("previousValue"),
          result4.get("addedValue"),
          result4.get("newValue"));

      System.out.println("\n--- Working with different counter ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result5 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("api-calls", Operation.INCREMENT, null));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, new-value=%d%n",
          result5.get("action"),
          result5.get("counter"),
          result5.get("previousValue"),
          result5.get("newValue"));

      System.out.println("\n--- Final state check ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> result6 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("page-views", Operation.GET, null));
      @SuppressWarnings("unchecked")
      Map<String, Object> result7 =
          (Map<String, Object>)
              agent.invoke(createCounterRequest("api-calls", Operation.GET, null));
      System.out.println("page-views final value: " + result6.get("value"));
      System.out.println("api-calls final value: " + result7.get("value"));

      System.out.println("\nNotice how:");
      System.out.println("- Counter values persist across invocations");
      System.out.println("- Different counters maintain separate state");
      System.out.println("- Various store operations (get, put, update) work correctly");
      System.out.println("- HashMap provides flexible key-value data structure");
    }
  }
}
