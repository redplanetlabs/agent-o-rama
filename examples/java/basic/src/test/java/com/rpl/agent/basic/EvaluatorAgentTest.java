package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.CreateEvaluatorOptions;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

/** Test for EvaluatorAgent example. */
public class EvaluatorAgentTest {

  @Test
  public void testEvaluatorAgent() throws Exception {
    // Test that the evaluator agent runs properly
    try (InProcessCluster ipc = InProcessCluster.create()) {
      EvaluatorAgent.EvaluatorAgentModule module = new EvaluatorAgent.EvaluatorAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("TextProcessor");

      // Test agent produces expected outputs
      String shortOutput = (String) agent.invoke("Hi");
      String mediumOutput = (String) agent.invoke("This is a test input");
      String longOutput = (String) agent.invoke("This is a much longer test input");

      assertEquals("short", shortOutput);
      assertEquals("good medium length", mediumOutput);
      assertEquals("too long", longOutput);

      // Test evaluators can be created and used
      Map<String, String> lengthParams = new HashMap<>();
      lengthParams.put("maxLength", "20");
      manager.createEvaluator(
          "test-length",
          "length-checker",
          lengthParams,
          "Test length evaluator",
          new CreateEvaluatorOptions());

      @SuppressWarnings("unchecked")
      Map<String, Object> result =
          (Map<String, Object>) manager.tryEvaluator("test-length", "input", "reference", "short");

      assertNotNull(result);
      assertTrue(result.containsKey("within-limit?"));
      assertEquals(true, result.get("within-limit?"));
      assertEquals(5, result.get("actual-length"));
      assertEquals(20, result.get("max-length"));

      // Test removeEvaluator
      Set<String> beforeRemoval = manager.searchEvaluators("");
      assertEquals(1, beforeRemoval.size());
      assertTrue(beforeRemoval.contains("test-length"));

      manager.removeEvaluator("test-length");

      Set<String> afterRemoval = manager.searchEvaluators("");
      assertEquals(0, afterRemoval.size());

      // Test that removing non-existent evaluator doesn't error
      manager.removeEvaluator("non-existent");
    }
  }
}