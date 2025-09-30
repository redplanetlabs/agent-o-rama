package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Java example demonstrating subscribing to streaming chunks from a specific agent invocation.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>agent.initiate: Start agent execution without waiting
 *   <li>agent.streamSpecific: Subscribe to streaming from a specific invocation by invoke-id
 *   <li>agent.nextStep: Trigger actual execution after subscribing
 *   <li>Targeting streaming subscription to one invocation among multiple
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class StreamSpecificAgent {

  /** Agent Module demonstrating stream-specific functionality. */
  public static class StreamSpecificAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("StreamSpecificAgent")
          .node("process-task", null, new ProcessTaskFunction());
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
    System.out.println("Starting Stream-Specific Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      StreamSpecificAgentModule module = new StreamSpecificAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamSpecificAgent");

      System.out.println("Stream-Specific Agent Example:");
      System.out.println("Demonstrating streaming from a specific invocation...\n");

      // Track chunks received for the specific invocation we're monitoring
      List<Map<String, Object>> chunksReceived = new ArrayList<>();

      // Start three invocations
      Map<String, Object> request1 = new HashMap<>();
      request1.put("taskId", "task-1");
      request1.put("itemsToProcess", 3);
      AgentInvoke invoke1 = agent.initiate(request1);

      Map<String, Object> request2 = new HashMap<>();
      request2.put("taskId", "task-2");
      request2.put("itemsToProcess", 4);
      AgentInvoke invoke2 = agent.initiate(request2);

      Map<String, Object> request3 = new HashMap<>();
      request3.put("taskId", "task-3");
      request3.put("itemsToProcess", 2);
      AgentInvoke invoke3 = agent.initiate(request3);

      // Get the invoke-id for the specific invocation we want to monitor
      UUID targetInvokeId = invoke2.getAgentInvokeId();

      System.out.println("Started 3 agent invocations");
      System.out.printf(
          "Subscribing to streaming from invoke-id: %s (task-2 only)...%n", targetInvokeId);

      // Subscribe to streaming chunks from ONLY invoke2 using its specific invoke-id
      agent.streamSpecific(
          invoke2,
          "process-task",
          targetInvokeId,
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              @SuppressWarnings("unchecked")
              Map<String, Object> chunk = (Map<String, Object>) chunkObj;

              chunksReceived.add(chunk);

              System.out.printf(
                  "Received streaming chunk: Task=%s Item=%d [invoke-id=%s]%n",
                  chunk.get("taskId"), chunk.get("itemNumber"), targetInvokeId);
            }
          });

      // Now trigger execution using agent-next-step
      System.out.println("\nTriggering execution with nextStep...");
      agent.nextStep(invoke1);
      agent.nextStep(invoke2);
      agent.nextStep(invoke3);

      // Wait for all invocations to complete
      System.out.println("\nWaiting for all invocations to complete...");
      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.result(invoke1);
      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.result(invoke2);
      @SuppressWarnings("unchecked")
      Map<String, Object> result3 = (Map<String, Object>) agent.result(invoke3);

      System.out.println("\nFinal results:");
      System.out.printf(
          "  %s: %d items processed%n", result1.get("taskId"), result1.get("totalItems"));
      System.out.printf(
          "  %s: %d items processed%n", result2.get("taskId"), result2.get("totalItems"));
      System.out.printf(
          "  %s: %d items processed%n", result3.get("taskId"), result3.get("totalItems"));

      System.out.println("\nStreaming summary:");
      System.out.printf("  Chunks received from task-2 only: %d%n", chunksReceived.size());
      System.out.printf("  Expected chunks: %d%n", 4);

      System.out.println("\nNotice how:");
      System.out.println("- streamSpecific subscribes to ONE specific invocation");
      System.out.println("- We subscribed only to task-2 using its invoke-id");
      System.out.println("- initiate prepares the invocation without executing");
      System.out.println("- nextStep triggers actual execution");
      System.out.println("- Only chunks from task-2 were received, not task-1 or task-3");
    }
  }
}
