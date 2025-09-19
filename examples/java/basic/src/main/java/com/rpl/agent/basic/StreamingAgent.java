package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java example demonstrating streaming chunk emission from agent nodes.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>streamChunk: Emit streaming data from nodes
 *   <li>agent.stream: Subscribe to streaming data from specific nodes
 *   <li>Real-time data flow with incremental results
 *   <li>Streaming completion and callbacks
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class StreamingAgent {

  /** Agent Module demonstrating streaming functionality. */
  public static class StreamingAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("StreamingAgent").node("process-data", null, new ProcessDataFunction());
    }
  }

  /** Node function that processes data and streams progress updates. */
  public static class ProcessDataFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      int dataSize = (Integer) request.get("dataSize");
      int chunkSize = (Integer) request.get("chunkSize");
      int totalChunks = (int) Math.ceil((double) dataSize / chunkSize);

      System.out.printf("Processing %d items in chunks of %d%n", dataSize, chunkSize);

      // Stream progress as we process chunks
      for (int chunkNum = 0; chunkNum < totalChunks; chunkNum++) {
        int startIdx = chunkNum * chunkSize;
        int endIdx = Math.min(startIdx + chunkSize, dataSize);
        List<Integer> items = new ArrayList<>();
        for (int i = startIdx; i < endIdx; i++) {
          items.add(i);
        }
        double progress = (double) (chunkNum + 1) / totalChunks;

        // Simulate processing time
        try {
          Thread.sleep(100);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }

        // Stream chunk progress
        Map<String, Object> chunkData = new HashMap<>();
        chunkData.put("chunkNumber", chunkNum);
        chunkData.put("itemsProcessed", items.size());
        chunkData.put("progress", progress);
        chunkData.put("items", items);
        agentNode.streamChunk(chunkData);

        System.out.printf(
            "Processed chunk %d/%d (%.1f%%)%n", chunkNum + 1, totalChunks, progress * 100.0);
      }

      // Return final result
      Map<String, Object> result = new HashMap<>();
      result.put("action", "data-processing");
      result.put("totalItems", dataSize);
      result.put("totalChunks", totalChunks);
      result.put("chunkSize", chunkSize);
      result.put("completedAt", System.currentTimeMillis());
      agentNode.result(result);
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("Starting Streaming Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      StreamingAgentModule module = new StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      System.out.println("Streaming Agent Example:");
      System.out.println("Processing data with real-time streaming updates...");

      // Start async processing
      Map<String, Object> request = new HashMap<>();
      request.put("dataSize", 50);
      request.put("chunkSize", 10);
      AgentInvoke invoke = agent.initiate(request);
      AtomicInteger chunksReceived = new AtomicInteger(0);

      // Subscribe to streaming chunks
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              @SuppressWarnings("unchecked")
              Map<String, Object> chunk = (Map<String, Object>) chunkObj;
              chunksReceived.incrementAndGet();
              System.out.printf(
                  "Received chunk %d: %d items (%.1f%% complete)%n",
                  (Integer) chunk.get("chunkNumber"),
                  (Integer) chunk.get("itemsProcessed"),
                  (Double) chunk.get("progress") * 100.0);
            }
          });

      // Wait for completion
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.result(invoke);

      System.out.println("\nFinal result:");
      System.out.println("  Total items processed: " + result.get("totalItems"));
      System.out.println("  Total chunks: " + result.get("totalChunks"));
      System.out.println("  Chunk size: " + result.get("chunkSize"));
      System.out.println("  Chunks received via streaming: " + chunksReceived.get());

      System.out.println("\nNotice how:");
      System.out.println("- Streaming provides real-time progress updates");
      System.out.println("- Chunks are received while processing continues");
      System.out.println("- Final result provides summary information");
    }
  }
}
