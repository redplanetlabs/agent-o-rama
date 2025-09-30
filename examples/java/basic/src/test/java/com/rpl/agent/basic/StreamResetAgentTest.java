package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentStream;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Test class for StreamResetAgent demonstrating stream reset behavior on retry.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>streamChunk: Emitting streaming data from agent nodes
 *   <li>Exception handling: Node throwing exception on first execution
 *   <li>Automatic retry: Agent-o-rama automatically retries failed nodes
 *   <li>Stream reset: Streaming subscriptions notified when a node retries
 *   <li>AgentStream.numResets: Query the number of resets that occurred
 * </ul>
 */
public class StreamResetAgentTest {

  @Test
  public void testStreamResetOnRetry() throws Exception {
    // Test demonstrates stream reset behavior when a node is retried after failure
    // Contracts tested:
    // - Node throws exception on first execution
    // - Agent automatically retries failed node
    // - Streaming subscription receives reset notification
    // - AgentStream.numResets returns correct reset count

    // Reset the execution flag for this test
    StreamResetAgent.firstExecution.set(true);

    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamResetAgent.StreamResetAgentModule module =
          new StreamResetAgent.StreamResetAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamResetAgent");

      // Start agent execution
      Map<String, Object> request = new HashMap<>();
      request.put("dataSize", 5);
      AgentInvoke invoke = agent.initiate(request);

      // Track streaming updates
      AtomicInteger chunksReceived = new AtomicInteger(0);
      AtomicBoolean resetSeen = new AtomicBoolean(false);
      AtomicBoolean completeSeen = new AtomicBoolean(false);
      List<Map<String, Object>> receivedChunks = new ArrayList<>();

      // Subscribe to streaming chunks
      AgentStream stream =
          agent.stream(
              invoke,
              "process-with-retry",
              (allChunks, newChunks, reset, complete) -> {
                if (reset) {
                  resetSeen.set(true);
                }
                if (complete) {
                  completeSeen.set(true);
                }
                if (newChunks != null) {
                  for (Object chunkObj : newChunks) {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> chunk = (Map<String, Object>) chunkObj;
                    receivedChunks.add(chunk);
                    chunksReceived.incrementAndGet();
                  }
                }
              });

      // Wait for final result
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.result(invoke);
      int resetCount = stream.numResets();

      // Verify final result
      assertNotNull("Final result should not be null", result);
      assertEquals("Status should be completed", "completed", result.get("status"));
      assertEquals("Should process 5 chunks", 5, (int) result.get("totalChunks"));
      assertEquals(
          "Message should indicate retry",
          "Processing completed after retry",
          result.get("message"));

      // Verify streaming behavior
      // We receive chunks from both the failed attempt (2 chunks) and the retry (5 chunks)
      assertEquals(
          "Should receive 7 chunks total (2 from first attempt + 5 from retry)",
          7,
          chunksReceived.get());
      assertEquals("Should have 7 chunks in list", 7, receivedChunks.size());

      // Verify chunk data includes both attempts
      List<String> chunkData = new ArrayList<>();
      for (Map<String, Object> chunk : receivedChunks) {
        chunkData.add((String) chunk.get("data"));
      }
      List<String> expected =
          List.of("chunk-1", "chunk-2", "chunk-1", "chunk-2", "chunk-3", "chunk-4", "chunk-5");
      assertEquals("Chunk data should match expected", expected, chunkData);

      // Verify reset was detected
      assertTrue("Stream callback should have received reset=true", resetSeen.get());

      // Verify completion was detected
      assertTrue("Stream callback should have received complete=true", completeSeen.get());

      // Verify reset count
      assertEquals("AgentStream.numResets should return 1", 1, resetCount);
    }
  }
}
