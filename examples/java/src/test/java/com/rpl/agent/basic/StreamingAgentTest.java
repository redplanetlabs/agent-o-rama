package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.Test;

/**
 * Test class for StreamingAgent demonstrating streaming data patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>streamChunk: Emitting streaming data from agent nodes
 *   <li>agent.stream: Subscribing to streaming data from specific nodes
 *   <li>Real-time data flow with incremental results
 *   <li>Streaming completion and callbacks
 * </ul>
 */
public class StreamingAgentTest {

  @Test
  public void testStreamingAgentBasicFunctionality() throws Exception {
    // Tests basic streaming functionality
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamingAgent.StreamingAgentModule module = new StreamingAgent.StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      // Start agent execution with small numbers for testing
      Map<String, Object> request = new HashMap<>();
      request.put("dataSize", 10); // 10 total items
      request.put("chunkSize", 5); // chunk size 5
      AgentInvoke invoke = agent.initiate(request);

      // Track chunks received via streaming
      AtomicInteger chunksReceived = new AtomicInteger(0);
      List<Map<String, Object>> receivedChunks = new ArrayList<>();

      // Subscribe to streaming chunks
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              @SuppressWarnings("unchecked")
              Map<String, Object> chunk = (Map<String, Object>) chunkObj;
              receivedChunks.add(chunk);
              chunksReceived.incrementAndGet();
            }
          });

      // Get final result
      @SuppressWarnings("unchecked")
      Map<String, Object> result = (Map<String, Object>) agent.result(invoke);

      // Verify streaming chunks were emitted
      assertTrue("Should have received streaming chunks", chunksReceived.get() > 0);
      assertTrue("Should have received chunks", receivedChunks.size() > 0);

      // Verify final result
      assertNotNull("Final result should not be null", result);
      assertEquals("Should process 10 items", 10, (int) result.get("totalItems"));
      assertEquals("Should have chunk size 5", 5, (int) result.get("chunkSize"));
      assertTrue("Should have total chunks", (Integer) result.get("totalChunks") > 0);
    }
  }
}
