package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.CreateEvaluatorOptions;
import com.rpl.agentorama.ExampleRun;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Java example demonstrating evaluator usage for agent performance assessment.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>Declaring custom evaluator builders in the module
 *   <li>Creating evaluators at the manager level (outside agents)
 *   <li>Using evaluators to test agent outputs
 *   <li>Built-in evaluators: conciseness, F1-score, LLM judge
 *   <li>Custom evaluator builders with parameter configuration
 *   <li>Removing evaluators with removeEvaluator
 * </ul>
 */
public class EvaluatorAgent {

  /**
   * Agent module with custom evaluator builders.
   *
   * <p>This module demonstrates how to declare custom evaluator builders for assessing agent
   * outputs. It includes regular evaluators, comparative evaluators, and summary evaluators.
   */
  public static class EvaluatorAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare custom regular evaluator builder
      topology.declareEvaluatorBuilder(
          "length-checker",
          "Checks if text length meets criteria",
          params -> {
            int maxLength = Integer.parseInt(params.getOrDefault("maxLength", "100"));
            return (fetcher, input, referenceOutput, output) -> {
              int outputLength = output.toString().length();
              Map<String, Object> result = new HashMap<>();
              result.put("within-limit?", outputLength <= maxLength);
              result.put("actual-length", outputLength);
              result.put("max-length", maxLength);
              return result;
            };
          });

      // Declare custom comparative evaluator builder
      topology.declareComparativeEvaluatorBuilder(
          "quality-ranker",
          "Ranks outputs by simple quality metric",
          params -> {
            return (fetcher, input, referenceOutput, outputs) -> {
              int bestIndex = 0;
              Object bestOutput = null;
              int bestScore = Integer.MIN_VALUE;

              for (int i = 0; i < outputs.size(); i++) {
                Object output = outputs.get(i);
                String outputStr = output.toString();
                int score = outputStr.length();

                if (outputStr.contains("good")) {
                  score += 10;
                }
                if (outputStr.contains("bad")) {
                  score -= 10;
                }

                if (score > bestScore) {
                  bestScore = score;
                  bestOutput = output;
                  bestIndex = i;
                }
              }

              Map<String, Object> result = new HashMap<>();
              result.put("best-index", bestIndex);
              result.put("best-output", bestOutput);
              result.put("best-score", bestScore);
              return result;
            };
          });

      // Declare custom summary evaluator builder
      topology.declareSummaryEvaluatorBuilder(
          "accuracy-summary",
          "Calculates accuracy across multiple examples",
          params -> {
            return (fetcher, exampleRuns) -> {
              int total = exampleRuns.size();
              int correct = 0;

              for (ExampleRun run : exampleRuns) {
                Object referenceOutput = run.getReferenceOutput();
                Object output = run.getOutput();
                if (referenceOutput.equals(output)) {
                  correct++;
                }
              }

              double accuracy = total > 0 ? (double) correct / total : 0.0;

              Map<String, Object> result = new HashMap<>();
              result.put("total-examples", total);
              result.put("correct-predictions", correct);
              result.put("accuracy", accuracy);
              return result;
            };
          });

      // Simple agent that processes text
      topology.newAgent("TextProcessor").node("process-text", null, new ProcessTextFunction());
    }
  }

  /** Node function that processes text and generates responses. */
  public static class ProcessTextFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String input) {
      String response;
      if (input.length() < 10) {
        response = "short";
      } else if (input.length() < 30) {
        response = "good medium length";
      } else {
        response = "too long";
      }

      System.out.println("Processing input: " + input);
      System.out.println("Generated response: " + response);
      agentNode.result(response);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Evaluator Example:");
    System.out.println("====================");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      EvaluatorAgentModule module = new EvaluatorAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TextProcessor");

      // Create evaluators at the manager level
      System.out.println("\n1. Creating evaluators...");

      // Create a length-based evaluator
      Map<String, String> lengthParams = new HashMap<>();
      lengthParams.put("maxLength", "50");
      manager.createEvaluator(
          "length-50",
          "length-checker",
          lengthParams,
          "Checks if responses are under 50 characters",
          new CreateEvaluatorOptions());

      // Create a conciseness evaluator (built-in)
      Map<String, String> conciseParams = new HashMap<>();
      conciseParams.put("threshold", "30");
      manager.createEvaluator(
          "concise-30",
          "aor/conciseness",
          conciseParams,
          "Built-in conciseness evaluator",
          new CreateEvaluatorOptions());

      // Create F1-score evaluator for classification tasks
      Map<String, String> f1Params = new HashMap<>();
      f1Params.put("positiveValue", "positive");
      manager.createEvaluator(
          "f1-positive",
          "aor/f1-score",
          f1Params,
          "F1 score for sentiment classification",
          new CreateEvaluatorOptions());

      // Create comparative evaluator
      manager.createEvaluator(
          "quality-compare",
          "quality-ranker",
          new HashMap<>(),
          "Ranks outputs by quality metric",
          new CreateEvaluatorOptions());

      // Create summary evaluator
      manager.createEvaluator(
          "accuracy-calc",
          "accuracy-summary",
          new HashMap<>(),
          "Calculates accuracy across examples",
          new CreateEvaluatorOptions());

      System.out.println("Created 5 evaluators");

      // Run the agent to generate some outputs
      System.out.println("\n2. Running agent to generate outputs...");
      String output1 = (String) agent.invoke("Hi");
      String output2 = (String) agent.invoke("This is a test input");
      String output3 =
          (String)
              agent.invoke(
                  "This is a much longer test input that should trigger different behavior");

      System.out.println("  Output 1: " + output1);
      System.out.println("  Output 2: " + output2);
      System.out.println("  Output 3: " + output3);

      // Test evaluators with the outputs
      System.out.println("\n3. Testing evaluators with agent outputs...");

      // Test regular evaluators
      @SuppressWarnings("unchecked")
      Map<String, Object> lengthResult =
          (Map<String, Object>) manager.tryEvaluator("length-50", "Hi", "short", output1);
      @SuppressWarnings("unchecked")
      Map<String, Object> conciseResult =
          (Map<String, Object>)
              manager.tryEvaluator(
                  "concise-30", "This is a test input", "good medium length", output2);

      System.out.println("\nLength evaluator result: " + lengthResult);
      System.out.println("Conciseness evaluator result: " + conciseResult);

      // Test comparative evaluator
      System.out.println("\n4. Testing comparative evaluator...");
      @SuppressWarnings("unchecked")
      Map<String, Object> comparisonResult =
          (Map<String, Object>)
              manager.tryComparativeEvaluator(
                  "quality-compare",
                  "What is best?",
                  "good medium length",
                  List.of("bad short", "good medium length", "okay"));

      System.out.println("Quality comparison result: " + comparisonResult);

      // Test summary evaluator with multiple examples
      System.out.println("\n5. Testing summary evaluator...");
      List<ExampleRun> examples =
          List.of(
              ExampleRun.create("input1", "positive", "positive"),
              ExampleRun.create("input2", "negative", "negative"),
              ExampleRun.create("input3", "positive", "negative"),
              ExampleRun.create("input4", "positive", "positive"));

      @SuppressWarnings("unchecked")
      Map<String, Object> f1Result =
          (Map<String, Object>) manager.trySummaryEvaluator("f1-positive", examples);
      @SuppressWarnings("unchecked")
      Map<String, Object> accuracyResult =
          (Map<String, Object>) manager.trySummaryEvaluator("accuracy-calc", examples);

      System.out.println("F1 score result: " + f1Result);
      System.out.println("Accuracy result: " + accuracyResult);

      // Search for evaluators
      System.out.println("\n6. Searching for evaluators...");
      Set<String> searchResults = manager.searchEvaluators("length");
      System.out.println("Evaluators matching 'length': " + searchResults);

      // Demonstrate removing evaluators
      System.out.println("\n7. Removing evaluators...");
      System.out.println("  Before removal, all evaluators:");
      System.out.println("    " + manager.searchEvaluators(""));

      // Remove a couple of evaluators
      manager.removeEvaluator("length-50");
      manager.removeEvaluator("quality-compare");
      System.out.println("  Removed 'length-50' and 'quality-compare'");

      System.out.println("  After removal, remaining evaluators:");
      System.out.println("    " + manager.searchEvaluators(""));

      // Demonstrate that removing non-existent evaluator doesn't error
      manager.removeEvaluator("non-existent");
      System.out.println("  Removing non-existent evaluator causes no error");

      System.out.println("\nKey takeaways:");
      System.out.println("- Evaluators are created at the manager level, not inside agents");
      System.out.println("- Agents focus on their core logic, evaluators assess their outputs");
      System.out.println("- Different evaluator types serve different assessment needs");
      System.out.println("- Custom evaluators can implement domain-specific logic");
      System.out.println("- Built-in evaluators provide common metrics like F1-score");
      System.out.println("- Evaluators can be removed when no longer needed");
    }
  }
}
