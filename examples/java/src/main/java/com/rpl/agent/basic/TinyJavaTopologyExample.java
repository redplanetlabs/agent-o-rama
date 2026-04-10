package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.store.KeyValueStore;
import com.rpl.agentorama.store.PStateStore;
import com.rpl.rama.Depot;
import com.rpl.rama.Path;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule;
import com.rpl.rama.module.StreamTopology;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

/**
 * Demonstrates the two integration patterns from the agent-o-rama wiki
 * "Integrating with regular Rama modules":
 * https://github.com/redplanetlabs/agent-o-rama/wiki/Integrating-with-regular-Rama-modules
 *
 * <h2>Pattern 1 — Accessing Rama objects from within agent nodes</h2>
 * <p>Use {@link AgentNode#getDepot(String)}, {@link AgentNode#getStore(String)}, etc. inside
 * node functions. See {@link WikiAccessingRamaObjectsModule}: agents append to a local depot
 * and read/write a {@link KeyValueStore}.
 *
 * <h2>Pattern 2 — Adding agents to regular Rama modules</h2>
 * <p>Implement {@link RamaModule} directly (do not extend {@code AgentModule}), create
 * {@link AgentTopology} with {@link AgentTopology#create}, declare depots/stores/agents, then
 * call {@link AgentTopology#define}. The same structure appears in
 * {@link WikiAccessingRamaObjectsModule} (wiki "Basic pattern") and
 * {@link WikiStreamPStateModule} (stream topology + agents).
 */
public class TinyJavaTopologyExample {

  /**
   * Wiki "Basic pattern" + depot access from agent nodes (Pattern 1 + 2).
   *
   * <p>Matches the wiki snippet: {@code RamaModule}, {@code declareDepot},
   * {@code AgentTopology.create}, {@code declareKeyValueStore}, {@code newAgent},
   * {@code agentTopology.define()}. Agent nodes use {@code getDepot} and {@code getStore} as in
   * the wiki sections "Accessing depots" and "Accessing PStates via stores".
   */
  public static class WikiAccessingRamaObjectsModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*events-depot", Depot.random());

      AgentTopology agentTopology = AgentTopology.create(setup, topologies);

      agentTopology.declareKeyValueStore("$$cache", String.class, String.class);
      agentTopology
          .newAgent("myAgent")
          .node(
              "process",
              null,
              (AgentNode agentNode, String input) -> {
                KeyValueStore<String, String> store = agentNode.getStore("$$cache");
                store.put("last-input", input);
                agentNode.getDepot("*events-depot").append(input);
                agentNode.result("Processed: " + input);
              });

      agentTopology.define();
    }
  }

  /**
   * Regular Rama module with a {@link StreamTopology} that owns a PState, plus agents.
   *
   * <p>Shows Pattern 2 (manual {@link AgentTopology}) together with stream processing: agent
   * nodes read the stream-built PState via {@link PStateStore} (read-only for this PState) and
   * append to depots to drive stream-side updates (Pattern 1 — depots / stores).
   */
  public static class WikiStreamPStateModule implements RamaModule {

    @Override
    public void define(Setup setup, Topologies topologies) {
      setup.declareDepot("*events", Depot.random());
      setup.declareDepot("*resetCommands", Depot.random());

      StreamTopology st = topologies.stream("eventCounter");
      st.pstate("$$eventCounts", PState.mapSchema(String.class, Long.class));
      st.source("*events")
          .out("*event")
          .localTransform(
              "$$eventCounts",
              Path.key("*event").term(c -> c == null ? 1L : (Long) c + 1L));
      st.source("*resetCommands").out("*event").localTransform(
          "$$eventCounts", Path.key("*event").termVal(0L));

      AgentTopology at = AgentTopology.create(setup, topologies);

      at.newAgent("RecordAgent")
          .node(
              "record",
              null,
              (AgentNode an, String eventName) -> {
                an.getDepot("*events").append(eventName);
                an.result("recorded: " + eventName);
              });

      at.newAgent("CountAgent")
          .node(
              "count",
              null,
              (AgentNode an, String eventName) -> {
                PStateStore counts = an.getStore("$$eventCounts");
                Long c = (Long) counts.selectOne(eventName, Path.key(eventName));
                an.result(eventName + " count = " + (c == null ? 0L : c));
              });

      at.newAgent("ResetAgent")
          .node(
              "reset",
              null,
              (AgentNode an, String eventName) -> {
                an.getDepot("*resetCommands").append(eventName);
                an.result("reset: " + eventName);
              });

      at.define();
    }
  }

  public static void main(String[] args) throws Exception {
    System.out.println("=== Pattern 1 & 2: Wiki basic Rama module + depots/stores in agents ===");
    runWikiBasicDemo();

    System.out.println("\n=== Stream topology PState + agents (read PState, append depots) ===");
    runStreamPStateDemo();
  }

  private static void runWikiBasicDemo() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new WikiAccessingRamaObjectsModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient agent = manager.getAgentClient("myAgent");
      System.out.println((String) agent.invoke("hello"));
    }
  }

  private static void runStreamPStateDemo() throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new WikiStreamPStateModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient recorder = manager.getAgentClient("RecordAgent");
      AgentClient counter = manager.getAgentClient("CountAgent");
      AgentClient resetter = manager.getAgentClient("ResetAgent");

      recorder.invoke("login");
      recorder.invoke("login");
      recorder.invoke("login");
      Thread.sleep(500);
      System.out.println((String) counter.invoke("login"));

      resetter.invoke("login");
      Thread.sleep(500);
      System.out.println((String) counter.invoke("login"));
    }
  }
}
