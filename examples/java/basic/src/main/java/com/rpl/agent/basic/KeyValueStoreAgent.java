package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.agentorama.store.KeyValueStore;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.time.Instant;

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
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class KeyValueStoreAgent {

  /** Request object for counter operations. */
  public static class CounterRequest {
    private final String counterName;
    private final Operation operation;
    private final Long value;

    public CounterRequest(String counterName, Operation operation, Long value) {
      this.counterName = counterName;
      this.operation = operation;
      this.value = value;
    }

    public String getCounterName() {
      return counterName;
    }

    public Operation getOperation() {
      return operation;
    }

    public Long getValue() {
      return value;
    }

    public enum Operation {
      GET,
      INCREMENT,
      SET,
      UPDATE
    }
  }

  /** Response object for counter operations. */
  public static class CounterResponse {
    private final String action;
    private final String counter;
    private final Long value;
    private final Long previousValue;
    private final Long newValue;
    private final Long addedValue;
    private final long processedAt;

    private CounterResponse(Builder builder) {
      this.action = builder.action;
      this.counter = builder.counter;
      this.value = builder.value;
      this.previousValue = builder.previousValue;
      this.newValue = builder.newValue;
      this.addedValue = builder.addedValue;
      this.processedAt = builder.processedAt;
    }

    public static class Builder {
      private String action;
      private String counter;
      private Long value;
      private Long previousValue;
      private Long newValue;
      private Long addedValue;
      private long processedAt = Instant.now().toEpochMilli();

      public Builder action(String action) {
        this.action = action;
        return this;
      }

      public Builder counter(String counter) {
        this.counter = counter;
        return this;
      }

      public Builder value(Long value) {
        this.value = value;
        return this;
      }

      public Builder previousValue(Long previousValue) {
        this.previousValue = previousValue;
        return this;
      }

      public Builder newValue(Long newValue) {
        this.newValue = newValue;
        return this;
      }

      public Builder addedValue(Long addedValue) {
        this.addedValue = addedValue;
        return this;
      }

      public CounterResponse build() {
        return new CounterResponse(this);
      }
    }

    public String getAction() {
      return action;
    }

    public String getCounter() {
      return counter;
    }

    public Long getValue() {
      return value;
    }

    public Long getPreviousValue() {
      return previousValue;
    }

    public Long getNewValue() {
      return newValue;
    }

    public Long getAddedValue() {
      return addedValue;
    }

    public long getProcessedAt() {
      return processedAt;
    }
  }

  /** Agent Module demonstrating key-value store usage. */
  public static class KeyValueStoreModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
      // Declare a key-value store for counters (String -> Long)
      topology.declareKeyValueStore("$$counters", String.class, Long.class);

      topology
          .newAgent("KeyValueStoreAgent")
          .node("manage-counter", null, new ManageCounterFunction());
    }
  }

  /** Node function that manages counter operations using the key-value store. */
  public static class ManageCounterFunction
      implements RamaVoidFunction2<AgentNode, CounterRequest> {

    @Override
    public void invoke(AgentNode agentNode, CounterRequest request) {
      KeyValueStore<String, Long> countersStore = agentNode.getStore("$$counters");
      String counterName = request.getCounterName();

      CounterResponse result;

      switch (request.getOperation()) {
        case GET:
          Long currentValue = countersStore.get(counterName);
          result =
              new CounterResponse.Builder()
                  .action("get")
                  .counter(counterName)
                  .value(currentValue)
                  .build();
          break;

        case INCREMENT:
          Long current = countersStore.get(counterName);
          if (current == null) current = 0L;
          Long newValue = current + 1;
          countersStore.put(counterName, newValue);
          result =
              new CounterResponse.Builder()
                  .action("increment")
                  .counter(counterName)
                  .previousValue(current)
                  .newValue(newValue)
                  .build();
          break;

        case SET:
          countersStore.put(counterName, request.getValue());
          result =
              new CounterResponse.Builder()
                  .action("set")
                  .counter(counterName)
                  .value(request.getValue())
                  .build();
          break;

        case UPDATE:
          Long currentVal = countersStore.get(counterName);
          if (currentVal == null) currentVal = 0L;
          Long updatedValue = currentVal + request.getValue();
          countersStore.update(counterName, v -> (v == null ? 0L : v) + request.getValue());
          result =
              new CounterResponse.Builder()
                  .action("update")
                  .counter(counterName)
                  .previousValue(currentVal)
                  .addedValue(request.getValue())
                  .newValue(updatedValue)
                  .build();
          break;

        default:
          throw new IllegalArgumentException("Unknown operation: " + request.getOperation());
      }

      System.out.printf(
          "Counter '%s' %s: %s%n",
          counterName,
          request.getOperation().toString().toLowerCase(),
          result.getValue() != null
              ? result.getValue()
              : result.getNewValue() != null ? result.getNewValue() : "completed");

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
      CounterResponse result1 =
          (CounterResponse)
              agent.invoke(new CounterRequest("page-views", CounterRequest.Operation.SET, 10L));
      System.out.printf(
          "Result: action=%s, counter=%s, value=%d%n",
          result1.getAction(), result1.getCounter(), result1.getValue());

      System.out.println("\n--- Getting current counter value ---");
      CounterResponse result2 =
          (CounterResponse)
              agent.invoke(new CounterRequest("page-views", CounterRequest.Operation.GET, null));
      System.out.printf(
          "Result: action=%s, counter=%s, value=%d%n",
          result2.getAction(), result2.getCounter(), result2.getValue());

      System.out.println("\n--- Incrementing counter ---");
      CounterResponse result3 =
          (CounterResponse)
              agent.invoke(
                  new CounterRequest("page-views", CounterRequest.Operation.INCREMENT, null));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, new-value=%d%n",
          result3.getAction(),
          result3.getCounter(),
          result3.getPreviousValue(),
          result3.getNewValue());

      System.out.println("\n--- Updating counter by adding value ---");
      CounterResponse result4 =
          (CounterResponse)
              agent.invoke(new CounterRequest("page-views", CounterRequest.Operation.UPDATE, 5L));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, added-value=%d, new-value=%d%n",
          result4.getAction(),
          result4.getCounter(),
          result4.getPreviousValue(),
          result4.getAddedValue(),
          result4.getNewValue());

      System.out.println("\n--- Working with different counter ---");
      CounterResponse result5 =
          (CounterResponse)
              agent.invoke(
                  new CounterRequest("api-calls", CounterRequest.Operation.INCREMENT, null));
      System.out.printf(
          "Result: action=%s, counter=%s, previous-value=%d, new-value=%d%n",
          result5.getAction(),
          result5.getCounter(),
          result5.getPreviousValue(),
          result5.getNewValue());

      System.out.println("\n--- Final state check ---");
      CounterResponse result6 =
          (CounterResponse)
              agent.invoke(new CounterRequest("page-views", CounterRequest.Operation.GET, null));
      CounterResponse result7 =
          (CounterResponse)
              agent.invoke(new CounterRequest("api-calls", CounterRequest.Operation.GET, null));
      System.out.println("page-views final value: " + result6.getValue());
      System.out.println("api-calls final value: " + result7.getValue());

      System.out.println("\nNotice how:");
      System.out.println("- Counter values persist across invocations");
      System.out.println("- Different counters maintain separate state");
      System.out.println("- Various store operations (get, put, update) work correctly");
    }
  }
}
