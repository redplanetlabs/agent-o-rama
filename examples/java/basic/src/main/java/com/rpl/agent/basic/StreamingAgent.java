package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.RamaSerializable;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.List;
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

  /** Request object for data processing parameters. */
  public static record ProcessingRequest(int dataSize, int chunkSize) implements RamaSerializable {}

  /** Streaming chunk data emitted during processing. */
  public static record ChunkData(
      int chunkNumber, int itemsProcessed, double progress, List<Integer> items)
      implements RamaSerializable {}

  /** Final result object returned after processing completion. */
  public static record ProcessingResult(
      String action, int totalItems, int totalChunks, int chunkSize, long completedAt)
      implements RamaSerializable {}

  /** Agent Module demonstrating streaming functionality. */
  public static class StreamingAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentsTopology topology) {
      topology.newAgent("StreamingAgent").node("process-data", null, new ProcessDataFunction());
    }
  }

  /** Node function that processes data and streams progress updates. */
  public static class ProcessDataFunction
      implements RamaVoidFunction2<AgentNode, ProcessingRequest> {

    @Override
    public void invoke(AgentNode agentNode, ProcessingRequest request) {
      int dataSize = request.dataSize();
      int chunkSize = request.chunkSize();
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
        ChunkData chunkData = new ChunkData(chunkNum, items.size(), progress, items);
        agentNode.streamChunk(chunkData);

        System.out.printf(
            "Processed chunk %d/%d (%.1f%%)%n", chunkNum + 1, totalChunks, progress * 100.0);
      }

      // Return final result
      ProcessingResult result =
          new ProcessingResult(
              "data-processing", dataSize, totalChunks, chunkSize, System.currentTimeMillis());
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
      AgentInvoke invoke = agent.initiate(new ProcessingRequest(50, 10));
      AtomicInteger chunksReceived = new AtomicInteger(0);

      // Subscribe to streaming chunks
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              ChunkData chunk = (ChunkData) chunkObj;
              chunksReceived.incrementAndGet();
              System.out.printf(
                  "Received chunk %d: %d items (%.1f%% complete)%n",
                  chunk.chunkNumber(), chunk.itemsProcessed(), chunk.progress() * 100.0);
            }
          });

      // Wait for completion
      ProcessingResult result = (ProcessingResult) agent.result(invoke);

      System.out.println("\nFinal result:");
      System.out.println("  Total items processed: " + result.totalItems());
      System.out.println("  Total chunks: " + result.totalChunks());
      System.out.println("  Chunk size: " + result.chunkSize());
      System.out.println("  Chunks received via streaming: " + chunksReceived.get());

      System.out.println("\nNotice how:");
      System.out.println("- Streaming provides real-time progress updates");
      System.out.println("- Chunks are received while processing continues");
      System.out.println("- Final result provides summary information");
    }
  }
}
