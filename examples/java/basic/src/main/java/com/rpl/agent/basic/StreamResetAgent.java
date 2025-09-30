package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentStream;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Java example demonstrating stream reset behavior when a node is retried after failure.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>streamChunk: Emit streaming data from nodes
 *   <li>Exception handling: Node throws exception on first execution
 *   <li>Automatic retry: Agent-o-rama automatically retries failed nodes
 *   <li>Stream reset: Streaming subscriptions are notified when a node retries
 *   <li>AgentStream.numResets: Query the number of resets that occurred
 * </ul>
 *
 * <p>All required classes are defined as nested classes within this single file for simplicity and
 * self-containment.
 */
public class StreamResetAgent {

  /** Flag to track whether this is the first execution attempt. */
  static final AtomicBoolean firstExecution = new AtomicBoolean(true);

  /** Agent Module demonstrating stream reset on retry. */
  public static class StreamResetAgentModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology
          .newAgent("StreamResetAgent")
          .node("process-with-retry", null, new ProcessWithRetryFunction());
    }
  }

  /** Node function that fails on first execution, succeeds on retry. */
  public static class ProcessWithRetryFunction
      implements RamaVoidFunction2<AgentNode, Map<String, Object>> {

    @Override
    public void invoke(AgentNode agentNode, Map<String, Object> request) {
      int dataSize = (Integer) request.get("dataSize");
      System.out.println("Starting data processing...");

      if (firstExecution.get()) {
        // First execution: emit some chunks, then fail
        System.out.println("First execution: emitting partial data before failure");

        Map<String, Object> chunk1 = new HashMap<>();
        chunk1.put("chunkNumber", 0);
        chunk1.put("data", "chunk-1");
        agentNode.streamChunk(chunk1);

        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }

        Map<String, Object> chunk2 = new HashMap<>();
        chunk2.put("chunkNumber", 1);
        chunk2.put("data", "chunk-2");
        agentNode.streamChunk(chunk2);

        try {
          Thread.sleep(50);
        } catch (InterruptedException e) {
          Thread.currentThread().interrupt();
          return;
        }

        firstExecution.set(false);
        System.out.println("Simulating failure...");
        throw new RuntimeException("Simulated processing failure");

      } else {
        // Retry execution: emit all chunks successfully
        System.out.println("Retry execution: processing all data successfully");

        for (int i = 0; i < dataSize; i++) {
          Map<String, Object> chunk = new HashMap<>();
          chunk.put("chunkNumber", i);
          chunk.put("data", "chunk-" + (i + 1));
          agentNode.streamChunk(chunk);

          try {
            Thread.sleep(50);
          } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return;
          }
        }

        System.out.println("Processing completed successfully after retry");

        Map<String, Object> result = new HashMap<>();
        result.put("status", "completed");
        result.put("totalChunks", dataSize);
        result.put("message", "Processing completed after retry");
        agentNode.result(result);
      }
    }
  }

  public static void main(String[] args) throws Exception {
    // Reset the execution flag for this run
    firstExecution.set(true);

    System.out.println("Starting Stream Reset Agent Example...");

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch the agent module
      StreamResetAgentModule module = new StreamResetAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamResetAgent");

      System.out.println("Stream Reset Agent Example:");
      System.out.println(
          "This example demonstrates what happens when a streaming node fails and retries.\n");

      // Track streaming updates
      Map<String, Object> request = new HashMap<>();
      request.put("dataSize", 5);
      AgentInvoke invoke = agent.initiate(request);
      AtomicInteger chunksReceived = new AtomicInteger(0);
      AtomicBoolean resetSeen = new AtomicBoolean(false);

      // Subscribe to streaming chunks
      AgentStream stream =
          agent.stream(
              invoke,
              "process-with-retry",
              (allChunks, newChunks, reset, complete) -> {
                if (reset) {
                  resetSeen.set(true);
                  System.out.println("\n*** STREAM RESET DETECTED ***");
                  System.out.println("The node was retried and the stream was reset.");
                }

                for (Object chunkObj : newChunks) {
                  @SuppressWarnings("unchecked")
                  Map<String, Object> chunk = (Map<String, Object>) chunkObj;
                  chunksReceived.incrementAndGet();
                  System.out.printf(
                      "Received: %s (reset=%s, complete=%s)%n", chunk.get("data"), reset, complete);
                }

                if (complete) {
                  System.out.println("\nStreaming completed.");
                }
              });

      // Wait for completion
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.result(invoke);
      int resetCount = stream.numResets();

      System.out.println("\n=== Final Results ===");
      System.out.println("Status: " + result.get("status"));
      System.out.println("Message: " + result.get("message"));
      System.out.println("Total chunks received: " + chunksReceived.get());
      System.out.println("Stream was reset: " + resetSeen.get());
      System.out.println("Reset count: " + resetCount);

      System.out.println("\nNotice how:");
      System.out.println("- The first execution emitted 2 chunks before failing");
      System.out.println("- The stream was automatically reset when the node retried");
      System.out.println("- The retry execution emitted all 5 chunks successfully");
      System.out.println("- The callback received reset=true to indicate the reset");
      System.out.println("- AgentStream.numResets shows the reset count is " + resetCount);
    }
  }
}
