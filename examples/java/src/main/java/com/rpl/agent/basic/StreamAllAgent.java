package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Java example demonstrating subscribing to streaming chunks across multiple agent invocations.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>streamChunk: Emit streaming data from nodes
 *   <li>agent.streamAll: Subscribe to streaming data from all invocations of a node
 *   <li>Multiple agent invocations with single subscription
 *   <li>Tracking chunks by invoke ID
 *   <li>Invoke ID mapping in streaming callbacks
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class StreamAllAgent {

  /** Agent Module demonstrating stream-all functionality. */
  public static class StreamAllAgentModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("StreamAllAgent").node("process-task", null, new ProcessTaskFunction());
    }
  }

  /** Node function that processes a task and streams progress updates. */
  public static class ProcessTaskFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      String taskId = (String) request.get("taskId");
      int itemsToProcess = (Integer) request.get("itemsToProcess");

      System.out.printf("%nProcessing task %s with %d items%n", taskId, itemsToProcess);

      // Stream progress as we process items
      for (int itemNum = 0; itemNum < itemsToProcess; itemNum++) {
        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }

        // Stream progress update
        Map<String, Object> chunk = new HashMap<>();
        chunk.put("taskId", taskId);
        chunk.put("itemNumber", itemNum);
        chunk.put("status", "processing");
        agentNode.streamChunk(chunk);

        System.out.printf("Task %s: Processed item %d/%d%n", taskId, itemNum + 1, itemsToProcess);
      }

      // Return final result
      Map<String, Object> result = new HashMap<>();
      result.put("taskId", taskId);
      result.put("status", "completed");
      result.put("totalItems", itemsToProcess);
      result.put("completedAt", System.currentTimeMillis());
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Stream-All Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      StreamAllAgentModule module = new StreamAllAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamAllAgent");

      System.out.println("Stream-All Agent Example:");
      System.out.println("Demonstrating streaming across multiple agent invocations...");

      // Track chunks received by invoke ID
      Map<Object, List<Map<String, Object>>> chunksByInvoke = new ConcurrentHashMap<>();

      // Start two invocations
      Map<String, Object> request1 = new HashMap<>();
      request1.put("taskId", "task-1");
      request1.put("itemsToProcess", 3);
      AgentInvoke invoke1 = agent.initiate(request1);

      Map<String, Object> request2 = new HashMap<>();
      request2.put("taskId", "task-2");
      request2.put("itemsToProcess", 4);
      AgentInvoke invoke2 = agent.initiate(request2);

      System.out.println("\nStarted 2 agent invocations");
      System.out.println("Subscribing to streaming from all invocations...");

      // Subscribe to streaming chunks from ALL invocations
      agent.streamAll(
          invoke1,
          "process-task",
          (allChunks, newChunks, resetInvokeIds, complete) -> {
            for (Map.Entry<UUID, List<Object>> entry : newChunks.entrySet()) {
              UUID invokeId = entry.getKey();
              List<Object> chunks = entry.getValue();

              for (Object chunkObj : chunks) {
                @SuppressWarnings("unchecked")
                Map<String, Object> chunk = (Map<String, Object>) chunkObj;

                chunksByInvoke.computeIfAbsent(invokeId, k -> new ArrayList<>()).add(chunk);

                System.out.printf(
                    "Received streaming chunk: Task=%s Item=%d [invoke-id=%s]%n",
                    chunk.get("taskId"), chunk.get("itemNumber"), invokeId);
              }
            }
          });

      // Wait for all invocations to complete
      System.out.println("\nWaiting for all invocations to complete...");
      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.result(invoke1);
      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.result(invoke2);

      System.out.println("\nFinal results:");
      System.out.printf(
          "  %s: %d items processed%n", result1.get("taskId"), result1.get("totalItems"));
      System.out.printf(
          "  %s: %d items processed%n", result2.get("taskId"), result2.get("totalItems"));

      System.out.println("\nStreaming summary:");
      System.out.printf("  Total invocations tracked: %d%n", chunksByInvoke.size());
      for (Map.Entry<Object, List<Map<String, Object>>> entry : chunksByInvoke.entrySet()) {
        System.out.printf(
            "  Invoke %s: received %d streaming chunks%n", entry.getKey(), entry.getValue().size());
      }

      System.out.println("\nNotice how:");
      System.out.println("- streamAll subscribes to ALL invocations of an agent");
      System.out.println("- Both invocations were started before subscribing");
      System.out.println("- Chunks are grouped and delivered by invoke ID");
      System.out.println("- A single callback handles all invocations");
      System.out.println("- Each invocation is tracked independently");
    }
  }
}
