package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Test class for StreamAllAgent demonstrating streaming across multiple invocations.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>streamChunk: Emitting streaming data from agent nodes
 *   <li>agent.streamAll: Subscribing to streaming data from all invocations
 *   <li>Multiple agent invocations with single subscription
 *   <li>Tracking chunks by invoke ID
 * </ul>
 */
public class StreamAllAgentTest {

  @Test
  public void testStreamAllAgentMultipleInvocations() throws Exception {
    // Tests streaming across multiple agent invocations using streamAll
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamAllAgent.StreamAllAgentModule module = new StreamAllAgent.StreamAllAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamAllAgent");

      // Track chunks received by invoke ID
      Map<Object, List<Map<String, Object>>> chunksByInvoke = new ConcurrentHashMap<>();
      AtomicInteger totalChunksReceived = new AtomicInteger(0);

      // Start two invocations
      Map<String, Object> request1 = new HashMap<>();
      request1.put("taskId", "test-1");
      request1.put("itemsToProcess", 3);
      AgentInvoke invoke1 = agent.initiate(request1);

      Map<String, Object> request2 = new HashMap<>();
      request2.put("taskId", "test-2");
      request2.put("itemsToProcess", 2);
      AgentInvoke invoke2 = agent.initiate(request2);

      // Subscribe to streaming chunks from all invocations
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
                totalChunksReceived.incrementAndGet();
              }
            }
          });

      // Wait for all invocations to complete
      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.result(invoke1);
      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.result(invoke2);

      // Verify all results
      assertEquals("First task ID should be test-1", "test-1", result1.get("taskId"));
      assertEquals("First task should process 3 items", 3, (int) result1.get("totalItems"));
      assertEquals("First task should be completed", "completed", result1.get("status"));

      assertEquals("Second task ID should be test-2", "test-2", result2.get("taskId"));
      assertEquals("Second task should process 2 items", 2, (int) result2.get("totalItems"));
      assertEquals("Second task should be completed", "completed", result2.get("status"));

      // Verify streaming chunks were received
      assertEquals("Should track 2 invocations", 2, chunksByInvoke.size());
      assertEquals("Should receive total of 5 chunks (3+2)", 5, totalChunksReceived.get());

      // Verify chunk counts for each invocation
      int threeChunkCount = 0;
      int twoChunkCount = 0;

      for (List<Map<String, Object>> chunks : chunksByInvoke.values()) {
        int size = chunks.size();
        if (size == 3) threeChunkCount++;
        if (size == 2) twoChunkCount++;

        // Verify chunk structure
        for (Map<String, Object> chunk : chunks) {
          assertNotNull("Chunk should have taskId", chunk.get("taskId"));
          assertNotNull("Chunk should have itemNumber", chunk.get("itemNumber"));
          assertEquals("Chunk status should be processing", "processing", chunk.get("status"));
        }
      }

      assertEquals("Should have one invocation with 3 chunks", 1, threeChunkCount);
      assertEquals("Should have one invocation with 2 chunks", 1, twoChunkCount);
    }
  }
}
