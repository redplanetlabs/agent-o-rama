package com.rpl.aortest;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentModule;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.rama.Depot;
import com.rpl.rama.Path;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.module.StreamTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Tests for AgentNode.getClusterRetriever. */
public class ClusterRetrieverTest {

  public static class OtherModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*depot", Depot.hashBy("identity"));

      StreamTopology st = topologies.stream("s");
      st.pstate("$$p", PState.mapSchema(String.class, Long.class));
      st.source("*depot")
          .out("*k")
          .localTransform(
              "$$p",
              Path.key("*k").term(c -> c == null ? 1L : (Long) c + 1L));
    }
  }

  private static AgentModule agentModule(String otherModuleName) {
    return new AgentModule() {
      @Override
      protected void defineAgents(AgentTopology topology) {
        topology
            .newAgent("foo")
            .node(
                "start",
                null,
                (AgentNode agentNode, String k) -> {
                  ClusterManagerBase retriever = agentNode.getClusterRetriever();
                  Depot depot = retriever.clusterDepot(otherModuleName, "*depot");
                  PState p = retriever.clusterPState(otherModuleName, "$$p");

                  depot.append(k);

                  Path kpath = Path.key(k);
                  List<Object> res = new ArrayList<>();
                  res.add(p.selectOne(kpath));
                  res.add(p.selectOne(k, kpath));
                  res.add(p.select(kpath));
                  res.add(p.select(k, kpath));

                  agentNode.result(res);
                });
      }
    };
  }

  public static void testClusterRetriever() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      OtherModule otherModule = new OtherModule();
      ipc.launchModule(otherModule, new LaunchConfig(1, 1));

      String otherModuleName = otherModule.getModuleName();
      AgentModule agentModule = agentModule(otherModuleName);
      ipc.launchModule(agentModule, new LaunchConfig(2, 2));

      AgentManager manager = AgentManager.create(ipc, agentModule.getModuleName());
      AgentClient foo = manager.getAgentClient("foo");

      @SuppressWarnings("unchecked")
      List<Object> res = (List<Object>) foo.invoke("a");
      if (res == null) {
        throw new AssertionError("Agent should return a result");
      }
      if (res.size() != 4) {
        throw new AssertionError("Expected 4 results but got " + res.size());
      }
      if (!Long.valueOf(1L).equals(res.get(0))) {
        throw new AssertionError("Expected selectOne result 1 but got " + res.get(0));
      }
      if (!Long.valueOf(1L).equals(res.get(1))) {
        throw new AssertionError("Expected partitioned selectOne result 1 but got " + res.get(1));
      }
      @SuppressWarnings("unchecked")
      List<Long> selectRes = (List<Long>) res.get(2);
      if (!Arrays.asList(1L).equals(selectRes)) {
        throw new AssertionError("Expected select result [1] but got " + selectRes);
      }
      @SuppressWarnings("unchecked")
      List<Long> pkeySelectRes = (List<Long>) res.get(3);
      if (!Arrays.asList(1L).equals(pkeySelectRes)) {
        throw new AssertionError("Expected partitioned select result [1] but got " + pkeySelectRes);
      }
    }
  }

  public static boolean runAllTests() throws Exception {
    System.out.println("Running ClusterRetriever tests...");
    testClusterRetriever();
    System.out.println("✓ testClusterRetriever passed");
    System.out.println("All ClusterRetriever tests passed!");
    return true;
  }
}
