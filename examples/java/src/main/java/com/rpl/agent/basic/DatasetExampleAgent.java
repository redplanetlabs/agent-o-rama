package com.rpl.agent.basic;

import com.rpl.agentorama.AddDatasetExampleOptions;
import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * Java example demonstrating dataset example management for agent testing and evaluation.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>addDatasetExample: Add examples to datasets
 *   <li>setDatasetExampleInput: Update example inputs
 *   <li>setDatasetExampleReferenceOutput: Update reference outputs
 *   <li>addDatasetExampleTag: Add tags to examples
 *   <li>removeDatasetExampleTag: Remove tags from examples
 *   <li>removeDatasetExample: Delete examples
 *   <li>Snapshot-specific example operations
 * </ul>
 *
 * <p>Uses Map for calculator request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "operation" (String), "a" (Number), "b" (Number)
 *   <li>Response: "result" (Number or String for errors)
 * </ul>
 */
public class DatasetExampleAgent {

  /** JSON schema for calculator input validation. */
  public static final String MATH_INPUT_SCHEMA =
      "{\"type\": \"object\",\"properties\": {\"operation\": {\"type\": \"string\", \"enum\":"
          + " [\"add\", \"subtract\", \"multiply\", \"divide\"]},\"a\": {\"type\":"
          + " \"number\"},\"b\": {\"type\": \"number\"}},\"required\": [\"operation\", \"a\","
          + " \"b\"]}";

  /** JSON schema for calculator output validation. */
  public static final String MATH_OUTPUT_SCHEMA =
      "{"
          + "\"type\": \"object\","
          + "\"properties\": {"
          + "\"result\": {\"type\": [\"number\", \"string\"]}"
          + "},"
          + "\"required\": [\"result\"]"
          + "}";

  /** Helper method to create a calculator request Map. */
  public static Map<String, Object> createCalculatorRequest(String operation, Number a, Number b) {
    return Map.of("operation", operation, "a", a, "b", b);
  }

  /** Helper method to create a calculator response Map. */
  public static Map<String, Object> createCalculatorResponse(Object result) {
    return Map.of("result", result);
  }

  /** Helper method to create dataset example options with tags and reference output. */
  public static AddDatasetExampleOptions createExampleOptions(
      Object referenceOutput, Set<String> tags, String snapshotName) {
    AddDatasetExampleOptions options = new AddDatasetExampleOptions();
    options.referenceOutput = referenceOutput;
    options.tags = tags;
    options.snapshotName = snapshotName;
    return options;
  }

  /** Agent Module demonstrating dataset example management. */
  public static class DatasetExampleModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("SimpleCalculatorAgent").node("calculate", null, new CalculateFunction());
    }
  }

  /** Node function that performs calculator operations. */
  public static class CalculateFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      String operation = (String) request.get("operation");
      Number a = (Number) request.get("a");
      Number b = (Number) request.get("b");

      Object result;
      switch (operation) {
        case "add":
          result = a.doubleValue() + b.doubleValue();
          break;
        case "subtract":
          result = a.doubleValue() - b.doubleValue();
          break;
        case "multiply":
          result = a.doubleValue() * b.doubleValue();
          break;
        case "divide":
          if (b.doubleValue() == 0.0) {
            result = "Error: Division by zero";
          } else {
            result = a.doubleValue() / b.doubleValue();
          }
          break;
        default:
          result = "Unknown operation";
          break;
      }

      agentNode.result(createCalculatorResponse(result));
    }
  }

  /** Main method demonstrating dataset example management operations. */
  public static void main(String[] args) throws Exception {
    System.out.println("Dataset Example Management Example\n");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      DatasetExampleModule module = new DatasetExampleModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient calcAgent = manager.getAgentClient("SimpleCalculatorAgent");

      // Create dataset for example management
      UUID datasetId =
          manager.createDataset(
              "Math Examples Dataset",
              "Dataset for demonstrating example management",
              MATH_INPUT_SCHEMA,
              MATH_OUTPUT_SCHEMA);

      System.out.println("1. Created dataset: " + datasetId);

      // Add initial examples with different tags and sources
      Set<String> basicAdditionTags = Set.of("basic", "addition");

      UUID example1Id =
          manager.addDatasetExample(
              datasetId,
              createCalculatorRequest("add", 5, 3),
              createExampleOptions(createCalculatorResponse(8.0), basicAdditionTags, null));

      System.out.println("2. Added addition example: " + example1Id);

      Set<String> basicMultiplicationTags = Set.of("basic", "multiplication");

      UUID example2Id =
          manager.addDatasetExample(
              datasetId,
              createCalculatorRequest("multiply", 4, 7),
              createExampleOptions(createCalculatorResponse(28.0), basicMultiplicationTags, null));

      System.out.println("3. Added multiplication example: " + example2Id);

      // Create snapshot to work with
      manager.snapshotDataset(datasetId, null, "v1.0");
      System.out.println("4. Created 'v1.0' snapshot");

      // Add example with snapshot specified
      Set<String> basicSubtractionTags = Set.of("basic", "subtraction");

      UUID example3Id =
          manager.addDatasetExample(
              datasetId,
              createCalculatorRequest("subtract", 10, 3),
              createExampleOptions(createCalculatorResponse(7.0), basicSubtractionTags, "v1.0"));

      System.out.println("5. Added subtraction example to v1.0 snapshot: " + example3Id);

      // Demonstrate tag management
      manager.addDatasetExampleTag(datasetId, null, example1Id, "verified");
      System.out.println("6. Added 'verified' tag to addition example");

      manager.addDatasetExampleTag(datasetId, "v1.0", example2Id, "performance-test");
      System.out.println("7. Added 'performance-test' tag to multiplication example in v1.0");

      // Update example input
      manager.setDatasetExampleInput(
          datasetId, null, example1Id, createCalculatorRequest("add", 10, 5));
      System.out.println("8. Updated addition example input to 10 + 5");

      // Update reference output accordingly
      manager.setDatasetExampleReferenceOutput(
          datasetId, null, example1Id, createCalculatorResponse(15.0));
      System.out.println("9. Updated addition example reference output to 15");

      // Test agent with updated example
      System.out.println("\n10. Testing agent with updated example:");
      Map<String, Object> result =
          (Map<String, Object>) calcAgent.invoke(createCalculatorRequest("add", 10, 5));
      System.out.println("    Input: {\"operation\": \"add\", \"a\": 10, \"b\": 5}");
      System.out.println("    Agent result: " + result + " Expected: {\"result\": 15.0}");

      // Remove a tag
      manager.removeDatasetExampleTag(datasetId, null, example1Id, "basic");
      System.out.println("\n11. Removed 'basic' tag from addition example");

      // Add example with error case
      Set<String> errorTags = Set.of("edge-case", "error");

      UUID errorExampleId =
          manager.addDatasetExample(
              datasetId,
              createCalculatorRequest("divide", 10, 0),
              createExampleOptions(
                  createCalculatorResponse("Error: Division by zero"), errorTags, null));

      System.out.println("12. Added division by zero example: " + errorExampleId);

      // Test the error case
      System.out.println("\n13. Testing agent with error case:");
      Map<String, Object> errorResult =
          (Map<String, Object>) calcAgent.invoke(createCalculatorRequest("divide", 10, 0));
      System.out.println("    Input: {\"operation\": \"divide\", \"a\": 10, \"b\": 0}");
      System.out.println("    Agent result: " + errorResult);

      // Remove an example
      manager.removeDatasetExample(datasetId, null, errorExampleId);
      System.out.println("\n14. Removed division by zero example");

      // Snapshot-specific operations
      manager.setDatasetExampleInput(
          datasetId, "v1.0", example3Id, createCalculatorRequest("subtract", 20, 8));
      System.out.println("15. Updated subtraction example input in v1.0 snapshot");

      System.out.println("\nDemonstrated: Example creation, input/output updates,");
      System.out.println("             tag management, example removal, and snapshot operations");

      manager.close();
    }
  }
}
