package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.MultiAgg;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Java example demonstrating custom aggregation logic with multi-agg for complex data combination.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>multiAgg: Custom aggregation with multiple tagged input streams
 *   <li>init clause: Initialize aggregation state
 *   <li>on clauses: Handle different types of incoming data
 *   <li>Complex aggregation patterns and state management
 * </ul>
 *
 * <p>Uses HashMap for request and response data structures with keys:
 *
 * <ul>
 *   <li>Request: "numbers" (List&lt;Integer&gt;), "text" (List&lt;String&gt;)
 *   <li>Response: "summary" (Map), "details" (Map)
 * </ul>
 */
public class MultiAggAgent {

  /** Agent Module demonstrating multi-agg functionality. */
  public static class MultiAggModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("MultiAggAgent")
          // Start by distributing different types of data
          .aggStartNode(
              "distribute-data",
              List.of("process-numbers", "process-text"),
              (AgentNode agentNode, Map<String, Object> request) -> {
                List<Integer> numbers = (List<Integer>) request.get("numbers");
                List<String> text = (List<String>) request.get("text");

                System.out.println("Distributing data for parallel processing");

                // Send numbers for mathematical analysis
                if (numbers != null) {
                  for (Integer num : numbers) {
                    agentNode.emit("process-numbers", num);
                  }
                }

                // Send text for linguistic analysis
                if (text != null) {
                  for (String txt : text) {
                    agentNode.emit("process-text", txt);
                  }
                }
                return request;
              })
          // Process numbers - compute statistics
          .node("process-numbers", "combine-results", (AgentNode agentNode, Integer number) -> {
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("value", number);
            analysis.put("square", number * number);
            analysis.put("even", number % 2 == 0);

            agentNode.emit("combine-results", "number", analysis);
          })
          // Process text - analyze content
          .node("process-text", "combine-results", (AgentNode agentNode, String text) -> {
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("value", text);
            analysis.put("length", text.length());
            analysis.put("uppercase", text.toUpperCase());
            analysis.put("words", text.split("\\s+").length);

            agentNode.emit("combine-results", "text", analysis);
          })
          // Combine all analysis using multi-agg with tagged inputs
          .aggNode(
              "combine-results",
              null,
              MultiAgg.init(() -> new AggregationState())
                  .on(
                      "number",
                      (AggregationState state, Map<String, Object> analysis) -> {
                        state.numbers.add(analysis);
                        return state;
                      })
                  .on(
                      "text",
                      (AggregationState state, Map<String, Object> analysis) -> {
                        state.text.add(analysis);
                        return state;
                      }),
              (AgentNode agentNode, AggregationState aggregatedState, Object startNodeArg) -> {
                List<Map<String, Object>> numbers = aggregatedState.numbers;
                List<Map<String, Object>> textEntries = aggregatedState.text;

                // Calculate statistics from numbers
                int numberSum = 0;
                int squareSum = 0;
                int evenCount = 0;
                for (Map<String, Object> num : numbers) {
                  numberSum += (Integer) num.get("value");
                  squareSum += (Integer) num.get("square");
                  if ((Boolean) num.get("even")) {
                    evenCount++;
                  }
                }

                // Calculate statistics from text
                int totalWords = 0;
                int totalCharacters = 0;
                for (Map<String, Object> txt : textEntries) {
                  totalWords += (Integer) txt.get("words");
                  totalCharacters += (Integer) txt.get("length");
                }

                System.out.printf(
                    "Processed %d numbers and %d text entries%n", numbers.size(), textEntries.size());

                // Create final result
                Map<String, Object> result = new HashMap<>();
                Map<String, Object> summary = new HashMap<>();
                summary.put("numberSum", numberSum);
                summary.put("squareSum", squareSum);
                summary.put("evenCount", evenCount);
                summary.put("totalWords", totalWords);
                summary.put("totalCharacters", totalCharacters);

                Map<String, Object> details = new HashMap<>();
                details.put("numbers", numbers);
                details.put("text", textEntries);

                result.put("summary", summary);
                result.put("details", details);

                agentNode.result(result);
              });
    }
  }

  /** Custom aggregator state for combining multiple data types. */
  public static class AggregationState implements com.rpl.rama.RamaSerializable {
    public List<Map<String, Object>> numbers = new ArrayList<>();
    public List<Map<String, Object>> text = new ArrayList<>();

    private void writeObject(java.io.ObjectOutputStream out) throws java.io.IOException {
      byte[] ser = com.rpl.agentorama.impl.AORHelpers.freeze(this.toMap());
      out.writeInt(ser.length);
      out.write(ser);
    }

    private void readObject(java.io.ObjectInputStream in)
        throws java.io.IOException, ClassNotFoundException {
      int size = in.readInt();
      byte[] ser = new byte[size];
      in.readFully(ser);
      Map<String, Object> data = (Map<String, Object>) com.rpl.agentorama.impl.AORHelpers.thaw(ser);
      this.fromMap(data);
    }

    private Map<String, Object> toMap() {
      Map<String, Object> map = new HashMap<>();
      map.put("numbers", numbers);
      map.put("text", text);
      return map;
    }

    private void fromMap(Map<String, Object> map) {
      this.numbers = (List<Map<String, Object>>) map.get("numbers");
      this.text = (List<Map<String, Object>>) map.get("text");
    }
  }





  public static void main(String[] args) throws Exception {
    System.out.println("Starting Multi-Agg Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      MultiAggModule module = new MultiAggModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiAggAgent");

      System.out.println("Multi-Agg Agent Example:");
      System.out.println("Processing mixed data types with custom aggregation logic");

      Map<String, Object> request = new HashMap<>();
      request.put("numbers", new ArrayList(List.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10)));
      request.put(
          "text",
          new ArrayList(
              List.of("Hello world", "Multi-agg is powerful", "Parallel processing rocks")));

      Map<String, Object> result = (Map<String, Object>) agent.invoke(request);

      System.out.println("\nResults:");
      Map<String, Object> summary = (Map<String, Object>) result.get("summary");
      System.out.println("  Summary:");
      System.out.println("    Numbers processed: " + summary.get("numbersProcessed"));
      System.out.println("    Text entries processed: " + summary.get("textProcessed"));
      System.out.println("    Sum of numbers: " + summary.get("numberSum"));
      System.out.println("    Sum of squares: " + summary.get("squareSum"));
      System.out.println("    Even numbers: " + summary.get("evenCount"));
      System.out.println("    Total words: " + summary.get("totalWords"));
      System.out.println("    Total characters: " + summary.get("totalCharacters"));

      Map<String, Object> details = (Map<String, Object>) result.get("details");
      List<Map<String, Object>> numberDetails = (List<Map<String, Object>>) details.get("numbers");
      List<Map<String, Object>> textDetails = (List<Map<String, Object>>) details.get("text");

      System.out.println("\n  Sample detailed results:");
      System.out.println("    First number analysis: " + numberDetails.get(0));
      System.out.println("    First text analysis: " + textDetails.get(0));

      System.out.println("\nNotice how:");
      System.out.println("- Multi-agg handles different types of tagged inputs");
      System.out.println("- Each 'on' clause processes specific data types");
      System.out.println("- State accumulation works across multiple input streams");
      System.out.println("- Parallel processing with custom aggregation logic");
    }
  }
}
