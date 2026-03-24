package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.store.PStateStore;
import com.rpl.rama.Depot;
import com.rpl.rama.Path;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule;
import com.rpl.rama.module.StreamTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Two modules demonstrating the two integration strategies from the
 * agent-o-rama wiki "Integrating with regular Rama modules":
 *
 * <h2>Strategy 1 — AgentTopology owns the PState ({@link AgentOwnedPStateModule})</h2>
 * <p>Declare the PState via {@code agentTopology.declarePStateStore()}.
 * Because the AgentTopology owns the PState, agent nodes can read <em>and write</em>
 * it directly via {@code PStateStore.transform()}.
 *
 * <h2>Strategy 2 — External StreamTopology owns the PState ({@link ExternalPStateModule})</h2>
 * <p>Declare the PState on a {@code StreamTopology} obtained from
 * {@code topologies.stream()}, <em>before</em> creating the AgentTopology.
 * Because the PState belongs to a different topology, agents treat it as read-only.
 * To modify it, agents must append to a depot that the stream topology consumes —
 * all writes go through that depot, never through {@code PStateStore.transform()}.
 */
public class TinyJavaTopologyExample {

  // ═══════════════════════════════════════════════════════════════════════════
  // Strategy 1: AgentTopology declares and owns the PState
  //   → agents can read AND write the PState directly
  // ═══════════════════════════════════════════════════════════════════════════
  public static class AgentOwnedPStateModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*events", Depot.random());

      AgentTopology at = AgentTopology.create(setup, topologies);

      // PState declared on the AgentTopology → agents can write it directly.
      at.declarePStateStore("$$eventCounts", PState.mapSchema(String.class, Long.class));

      at.getStreamTopology()
        .source("*events").out("*event")
        .localTransform(
            "$$eventCounts",
            Path.key("*event").term(c -> c == null ? 1L : (Long) c + 1L));

      // RecordAgent: append an event to the depot;
      // the stream topology above increments $$eventCounts asynchronously.
      at.newAgent("RecordAgent")
        .node("record", null,
            (AgentNode an, String eventName) -> {
              an.getDepot("*events").append(eventName);
              an.result("recorded: " + eventName);
            });

      at.newAgent("CountAgent")
        .node("count", null,
            (AgentNode an, String eventName) -> {
              PStateStore counts = an.getStore("$$eventCounts");
              Long c = (Long) counts.selectOne(eventName, Path.key(eventName));
              an.result(eventName + " count = " + (c == null ? 0L : c));
            });

      // ResetAgent: because the AgentTopology owns $$eventCounts, an agent node
      // can call transform() on it directly — no depot round-trip needed.
      at.newAgent("ResetAgent")
        .node("reset", null,
            (AgentNode an, String eventName) -> {
              PStateStore counts = an.getStore("$$eventCounts");
              counts.transform(eventName, Path.key(eventName).termVal(0L));
              an.result("reset: " + eventName);
            });

      at.define();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Strategy 2: External StreamTopology owns the PState
  //   → agents read the PState directly, but must write through a depot
  // ═══════════════════════════════════════════════════════════════════════════
  public static class ExternalPStateModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*events", Depot.random());
      // Separate depot for reset commands — agents write here to trigger PState changes.
      setup.declareDepot("*resetCommands", Depot.random());

      // External stream topology owns $$eventCounts.
      StreamTopology st = topologies.stream("eventCounter");
      st.pstate("$$eventCounts", PState.mapSchema(String.class, Long.class));
      st.source("*events").out("*event")
        .localTransform(
            "$$eventCounts",
            Path.key("*event").term(c -> c == null ? 1L : (Long) c + 1L));
      // Reset path: consuming *resetCommands zeroes out the named counter.
      st.source("*resetCommands").out("*event")
        .localTransform("$$eventCounts", Path.key("*event").termVal(0L));

      AgentTopology at = AgentTopology.create(setup, topologies);

      at.newAgent("RecordAgent")
        .node("record", null,
            (AgentNode an, String eventName) -> {
              an.getDepot("*events").append(eventName);
              an.result("recorded: " + eventName);
            });

      // CountAgent: read-only access to the externally-owned PState.
      at.newAgent("CountAgent")
        .node("count", null,
            (AgentNode an, String eventName) -> {
              PStateStore counts = an.getStore("$$eventCounts");
              Long c = (Long) counts.selectOne(eventName, Path.key(eventName));
              an.result(eventName + " count = " + (c == null ? 0L : c));
            });

      // ResetAgent: cannot call transform() on $$eventCounts directly because it
      // belongs to a different topology. Instead, append to *resetCommands and let
      // the stream topology apply the write.
      at.newAgent("ResetAgent")
        .node("reset", null,
            (AgentNode an, String eventName) -> {
              an.getDepot("*resetCommands").append(eventName);
              an.result("reset: " + eventName);
            });

      at.define();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Driver
  // ═══════════════════════════════════════════════════════════════════════════
  public static void main(String[] args) throws Exception {
    System.out.println("=== Strategy 1: AgentTopology owns the PState ===");
    runDemo(new AgentOwnedPStateModule());

    System.out.println("\n=== Strategy 2: External StreamTopology owns the PState ===");
    runDemo(new ExternalPStateModule());
  }

  private static void runDemo(RamaModule module) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter  = manager.getAgentClient("CountAgent");
      AgentClient resetter = manager.getAgentClient("ResetAgent");

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      System.out.println((String) counter.invoke("login")); // login count = 3

      resetter.invoke("login");
      Thread.sleep(500);
      System.out.println((String) counter.invoke("login")); // login count = 0
    }
  }
}
