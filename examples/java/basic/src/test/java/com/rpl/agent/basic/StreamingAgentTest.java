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
import java.util.List;
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
      StreamingAgent.ProcessingRequest request =
          new StreamingAgent.ProcessingRequest(10, 5); // 10 total items, chunk size 5
      AgentInvoke invoke = agent.initiate(request);

      // Track chunks received via streaming
      AtomicInteger chunksReceived = new AtomicInteger(0);
      List<StreamingAgent.ChunkData> receivedChunks = new ArrayList<>();

      // Subscribe to streaming chunks
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              StreamingAgent.ChunkData chunk = (StreamingAgent.ChunkData) chunkObj;
              receivedChunks.add(chunk);
              chunksReceived.incrementAndGet();
            }
          });

      // Get final result
      StreamingAgent.ProcessingResult result =
          (StreamingAgent.ProcessingResult) agent.result(invoke);

      // Verify streaming chunks were emitted
      assertTrue("Should have received streaming chunks", chunksReceived.get() > 0);
      assertTrue("Should have received chunks", receivedChunks.size() > 0);

      // Verify final result
      assertNotNull("Final result should not be null", result);
      assertEquals("Should process 10 items", 10, result.getTotalItems());
      assertEquals("Should have chunk size 5", 5, result.getChunkSize());
      assertTrue("Should have total chunks", result.getTotalChunks() > 0);
    }
  }

  @Test
  public void testStreamingAgentWithDifferentSizes() throws Exception {
    // Tests streaming with different chunk sizes
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamingAgent.StreamingAgentModule module = new StreamingAgent.StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      // Test with different chunk sizes
      StreamingAgent.ProcessingRequest request1 =
          new StreamingAgent.ProcessingRequest(15, 3); // 15 items, chunk size 3
      AgentInvoke invoke1 = agent.initiate(request1);

      AtomicInteger chunksReceived1 = new AtomicInteger(0);
      agent.stream(
          invoke1,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            chunksReceived1.addAndGet(newChunks.size());
          });

      StreamingAgent.ProcessingResult result1 =
          (StreamingAgent.ProcessingResult) agent.result(invoke1);

      // Test with larger chunk size
      StreamingAgent.ProcessingRequest request2 =
          new StreamingAgent.ProcessingRequest(15, 7); // 15 items, chunk size 7
      AgentInvoke invoke2 = agent.initiate(request2);

      AtomicInteger chunksReceived2 = new AtomicInteger(0);
      agent.stream(
          invoke2,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            chunksReceived2.addAndGet(newChunks.size());
          });

      StreamingAgent.ProcessingResult result2 =
          (StreamingAgent.ProcessingResult) agent.result(invoke2);

      // Verify different chunk configurations
      assertEquals("Result 1 should process 15 items", 15, result1.getTotalItems());
      assertEquals("Result 1 should have chunk size 3", 3, result1.getChunkSize());
      assertEquals("Result 2 should process 15 items", 15, result2.getTotalItems());
      assertEquals("Result 2 should have chunk size 7", 7, result2.getChunkSize());

      // Smaller chunks should generate more chunks
      assertTrue(
          "Smaller chunks should create more chunk events",
          result1.getTotalChunks() > result2.getTotalChunks());
    }
  }

  @Test
  public void testStreamingAgentZeroItems() throws Exception {
    // Tests streaming with zero items
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamingAgent.StreamingAgentModule module = new StreamingAgent.StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      // Test with zero items
      StreamingAgent.ProcessingRequest request =
          new StreamingAgent.ProcessingRequest(0, 5); // 0 items
      AgentInvoke invoke = agent.initiate(request);

      AtomicInteger chunksReceived = new AtomicInteger(0);
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            chunksReceived.addAndGet(newChunks.size());
          });

      StreamingAgent.ProcessingResult result =
          (StreamingAgent.ProcessingResult) agent.result(invoke);

      // Should complete with zero items
      assertEquals("Should process 0 items", 0, result.getTotalItems());
      assertEquals("Should have 0 chunks", 0, result.getTotalChunks());
    }
  }

  @Test
  public void testStreamingAgentChunkProgression() throws Exception {
    // Tests that streaming chunks show progression
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      StreamingAgent.StreamingAgentModule module = new StreamingAgent.StreamingAgentModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StreamingAgent");

      // Start with reasonable size for testing
      StreamingAgent.ProcessingRequest request =
          new StreamingAgent.ProcessingRequest(20, 4); // 20 items, chunk size 4
      AgentInvoke invoke = agent.initiate(request);

      List<StreamingAgent.ChunkData> chunks = new ArrayList<>();
      agent.stream(
          invoke,
          "process-data",
          (allChunks, newChunks, reset, complete) -> {
            for (Object chunkObj : newChunks) {
              StreamingAgent.ChunkData chunk = (StreamingAgent.ChunkData) chunkObj;
              chunks.add(chunk);
            }
          });

      StreamingAgent.ProcessingResult result =
          (StreamingAgent.ProcessingResult) agent.result(invoke);

      // Verify progression in chunks
      if (!chunks.isEmpty()) {
        StreamingAgent.ChunkData firstChunk = chunks.get(0);
        StreamingAgent.ChunkData lastChunk = chunks.get(chunks.size() - 1);

        assertTrue("Progress should increase", lastChunk.getProgress() >= firstChunk.getProgress());
        assertTrue(
            "Chunk numbers should be sequential",
            lastChunk.getChunkNumber() >= firstChunk.getChunkNumber());
      }

      assertEquals("Should process 20 items", 20, result.getTotalItems());
      assertEquals("Should have chunk size 4", 4, result.getChunkSize());
    }
  }
}
