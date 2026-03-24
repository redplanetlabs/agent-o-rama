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
 * The AgentTopology creates and manages an internal StreamTopology; access it
 * with {@code agentTopology.getStreamTopology()} to wire depot → PState processing.
 * Agent nodes get a fully read/write {@code PStateStore} via {@code agentNode.getStore()}.
 *
 * <pre>{@code
 *   AgentTopology at = AgentTopology.create(setup, topologies);
 *   at.declarePStateStore("$$counts", PState.mapSchema(...));   // AgentTopology owns it
 *   at.getStreamTopology().source("*events")...localTransform("$$counts", ...);
 *   at.newAgent("...").node(...);
 *   at.define();
 * }</pre>
 *
 * <h2>Strategy 2 — External StreamTopology owns the PState ({@link ExternalPStateModule})</h2>
 * <p>Declare the PState directly on a {@code StreamTopology} obtained from
 * {@code topologies.stream()}, <em>before</em> creating the AgentTopology.
 * The AgentTopology is created afterwards and accesses the PState by name —
 * the PState lives in a separate topology.
 *
 * <pre>{@code
 *   StreamTopology st = topologies.stream("event-counter");
 *   st.pstate("$$counts", PState.mapSchema(...));              // external topology owns it
 *   st.source("*events")...localTransform("$$counts", ...);
 *   AgentTopology at = AgentTopology.create(setup, topologies);
 *   at.newAgent("...").node(...);                              // agents access $$counts by name
 *   at.define();
 * }</pre>
 *
 * <h2>Read/write access</h2>
 * <p>PStates declared via {@code agentTopology.declarePStateStore()} are read/write
 * from agent nodes. PStates declared outside the agent topology (Strategy 2) are
 * read-only from agent nodes — only the stream topology that declared them should
 * write to them.
 */
public class TinyJavaTopologyExample {

  // ═══════════════════════════════════════════════════════════════════════════
  // Strategy 1: AgentTopology declares and owns the PState
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * The AgentTopology creates its own internal StreamTopology.
   * We declare the PState on that topology via {@code at.declarePStateStore()},
   * then retrieve the internal StreamTopology with {@code at.getStreamTopology()}
   * to wire up the depot-to-PState processing.
   *
   * <p>Order:
   * <ol>
   *   <li>{@code setup.declareDepot()} — depots must be declared first
   *   <li>{@code AgentTopology.create(setup, topologies)}
   *   <li>{@code at.declarePStateStore(...)} — PState registered on AgentTopology
   *   <li>{@code at.getStreamTopology().source(...)} — stream processing
   *   <li>{@code at.newAgent(...)} — agent definitions
   *   <li>{@code at.define()} — finalize; required when implementing RamaModule directly
   * </ol>
   */
  public static class AgentOwnedPStateModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {

      // 1. Declare depot
      setup.declareDepot("*events", Depot.random());

      // 2. Create AgentTopology
      AgentTopology at = AgentTopology.create(setup, topologies);

      // 3. Declare PState through the AgentTopology
      //    (this registers it on the AgentTopology's internal StreamTopology)
      at.declarePStateStore(
          "$$event-counts",
          PState.mapSchema(String.class, Long.class));

      // 4. Wire stream processing via the AgentTopology's internal StreamTopology
      at.getStreamTopology()
        .source("*events").out("*event")
        .localTransform(
            "$$event-counts",
            Path.key("*event").term(c -> c == null ? 1L : (Long) c + 1L));

      // 5. Define agents
      at.newAgent("RecordAgent")
        .node("record", null,
            (AgentNode an, String eventName) -> {
              an.getDepot("*events").append(eventName);
              an.result("recorded: " + eventName);
            });

      at.newAgent("CountAgent")
        .node("count", null,
            (AgentNode an, String eventName) -> {
              PStateStore counts = an.getStore("$$event-counts");
              Long c = (Long) counts.selectOne(eventName, Path.key(eventName));
              an.result(eventName + " count = " + (c == null ? 0L : c));
            });

      // Strategy 1 only: because the AgentTopology owns $$event-counts, agents
      // can also write to it directly via transform() — not just read it.
      // This ResetAgent zeroes out a counter without going through the depot.
      at.newAgent("ResetAgent")
        .node("reset", null,
            (AgentNode an, String eventName) -> {
              PStateStore counts = an.getStore("$$event-counts");
              counts.transform(eventName, Path.key(eventName).termVal(0L));
              an.result("reset: " + eventName);
            });

      // 6. Finalize
      at.define();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Strategy 2: External StreamTopology declares the PState
  // ═══════════════════════════════════════════════════════════════════════════

  /**
   * A separate {@code StreamTopology} (obtained from {@code topologies.stream()})
   * declares and owns the PState. The AgentTopology is created afterwards and
   * accesses the PState purely by name — it doesn't own it.
   *
   * <p>Order:
   * <ol>
   *   <li>{@code setup.declareDepot()} — depots first
   *   <li>{@code topologies.stream()} → declare PState on it → wire source
   *   <li>{@code AgentTopology.create(setup, topologies)} — created after the stream topology
   *   <li>{@code at.newAgent(...)} — agents reference {@code "$$event-counts"} by name
   *   <li>{@code at.define()}
   * </ol>
   *
   * <p>The structural difference from Strategy 1: the PState is owned by the
   * independently-named StreamTopology ("event-counter"), not by the AgentTopology's
   * internal one. The AgentTopology resolves it via
   * {@code .getLocalPState(ramaClients, name)} at runtime.
   */
  public static class ExternalPStateModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {

      // 1. Declare depot
      setup.declareDepot("*events", Depot.random());

      // 2. Create an independent StreamTopology and declare the PState on it
      StreamTopology st = topologies.stream("event-counter");
      st.pstate("$$event-counts", PState.mapSchema(String.class, Long.class));
      st.source("*events").out("*event")
        .localTransform(
            "$$event-counts",
            Path.key("*event").term(c -> c == null ? 1L : (Long) c + 1L));

      // 3. Create AgentTopology *after* the stream topology
      //    It doesn't declare $$event-counts itself; it looks it up by name at runtime.
      AgentTopology at = AgentTopology.create(setup, topologies);

      // 4. Define agents — same read API as Strategy 1 from the agent side.
      //    Since $$event-counts is owned by the external StreamTopology, agents
      //    treat it as read-only (only the stream topology should write to it).
      at.newAgent("RecordAgent")
        .node("record", null,
            (AgentNode an, String eventName) -> {
              an.getDepot("*events").append(eventName);
              an.result("recorded: " + eventName);
            });

      at.newAgent("CountAgent")
        .node("count", null,
            (AgentNode an, String eventName) -> {
              // Read-only access: getStore() resolves $$event-counts by name
              // from the module. The PState is owned by the external topology.
              PStateStore counts = an.getStore("$$event-counts");
              Long c = (Long) counts.selectOne(eventName, Path.key(eventName));
              an.result(eventName + " count = " + (c == null ? 0L : c));
            });

      // 5. Finalize
      at.define();
    }
  }

  // ═══════════════════════════════════════════════════════════════════════════
  // Driver — runs both modules to show identical observable behaviour
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

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("purchase");
      recorder.invoke("login");

      Thread.sleep(500);

      System.out.println((String) counter.invoke("login"));    // login count = 3
      System.out.println((String) counter.invoke("purchase")); // purchase count = 1
      System.out.println((String) counter.invoke("logout"));   // logout count = 0
    }
  }
}
