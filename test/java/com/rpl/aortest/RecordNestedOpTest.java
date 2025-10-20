package com.rpl.aortest;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.NestedOpType;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.HashMap;
import java.util.Map;

/** Tests for AgentNode.recordNestedOp. */
public class RecordNestedOpTest {

  public static class RecordNestedOpModule extends AgentModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.newAgent("RecordNestedOpAgent").node("process", null, new RecordNestedOpFunction());
    }
  }

  public static class RecordNestedOpFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String input) {
      long startTime = System.currentTimeMillis();

      try {
        Thread.sleep(10);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
      }

      long finishTime = System.currentTimeMillis();

      Map<String, Object> info = new HashMap<>();
      info.put("operation", "test-op");
      info.put("input", input);

      agentNode.recordNestedOp(NestedOpType.MODEL_CALL, startTime, finishTime, info);

      agentNode.result("processed: " + input);
    }
  }

  public static void testRecordNestedOpWithModelCall() throws Exception {
    // Tests that recordNestedOp successfully records a MODEL_CALL operation
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RecordNestedOpModule module = new RecordNestedOpModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("RecordNestedOpAgent");

      String result = (String) agent.invoke("test-input");
      if (result == null) {
        throw new AssertionError("Agent should return a result");
      }
      if (!"processed: test-input".equals(result)) {
        throw new AssertionError("Expected 'processed: test-input' but got '" + result + "'");
      }
    }
  }

  public static class MultipleOpsFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String input) {
      long startTime1 = System.currentTimeMillis();
      long finishTime1 = startTime1 + 5;

      Map<String, Object> info1 = new HashMap<>();
      info1.put("operation", "db-read");
      agentNode.recordNestedOp(NestedOpType.DB_READ, startTime1, finishTime1, info1);

      long startTime2 = finishTime1 + 1;
      long finishTime2 = startTime2 + 5;

      Map<String, Object> info2 = new HashMap<>();
      info2.put("operation", "db-write");
      agentNode.recordNestedOp(NestedOpType.DB_WRITE, startTime2, finishTime2, info2);

      agentNode.result("multi-op: " + input);
    }
  }

  public static void testRecordMultipleNestedOps() throws Exception {
    // Tests that multiple recordNestedOp calls can be made in sequence
    try (InProcessCluster ipc = InProcessCluster.create()) {
      AgentModule module =
          new AgentModule() {
            @Override
            protected void defineAgents(AgentTopology topology) {
              topology.newAgent("MultiOpsAgent").node("process", null, new MultipleOpsFunction());
            }
          };
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MultiOpsAgent");

      String result = (String) agent.invoke("test");
      if (result == null) {
        throw new AssertionError("Agent should return a result");
      }
      if (!"multi-op: test".equals(result)) {
        throw new AssertionError("Expected 'multi-op: test' but got '" + result + "'");
      }
    }
  }

  public static class AllOpTypesFunction implements RamaVoidFunction2<AgentNode, Integer> {

    @Override
    public void invoke(AgentNode agentNode, Integer input) {
      NestedOpType[] opTypes = {
        NestedOpType.STORE_READ,
        NestedOpType.STORE_WRITE,
        NestedOpType.DB_READ,
        NestedOpType.DB_WRITE,
        NestedOpType.MODEL_CALL,
        NestedOpType.TOOL_CALL,
        NestedOpType.AGENT_CALL,
        NestedOpType.HUMAN_INPUT,
        NestedOpType.OTHER
      };

      long baseTime = System.currentTimeMillis();

      for (int i = 0; i < opTypes.length; i++) {
        long startTime = baseTime + (i * 10);
        long finishTime = startTime + 5;

        Map<String, Object> info = new HashMap<>();
        info.put("type", opTypes[i].toString());
        info.put("index", String.valueOf(i));

        agentNode.recordNestedOp(opTypes[i], startTime, finishTime, info);
      }

      agentNode.result(opTypes.length);
    }
  }

  public static void testRecordAllNestedOpTypes() throws Exception {
    // Tests that all NestedOpType enum values can be recorded
    try (InProcessCluster ipc = InProcessCluster.create()) {
      AgentModule module =
          new AgentModule() {
            @Override
            protected void defineAgents(AgentTopology topology) {
              topology.newAgent("AllTypesAgent").node("process", null, new AllOpTypesFunction());
            }
          };
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("AllTypesAgent");

      Integer result = (Integer) agent.invoke(42);
      if (result == null) {
        throw new AssertionError("Agent should return a result");
      }
      if (!Integer.valueOf(9).equals(result)) {
        throw new AssertionError("Expected 9 but got " + result);
      }
    }
  }

  public static boolean runAllTests() throws Exception {
    System.out.println("Running RecordNestedOp tests...");
    testRecordNestedOpWithModelCall();
    System.out.println("✓ testRecordNestedOpWithModelCall passed");
    testRecordMultipleNestedOps();
    System.out.println("✓ testRecordMultipleNestedOps passed");
    testRecordAllNestedOpTypes();
    System.out.println("✓ testRecordAllNestedOpTypes passed");
    System.out.println("All RecordNestedOp tests passed!");
    return true;
  }
}
