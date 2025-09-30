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
import org.junit.Test;

/**
 * Test class for StreamSpecificAgent demonstrating streaming from a specific invocation.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>streamChunk: Emitting streaming data from agent nodes
 *   <li>agent.streamSpecific: Subscribing to streaming data from a specific invocation
 *   <li>agent.nextStep: Triggering execution after subscription
 *   <li>Selective monitoring of one invocation among multiple
 * </ul>
 */
public class StreamSpecificAgentTest {

  @Test
  public void testStreamSpecificAgentSingleInvocation() throws Exception {
    // Tests streaming from a specific agent invocation using streamSpecific
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamSpecificAgent.StreamSpecificAgentModule module =
          new StreamSpecificAgent.StreamSpecificAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamSpecificAgent");

      // Track chunks received
      List<Map<String, Object>> chunksReceived = new ArrayList<>();

      // Start three invocations
      Map<String, Object> request1 = new HashMap<>();
      request1.put("taskId", "test-1");
      request1.put("itemsToProcess", 3);
      AgentInvoke invoke1 = agent.initiate(request1);

      Map<String, Object> request2 = new HashMap<>();
      request2.put("taskId", "test-2");
      request2.put("itemsToProcess", 4);
      AgentInvoke invoke2 = agent.initiate(request2);

      Map<String, Object> request3 = new HashMap<>();
      request3.put("taskId", "test-3");
      request3.put("itemsToProcess", 2);
      AgentInvoke invoke3 = agent.initiate(request3);

      // Get the invoke-id for the specific invocation to monitor
      UUID targetInvokeId = invoke2.getAgentInvokeId();

      // Subscribe to streaming from only invoke2
      agent.streamSpecific(
          invoke2,
          "process-task",
          targetInvokeId,
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              @SuppressWarnings("unchecked")
              Map<String, Object> chunk = (Map<String, Object>) chunkObj;
              chunksReceived.add(chunk);
            }
          });

      // Trigger execution with nextStep
      agent.nextStep(invoke1);
      agent.nextStep(invoke2);
      agent.nextStep(invoke3);

      // Get final results
      @SuppressWarnings("unchecked")
      Map<String, Object> result1 = (Map<String, Object>) agent.result(invoke1);
      @SuppressWarnings("unchecked")
      Map<String, Object> result2 = (Map<String, Object>) agent.result(invoke2);
      @SuppressWarnings("unchecked")
      Map<String, Object> result3 = (Map<String, Object>) agent.result(invoke3);

      // Verify all results
      assertEquals("First task ID should be test-1", "test-1", result1.get("taskId"));
      assertEquals("First task should process 3 items", 3, (int) result1.get("totalItems"));
      assertEquals("First task should be completed", "completed", result1.get("status"));

      assertEquals("Second task ID should be test-2", "test-2", result2.get("taskId"));
      assertEquals("Second task should process 4 items", 4, (int) result2.get("totalItems"));
      assertEquals("Second task should be completed", "completed", result2.get("status"));

      assertEquals("Third task ID should be test-3", "test-3", result3.get("taskId"));
      assertEquals("Third task should process 2 items", 2, (int) result3.get("totalItems"));
      assertEquals("Third task should be completed", "completed", result3.get("status"));

      // Verify streaming chunks were received ONLY from invoke2
      assertEquals("Should receive 4 chunks from test-2 only", 4, chunksReceived.size());

      // Verify all chunks are from test-2
      for (Map<String, Object> chunk : chunksReceived) {
        assertEquals("All chunks should be from test-2", "test-2", chunk.get("taskId"));
        assertNotNull("Chunk should have itemNumber", chunk.get("itemNumber"));
        assertEquals("Chunk status should be processing", "processing", chunk.get("status"));
      }

      // Verify we got the expected item numbers
      List<Integer> itemNumbers = new ArrayList<>();
      for (Map<String, Object> chunk : chunksReceived) {
        itemNumbers.add((Integer) chunk.get("itemNumber"));
      }
      assertEquals("Should have item numbers 0-3", List.of(0, 1, 2, 3), itemNumbers);
    }
  }
}
