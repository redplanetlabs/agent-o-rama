package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;

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
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class ForkingAgent {

  /** Initial input for the forking agent. */
  public static class ForkingInput {
    private final int baseValue;
    private final int multiplier;

    public ForkingInput(int baseValue, int multiplier) {
      this.baseValue = baseValue;
      this.multiplier = multiplier;
    }

    public int getBaseValue() {
      return baseValue;
    }

    public int getMultiplier() {
      return multiplier;
    }

    @Override
    public String toString() {
      return String.format("{base-value=%d, multiplier=%d}", baseValue, multiplier);
    }
  }

  /** Intermediate processing data passed between nodes. */
  public static class ProcessingData {
    private final ForkingInput originalInput;
    private final int processedValue;

    public ProcessingData(ForkingInput originalInput, int processedValue) {
      this.originalInput = originalInput;
      this.processedValue = processedValue;
    }

    public ForkingInput getOriginalInput() {
      return originalInput;
    }

    public int getProcessedValue() {
      return processedValue;
    }
  }

  /** Calculation result passed to validation. */
  public static class CalculationData {
    private final ForkingInput originalInput;
    private final int processedValue;
    private final int squared;
    private final double halved;

    public CalculationData(
        ForkingInput originalInput, int processedValue, int squared, double halved) {
      this.originalInput = originalInput;
      this.processedValue = processedValue;
      this.squared = squared;
      this.halved = halved;
    }

    public ForkingInput getOriginalInput() {
      return originalInput;
    }

    public int getProcessedValue() {
      return processedValue;
    }

    public int getSquared() {
      return squared;
    }

    public double getHalved() {
      return halved;
    }
  }

  /** Final result of the forking agent. */
  public static class ForkingResult {
    private final String action;
    private final ForkingInput originalInput;
    private final int processedValue;
    private final int squared;
    private final double halved;
    private final boolean valid;
    private final long completedAt;

    public ForkingResult(
        String action,
        ForkingInput originalInput,
        int processedValue,
        int squared,
        double halved,
        boolean valid,
        long completedAt) {
      this.action = action;
      this.originalInput = originalInput;
      this.processedValue = processedValue;
      this.squared = squared;
      this.halved = halved;
      this.valid = valid;
      this.completedAt = completedAt;
    }

    public String getAction() {
      return action;
    }

    public ForkingInput getOriginalInput() {
      return originalInput;
    }

    public int getProcessedValue() {
      return processedValue;
    }

    public int getSquared() {
      return squared;
    }

    public double getHalved() {
      return halved;
    }

    public boolean isValid() {
      return valid;
    }

    public long getCompletedAt() {
      return completedAt;
    }
  }

  /** Agent Module demonstrating forking functionality. */
  public static class ForkingModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
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
  public static class InitialProcessFunction implements RamaVoidFunction2<AgentNode, ForkingInput> {

    @Override
    public void invoke(AgentNode agentNode, ForkingInput input) {
      System.out.printf(
          "Initial processing: %d * %d%n", input.getBaseValue(), input.getMultiplier());
      int result = input.getBaseValue() * input.getMultiplier();

      ProcessingData data = new ProcessingData(input, result);
      agentNode.emit("calculate", data);
    }
  }

  /** Calculation function that can be forked. */
  public static class CalculateFunction implements RamaVoidFunction2<AgentNode, ProcessingData> {

    @Override
    public void invoke(AgentNode agentNode, ProcessingData data) {
      System.out.printf("Calculating with processed value: %d%n", data.getProcessedValue());
      int squared = data.getProcessedValue() * data.getProcessedValue();
      double halved = data.getProcessedValue() / 2.0;

      CalculationData calcData =
          new CalculationData(data.getOriginalInput(), data.getProcessedValue(), squared, halved);
      agentNode.emit("validate", calcData);
    }
  }

  /** Validation function. */
  public static class ValidateFunction implements RamaVoidFunction2<AgentNode, CalculationData> {

    @Override
    public void invoke(AgentNode agentNode, CalculationData data) {
      System.out.printf(
          "Validating results: squared=%d, halved=%.1f%n", data.getSquared(), data.getHalved());
      boolean isValid =
          data.getProcessedValue() > 0 && data.getSquared() >= data.getProcessedValue();

      ForkingResult result =
          new ForkingResult(
              "calculation-complete",
              data.getOriginalInput(),
              data.getProcessedValue(),
              data.getSquared(),
              data.getHalved(),
              isValid,
              System.currentTimeMillis());

      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Forking Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      ForkingModule module = new ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      System.out.println("Forking Agent Example:");
      System.out.println("Creating execution branches with different parameters");

      // Start base execution
      System.out.println("\n--- Base execution ---");
      AgentInvoke baseInvoke = agent.initiate(new ForkingInput(5, 3));
      ForkingResult baseResult = (ForkingResult) agent.result(baseInvoke);

      System.out.println("Base result:");
      System.out.println("  Original input: " + baseResult.getOriginalInput());
      System.out.println("  Processed value: " + baseResult.getProcessedValue());
      System.out.println("  Squared: " + baseResult.getSquared());
      System.out.println("  Valid: " + baseResult.isValid());

      // Fork without modification - re-runs with same data
      System.out.println("\n--- Fork 1: Re-run with same data ---");
      ForkingResult fork1 = (ForkingResult) agent.fork(baseInvoke, new HashMap<>());
      System.out.println("Fork 1 result:");
      System.out.println("  Processed value: " + fork1.getProcessedValue());
      System.out.println("  Squared: " + fork1.getSquared());
      System.out.println("  Valid: " + fork1.isValid());

      // Fork with async initiation
      System.out.println("\n--- Fork 2: Async fork re-run ---");
      AgentInvoke fork2Invoke = agent.initiateFork(baseInvoke, new HashMap<>());
      ForkingResult fork2Result = (ForkingResult) agent.result(fork2Invoke);
      System.out.println("Fork 2 result:");
      System.out.println("  Processed value: " + fork2Result.getProcessedValue());
      System.out.println("  Squared: " + fork2Result.getSquared());
      System.out.println("  Valid: " + fork2Result.isValid());

      // Another fork example
      System.out.println("\n--- Fork 3: Another fork re-run ---");
      ForkingResult fork3 = (ForkingResult) agent.fork(baseInvoke, new HashMap<>());
      System.out.println("Fork 3 result:");
      System.out.println("  Processed value: " + fork3.getProcessedValue());
      System.out.println("  Squared: " + fork3.getSquared());
      System.out.println("  Halved: " + fork3.getHalved());
      System.out.println("  Valid: " + fork3.isValid());

      System.out.println("\nNotice how:");
      System.out.println("- Forks create independent execution branches");
      System.out.println("- Forks re-run the agent execution independently");
      System.out.println("- Both sync and async forking are supported");
    }
  }
}
