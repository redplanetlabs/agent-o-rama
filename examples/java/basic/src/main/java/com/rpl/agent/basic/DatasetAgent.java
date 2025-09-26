package com.rpl.agent.basic;

import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Java example demonstrating dataset creation and lifecycle management for agent testing and
 * evaluation.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>createDataset: Create datasets with input/output schemas
 *   <li>setDatasetName: Update dataset names
 *   <li>setDatasetDescription: Update dataset descriptions
 *   <li>searchDatasets: Find datasets by name/description
 *   <li>snapshotDataset: Create dataset snapshots
 *   <li>removeDatasetSnapshot: Remove snapshots
 *   <li>destroyDataset: Delete entire datasets
 * </ul>
 *
 * <p>Uses Map for calculator request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "operation" (String), "a" (Number), "b" (Number)
 *   <li>Response: "result" (Number or String for errors)
 * </ul>
 */
public class DatasetAgent {

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

  /** Agent Module demonstrating dataset lifecycle management. */
  public static class DatasetModule extends AgentsModule {

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

  /** Main method demonstrating dataset lifecycle management. */
  public static void main(String[] args) throws Exception {
    System.out.println("Dataset Lifecycle Management Example\n");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      DatasetModule module = new DatasetModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);

      // Create initial dataset
      UUID datasetId =
          manager.createDataset(
              "Initial Calculator Dataset",
              "Basic calculator operations dataset",
              MATH_INPUT_SCHEMA,
              MATH_OUTPUT_SCHEMA);

      System.out.println("1. Created dataset: " + datasetId);

      // Update dataset name
      manager.setDatasetName(datasetId, "Advanced Math Dataset");
      System.out.println("2. Updated dataset name to 'Advanced Math Dataset'");

      // Update dataset description
      manager.setDatasetDescription(
          datasetId, "Comprehensive mathematical operations with edge case handling");
      System.out.println("3. Updated dataset description");

      // Create multiple snapshots to demonstrate snapshot management
      manager.snapshotDataset(datasetId, null, "baseline");
      System.out.println("4. Created 'baseline' snapshot");

      manager.snapshotDataset(datasetId, null, "v1.0");
      System.out.println("5. Created 'v1.0' snapshot");

      manager.snapshotDataset(datasetId, null, "experimental");
      System.out.println("6. Created 'experimental' snapshot");

      // Remove a snapshot
      manager.removeDatasetSnapshot(datasetId, "experimental");
      System.out.println("7. Removed 'experimental' snapshot");

      // Search for datasets
      System.out.println("\n8. Searching for datasets:");
      Map<UUID, String> mathResults = manager.searchDatasets("Math", 5);
      Map<UUID, String> calcResults = manager.searchDatasets("Calculator", 5);
      System.out.println("   'Math' search: " + mathResults.size() + " results");
      System.out.println("   'Calculator' search: " + calcResults.size() + " results");

      // Create another dataset to demonstrate multiple datasets
      UUID geometryDatasetId =
          manager.createDataset(
              "Geometry Dataset",
              "Geometric calculations and formulas",
              MATH_INPUT_SCHEMA,
              MATH_OUTPUT_SCHEMA);

      System.out.println("\n9. Created second dataset: " + geometryDatasetId);

      // Search again to show multiple results
      System.out.println("\n10. Updated search results:");
      Map<UUID, String> allResults = manager.searchDatasets("", 10);
      System.out.println("    Total datasets: " + allResults.size());

      // Demonstrate dataset destruction
      manager.destroyDataset(geometryDatasetId);
      System.out.println("11. Destroyed geometry dataset");

      // Final search to confirm deletion
      Map<UUID, String> finalResults = manager.searchDatasets("", 10);
      System.out.println("12. Datasets remaining: " + finalResults.size());

      System.out.println("\nDemonstrated: Dataset creation, name/description updates,");
      System.out.println("             snapshot management, search, and destruction");

      manager.close();
    }
  }
}
