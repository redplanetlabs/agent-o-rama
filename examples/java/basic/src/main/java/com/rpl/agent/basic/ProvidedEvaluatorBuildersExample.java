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
import dev.langchain4j.model.openai.OpenAiChatModel;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java example demonstrating the three built-in evaluator builders.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>aor/llm-judge: AI-powered evaluation with customizable prompt and model
 *   <li>aor/conciseness: Length-based boolean evaluation
 *   <li>aor/f1-score: Classification metrics (F1, precision, recall)
 *   <li>createEvaluator: Creating evaluators with provided builders
 *   <li>tryEvaluator: Testing regular evaluators
 *   <li>trySummaryEvaluator: Testing summary evaluators with multiple examples
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class ProvidedEvaluatorBuildersExample {

  /**
   * Agent module demonstrating provided evaluator builders.
   *
   * <p>This module creates a simple text generator agent and demonstrates how to create and use the
   * three built-in evaluator builders available in agent-o-rama.
   */
  public static class ProvidedEvaluatorBuildersModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare OpenAI model for LLM judge evaluator
      topology.declareAgentObjectBuilder(
          "test-model",
          setup ->
              OpenAiChatModel.builder()
                  .apiKey("test-key") // Test API key for demonstration
                  .modelName("gpt-4o-mini")
                  .temperature(0.0)
                  .build());

      // Simple agent that generates text of different lengths for testing
      topology.newAgent("TextGenerator").node("generate", null, new GenerateTextFunction());
    }
  }

  /**
   * Node function that generates different types of text based on input type.
   *
   * <p>This function demonstrates a simple text generator that produces outputs of varying lengths
   * and content for testing different evaluator builders.
   */
  public static class GenerateTextFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String inputType) {
      String result;
      switch (inputType) {
        case "short":
          result = "Yes";
          break;
        case "medium":
          result = "This is a medium-length response";
          break;
        case "long":
          result =
              "This is a much longer response that contains more detailed information and"
                  + " explanations to demonstrate various length thresholds";
          break;
        case "positive":
          result = "positive";
          break;
        case "negative":
          result = "negative";
          break;
        default:
          result = "default";
      }
      agentNode.result(result);
    }
  }

  /**
   * Creates the three demo evaluators using provided builders.
   *
   * @param manager the agent manager to create evaluators with
   * @return a map containing the created evaluator names for reference
   */
  public static Map<String, String> createDemoEvaluators(AgentManager manager) {
    Map<String, String> evaluatorNames = new HashMap<>();

    // Create LLM judge evaluator (aor/llm-judge)
    Map<String, String> llmParams = new HashMap<>();
    llmParams.put("model", "test-model");
    llmParams.put("temperature", "0.0");
    llmParams.put(
        "prompt",
        "Rate the quality of this response on a scale of 1-10. Input: %input, Expected:"
            + " %referenceOutput, Actual: %output");
    llmParams.put(
        "outputSchema",
        "{\"type\": \"object\", \"properties\": {\"score\": {\"type\": \"integer\", \"minimum\": 0,"
            + " \"maximum\": 10}}, \"required\": [\"score\"]}");

    manager.createEvaluator(
        "quality-judge",
        "aor/llm-judge",
        llmParams,
        "AI-powered quality evaluation",
        new CreateEvaluatorOptions());
    evaluatorNames.put("llm-judge", "quality-judge");

    // Create conciseness evaluator (aor/conciseness)
    Map<String, String> conciseParams = new HashMap<>();
    conciseParams.put("threshold", "25");

    manager.createEvaluator(
        "brief-check",
        "aor/conciseness",
        conciseParams,
        "Checks if response is under 25 characters",
        new CreateEvaluatorOptions());
    evaluatorNames.put("conciseness", "brief-check");

    // Create F1-score evaluator (aor/f1-score)
    Map<String, String> f1Params = new HashMap<>();
    f1Params.put("positiveValue", "positive");

    manager.createEvaluator(
        "sentiment-f1",
        "aor/f1-score",
        f1Params,
        "F1 score for sentiment classification",
        new CreateEvaluatorOptions());
    evaluatorNames.put("f1-score", "sentiment-f1");

    return evaluatorNames;
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Provided Evaluator Builders Example");
    System.out.println("====================================");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      ProvidedEvaluatorBuildersModule module = new ProvidedEvaluatorBuildersModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TextGenerator");

      System.out.println("\n1. Creating evaluators using provided builders...");

      // Create all three demo evaluators
      Map<String, String> evaluatorNames = createDemoEvaluators(manager);
      System.out.println("Created 3 evaluators using provided builders: " + evaluatorNames);

      // Generate some test outputs
      System.out.println("\n2. Generating test outputs...");
      String shortOutput = (String) agent.invoke("short");
      String mediumOutput = (String) agent.invoke("medium");
      String longOutput = (String) agent.invoke("long");
      String posOutput = (String) agent.invoke("positive");
      String negOutput = (String) agent.invoke("negative");

      System.out.println("  Short output: " + shortOutput);
      System.out.println("  Medium output: " + mediumOutput);
      System.out.println("  Long output: " + longOutput);

      // Test aor/conciseness evaluator
      System.out.println("\n3. Testing aor/conciseness evaluator...");
      @SuppressWarnings("unchecked")
      Map<String, Object> shortConcise =
          (Map<String, Object>) manager.tryEvaluator("brief-check", "test", "brief", shortOutput);
      @SuppressWarnings("unchecked")
      Map<String, Object> mediumConcise =
          (Map<String, Object>) manager.tryEvaluator("brief-check", "test", "brief", mediumOutput);
      @SuppressWarnings("unchecked")
      Map<String, Object> longConcise =
          (Map<String, Object>) manager.tryEvaluator("brief-check", "test", "brief", longOutput);

      System.out.println("  Short output concise?: " + shortConcise.get("concise?"));
      System.out.println("  Medium output concise?: " + mediumConcise.get("concise?"));
      System.out.println("  Long output concise?: " + longConcise.get("concise?"));

      // Test aor/f1-score evaluator (summary type - requires multiple examples)
      System.out.println("\n4. Testing aor/f1-score evaluator...");
      List<ExampleRun> examples =
          List.of(
              ExampleRun.create("input1", "positive", posOutput),
              ExampleRun.create("input2", "negative", negOutput),
              ExampleRun.create("input3", "positive", "positive"),
              ExampleRun.create("input4", "negative", "positive")); // This one is wrong

      @SuppressWarnings("unchecked")
      Map<String, Object> f1Result =
          (Map<String, Object>) manager.trySummaryEvaluator("sentiment-f1", examples);

      System.out.println("  F1 Score: " + f1Result.get("score"));
      System.out.println("  Precision: " + f1Result.get("precision"));
      System.out.println("  Recall: " + f1Result.get("recall"));

      // Note about aor/llm-judge
      System.out.println("\n5. About aor/llm-judge evaluator...");
      System.out.println("  The LLM judge evaluator would normally make API calls to OpenAI.");
      System.out.println("  It's created successfully but requires a real API key to execute.");
      System.out.println(
          "  It evaluates by sending input/reference/output to an AI model for scoring.");

      System.out.println("\nKey takeaways:");
      System.out.println("- aor/conciseness: Simple length-based boolean evaluation");
      System.out.println("- aor/f1-score: Classification metrics across multiple examples");
      System.out.println("- aor/llm-judge: AI-powered evaluation with customizable prompts");
      System.out.println("- All provided builders are ready to use with createEvaluator!");
    }
  }
}
