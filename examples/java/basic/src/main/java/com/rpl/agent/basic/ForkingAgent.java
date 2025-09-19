package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java example demonstrating agent execution forking and branching patterns.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>agent.initiateFork: Create execution branches from existing invocations
 *   <li>agent.fork: Synchronous forking with modified parameters
 *   <li>Branching execution paths with different inputs
 *   <li>Fork management and result handling
 * </ul>
 *
 * <p>Uses HashMap<String, Object> for data exchange between nodes.
 */
public class ForkingAgent {

  /** Agent Module demonstrating forking functionality. */
  public static class ForkingModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("ForkingAgent")
          // Initial processing node
          .node("initial-process", "calculate", new InitialProcessFunction())
          // Calculation node that can be forked
          .node("calculate", "validate", new CalculateFunction())
          // Validation node
          .node("validate", null, new ValidateFunction());
    }
  }

  /** Initial processing function. */
  public static class InitialProcessFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> input) {
      @SuppressWarnings("unchecked")
      List<Integer> numbers = (List<Integer>) input.get("numbers");
      String operation = (String) input.get("operation");

      System.out.printf("Initial processing: %s on %s%n", operation, numbers);

      int totalItems = numbers.size();

      Map<String, Object> processingData = new HashMap<>();
      processingData.put("numbers", numbers);
      processingData.put("operation", operation);
      processingData.put("totalItems", totalItems);

      agentNode.emit("calculate", processingData);
    }
  }

  /** Calculation function that can be forked. */
  public static class CalculateFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> data) {
      @SuppressWarnings("unchecked")
      List<Integer> numbers = (List<Integer>) data.get("numbers");
      String operation = (String) data.get("operation");
      int totalItems = (Integer) data.get("totalItems");

      System.out.printf("Calculating %s on %d items: %s%n", operation, totalItems, numbers);

      int result;
      switch (operation) {
        case "sum":
          result = numbers.stream().mapToInt(Integer::intValue).sum();
          break;
        case "product":
          result = numbers.stream().mapToInt(Integer::intValue).reduce(1, (a, b) -> a * b);
          break;
        case "max":
          result = numbers.stream().mapToInt(Integer::intValue).max().orElse(0);
          break;
        default:
          result = 0;
      }

      Map<String, Object> calculationData = new HashMap<>();
      calculationData.put("numbers", numbers);
      calculationData.put("operation", operation);
      calculationData.put("result", result);

      agentNode.emit("validate", calculationData);
    }
  }

  /** Validation function. */
  public static class ValidateFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> data) {
      @SuppressWarnings("unchecked")
      List<Integer> numbers = (List<Integer>) data.get("numbers");
      String operation = (String) data.get("operation");
      int result = (Integer) data.get("result");

      System.out.printf("Validating %s result: %d%n", operation, result);

      long processingTime = System.currentTimeMillis();

      Map<String, Object> forkingResult = new HashMap<>();
      forkingResult.put("numbers", numbers);
      forkingResult.put("operation", operation);
      forkingResult.put("result", result);
      forkingResult.put("processingTime", processingTime);

      agentNode.result(forkingResult);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Forking Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      ForkingModule module = new ForkingModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      System.out.println("Forking Agent Example:");
      System.out.println("Creating execution branches with different parameters");

      // Start base execution
      System.out.println("\n--- Base execution ---");
      Map<String, Object> input = new HashMap<>();
      input.put("numbers", Arrays.asList(5, 3, 8, 2));
      input.put("operation", "sum");

      AgentInvoke baseInvoke = agent.initiate(input);
      @SuppressWarnings("unchecked")
      Map<String, Object> baseResult = (Map<String, Object>) agent.result(baseInvoke);

      System.out.println("Base result:");
      System.out.println("  Numbers: " + baseResult.get("numbers"));
      System.out.println("  Operation: " + baseResult.get("operation"));
      System.out.println("  Result: " + baseResult.get("result"));
      System.out.println("  Processing time: " + baseResult.get("processingTime"));

      // Fork without modification - re-runs with same data
      System.out.println("\n--- Fork 1: Re-run with same data ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> fork1 = (Map<String, Object>) agent.fork(baseInvoke, new HashMap<>());
      System.out.println("Fork 1 result:");
      System.out.println("  Numbers: " + fork1.get("numbers"));
      System.out.println("  Operation: " + fork1.get("operation"));
      System.out.println("  Result: " + fork1.get("result"));
      System.out.println("  Processing time: " + fork1.get("processingTime"));

      // Fork with async initiation
      System.out.println("\n--- Fork 2: Async fork re-run ---");
      AgentInvoke fork2Invoke = agent.initiateFork(baseInvoke, new HashMap<>());
      @SuppressWarnings("unchecked")
      Map<String, Object> fork2Result = (Map<String, Object>) agent.result(fork2Invoke);
      System.out.println("Fork 2 result:");
      System.out.println("  Numbers: " + fork2Result.get("numbers"));
      System.out.println("  Operation: " + fork2Result.get("operation"));
      System.out.println("  Result: " + fork2Result.get("result"));
      System.out.println("  Processing time: " + fork2Result.get("processingTime"));

      // Another fork example
      System.out.println("\n--- Fork 3: Another fork re-run ---");
      @SuppressWarnings("unchecked")
      Map<String, Object> fork3 = (Map<String, Object>) agent.fork(baseInvoke, new HashMap<>());
      System.out.println("Fork 3 result:");
      System.out.println("  Numbers: " + fork3.get("numbers"));
      System.out.println("  Operation: " + fork3.get("operation"));
      System.out.println("  Result: " + fork3.get("result"));
      System.out.println("  Processing time: " + fork3.get("processingTime"));

      System.out.println("\nNotice how:");
      System.out.println("- Forks create independent execution branches");
      System.out.println("- Forks re-run the agent execution independently");
      System.out.println("- Both sync and async forking are supported");
    }
  }
}
