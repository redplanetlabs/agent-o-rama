package com.rpl.agent.basic;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentInvoke;
import com.rpl.agentorama.AgentManager;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import org.junit.Test;

/**
 * Test class for ForkingAgent demonstrating agent execution forking and branching patterns.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>agent.initiateFork: Creating execution branches from existing invocations
 *   <li>agent.fork: Synchronous forking with modified parameters
 *   <li>Branching execution paths with different inputs
 *   <li>Fork management and result handling
 * </ul>
 */
public class ForkingAgentTest {

  @Test
  public void testBasicForkingAgentFunctionality() throws Exception {
    // Tests basic forking agent functionality
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Test basic execution
      ForkingAgent.ForkingInput input = new ForkingAgent.ForkingInput(4, 3);
      ForkingAgent.ForkingResult result = (ForkingAgent.ForkingResult) agent.invoke(input);

      assertNotNull("Result should not be null", result);
      assertEquals(
          "Action should be calculation-complete", "calculation-complete", result.getAction());
      assertEquals("Processed value should be 12", 12, result.getProcessedValue());
      assertEquals("Squared should be 144", 144, result.getSquared());
      assertEquals("Halved should be 6.0", 6.0, result.getHalved(), 0.01);
      assertTrue("Result should be valid", result.isValid());
    }
  }

  @Test
  public void testSynchronousFork() throws Exception {
    // Tests synchronous forking functionality
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Start base execution
      ForkingAgent.ForkingInput input = new ForkingAgent.ForkingInput(5, 2);
      AgentInvoke baseInvoke = agent.initiate(input);
      ForkingAgent.ForkingResult baseResult = (ForkingAgent.ForkingResult) agent.result(baseInvoke);

      // Verify base result
      assertEquals("Base processed value should be 10", 10, baseResult.getProcessedValue());
      assertEquals("Base squared should be 100", 100, baseResult.getSquared());

      // Synchronous fork with same parameters
      ForkingAgent.ForkingResult forkResult =
          (ForkingAgent.ForkingResult) agent.fork(baseInvoke, new HashMap<>());

      // Fork should produce the same result
      assertNotNull("Fork result should not be null", forkResult);
      assertEquals(
          "Fork processed value should match base",
          baseResult.getProcessedValue(),
          forkResult.getProcessedValue());
      assertEquals(
          "Fork squared should match base", baseResult.getSquared(), forkResult.getSquared());
      assertEquals(
          "Fork halved should match base", baseResult.getHalved(), forkResult.getHalved(), 0.01);
      assertEquals("Fork validity should match base", baseResult.isValid(), forkResult.isValid());
    }
  }

  @Test
  public void testAsynchronousFork() throws Exception {
    // Tests asynchronous forking functionality
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Start base execution
      ForkingAgent.ForkingInput input = new ForkingAgent.ForkingInput(3, 4);
      AgentInvoke baseInvoke = agent.initiate(input);
      ForkingAgent.ForkingResult baseResult = (ForkingAgent.ForkingResult) agent.result(baseInvoke);

      // Asynchronous fork
      AgentInvoke forkInvoke = agent.initiateFork(baseInvoke, new HashMap<>());
      assertNotNull("Fork invoke should not be null", forkInvoke);

      ForkingAgent.ForkingResult forkResult = (ForkingAgent.ForkingResult) agent.result(forkInvoke);

      // Fork should produce the same result
      assertNotNull("Fork result should not be null", forkResult);
      assertEquals(
          "Fork processed value should match base",
          baseResult.getProcessedValue(),
          forkResult.getProcessedValue());
      assertEquals(
          "Fork squared should match base", baseResult.getSquared(), forkResult.getSquared());
      assertEquals(
          "Fork halved should match base", baseResult.getHalved(), forkResult.getHalved(), 0.01);
      assertEquals("Fork validity should match base", baseResult.isValid(), forkResult.isValid());
    }
  }

  @Test
  public void testMultipleForks() throws Exception {
    // Tests multiple forks from the same base execution
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Start base execution
      ForkingAgent.ForkingInput input = new ForkingAgent.ForkingInput(6, 2);
      AgentInvoke baseInvoke = agent.initiate(input);
      ForkingAgent.ForkingResult baseResult = (ForkingAgent.ForkingResult) agent.result(baseInvoke);

      // Create multiple forks
      ForkingAgent.ForkingResult fork1 =
          (ForkingAgent.ForkingResult) agent.fork(baseInvoke, new HashMap<>());
      ForkingAgent.ForkingResult fork2 =
          (ForkingAgent.ForkingResult) agent.fork(baseInvoke, new HashMap<>());

      AgentInvoke fork3Invoke = agent.initiateFork(baseInvoke, new HashMap<>());
      ForkingAgent.ForkingResult fork3 = (ForkingAgent.ForkingResult) agent.result(fork3Invoke);

      // All forks should produce the same result as base
      assertNotNull("Fork 1 should not be null", fork1);
      assertNotNull("Fork 2 should not be null", fork2);
      assertNotNull("Fork 3 should not be null", fork3);

      // Verify all have same processed value (12)
      assertEquals("All should have same processed value", 12, baseResult.getProcessedValue());
      assertEquals("Fork 1 processed value should match", 12, fork1.getProcessedValue());
      assertEquals("Fork 2 processed value should match", 12, fork2.getProcessedValue());
      assertEquals("Fork 3 processed value should match", 12, fork3.getProcessedValue());

      // Verify all have same squared value (144)
      assertEquals("All should have same squared value", 144, baseResult.getSquared());
      assertEquals("Fork 1 squared should match", 144, fork1.getSquared());
      assertEquals("Fork 2 squared should match", 144, fork2.getSquared());
      assertEquals("Fork 3 squared should match", 144, fork3.getSquared());
    }
  }

  @Test
  public void testForkingWithDifferentInputs() throws Exception {
    // Tests forking behavior with different initial inputs
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Deploy the agent module
      ForkingAgent.ForkingModule module = new ForkingAgent.ForkingModule();
      ipc.launchModule(module, new LaunchConfig(2, 2));

      // Get agent manager and client
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("ForkingAgent");

      // Test case 1: Large numbers
      ForkingAgent.ForkingInput input1 = new ForkingAgent.ForkingInput(10, 5);
      AgentInvoke invoke1 = agent.initiate(input1);
      ForkingAgent.ForkingResult result1 = (ForkingAgent.ForkingResult) agent.result(invoke1);

      assertEquals("Result 1 processed value should be 50", 50, result1.getProcessedValue());
      assertEquals("Result 1 squared should be 2500", 2500, result1.getSquared());
      assertTrue("Result 1 should be valid", result1.isValid());

      // Fork from result 1
      ForkingAgent.ForkingResult fork1 =
          (ForkingAgent.ForkingResult) agent.fork(invoke1, new HashMap<>());
      assertEquals(
          "Fork 1 should match original", result1.getProcessedValue(), fork1.getProcessedValue());

      // Test case 2: Small numbers
      ForkingAgent.ForkingInput input2 = new ForkingAgent.ForkingInput(2, 3);
      AgentInvoke invoke2 = agent.initiate(input2);
      ForkingAgent.ForkingResult result2 = (ForkingAgent.ForkingResult) agent.result(invoke2);

      assertEquals("Result 2 processed value should be 6", 6, result2.getProcessedValue());
      assertEquals("Result 2 squared should be 36", 36, result2.getSquared());
      assertTrue("Result 2 should be valid", result2.isValid());

      // Fork from result 2
      ForkingAgent.ForkingResult fork2 =
          (ForkingAgent.ForkingResult) agent.fork(invoke2, new HashMap<>());
      assertEquals(
          "Fork 2 should match original", result2.getProcessedValue(), fork2.getProcessedValue());
    }
  }
}
