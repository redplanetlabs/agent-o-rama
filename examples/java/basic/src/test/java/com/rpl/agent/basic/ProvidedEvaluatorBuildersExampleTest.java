package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.ExampleRun;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/**
 * Test class for ProvidedEvaluatorBuildersExample demonstrating evaluator testing patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Testing agent output generation with different input types
 *   <li>Creating evaluators with provided builders in tests
 *   <li>Testing evaluator functionality with known inputs
 *   <li>Validating evaluator results without requiring external API calls
 * </ul>
 */
public class ProvidedEvaluatorBuildersExampleTest {

  @Test
  public void testTextGeneratorAgent() throws Exception {
    // Test validates that the text generator produces expected outputs
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule module =
          new ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TextGenerator");

      // Test different output types
      assertEquals("Yes", agent.invoke("short"));
      assertEquals("This is a medium-length response", agent.invoke("medium"));
      assertEquals("positive", agent.invoke("positive"));
      assertEquals("negative", agent.invoke("negative"));
    }
  }

  @Test
  public void testEvaluatorCreation() throws Exception {
    // Test validates that evaluators can be created successfully with provided builders
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule module =
          new ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      // Create demo evaluators using the shared function
      Map<String, String> evaluatorNames =
          ProvidedEvaluatorBuildersExample.createDemoEvaluators(manager);

      // Verify evaluators were created
      assertNotNull("Should return a map of evaluator names", evaluatorNames);
      assertTrue("Should create LLM judge evaluator", evaluatorNames.containsKey("llm-judge"));
      assertTrue("Should create conciseness evaluator", evaluatorNames.containsKey("conciseness"));
      assertTrue("Should create F1-score evaluator", evaluatorNames.containsKey("f1-score"));

      // Verify evaluators exist by searching for them
      Set<String> searchResults = manager.searchEvaluators("");
      assertTrue("Should find at least 3 evaluators", searchResults.size() >= 3);
    }
  }

  @Test
  public void testConcisenessEvaluator() throws Exception {
    // Test validates that the conciseness evaluator works correctly
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule module =
          new ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and create evaluators
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      Map<String, String> evaluatorNames =
          ProvidedEvaluatorBuildersExample.createDemoEvaluators(manager);

      String conciseName = evaluatorNames.get("conciseness");

      // Test with outputs of different lengths
      @SuppressWarnings("unchecked")
      Map<String, Object> shortResult =
          (Map<String, Object>) manager.tryEvaluator(conciseName, "test", "ref", "Yes");
      @SuppressWarnings("unchecked")
      Map<String, Object> longResult =
          (Map<String, Object>)
              manager.tryEvaluator(
                  conciseName,
                  "test",
                  "ref",
                  "This is a much longer response that exceeds threshold");

      assertTrue("Short response should be concise", (Boolean) shortResult.get("concise?"));
      assertFalse("Long response should not be concise", (Boolean) longResult.get("concise?"));
    }
  }

  @Test
  public void testF1ScoreEvaluator() throws Exception {
    // Test validates that the F1-score evaluator works correctly
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule module =
          new ProvidedEvaluatorBuildersExample.ProvidedEvaluatorBuildersModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and create evaluators
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      Map<String, String> evaluatorNames =
          ProvidedEvaluatorBuildersExample.createDemoEvaluators(manager);

      String f1Name = evaluatorNames.get("f1-score");

      // Create test examples with known results
      List<ExampleRun> examples =
          List.of(
              ExampleRun.create("input1", "positive", "positive"), // TP
              ExampleRun.create("input2", "negative", "negative"), // TN
              ExampleRun.create("input3", "positive", "negative"), // FN
              ExampleRun.create("input4", "negative", "positive")); // FP

      @SuppressWarnings("unchecked")
      Map<String, Object> f1Result =
          (Map<String, Object>) manager.trySummaryEvaluator(f1Name, examples);

      // Verify the results contain expected metrics
      assertTrue("F1 score should be a number", f1Result.get("score") instanceof Number);
      assertTrue("Precision should be a number", f1Result.get("precision") instanceof Number);
      assertTrue("Recall should be a number", f1Result.get("recall") instanceof Number);

      // Verify F1 score is between 0 and 1
      double score = ((Number) f1Result.get("score")).doubleValue();
      assertTrue("F1 score should be between 0 and 1", score >= 0.0 && score <= 1.0);
    }
  }
}
