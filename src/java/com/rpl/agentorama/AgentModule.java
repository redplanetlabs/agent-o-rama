package com.rpl.agentorama;

import com.rpl.rama.*;

/**
 * Base class for creating agent modules that can be deployed to a Rama cluster.
 *
 * Alternatively, a regular RamaModule can be defined with an {@link AgentTopology} explicitly created to add agents to it.
 *
 * Agent modules are deployable units containing agent definitions, stores, and objects.
 * They extend RamaModule and provide a simplified interface for defining agents
 * and their associated infrastructure.
 *
 * The topology provides the configuration context for:
 * <ul>
 * <li>Declaring agents with {@link AgentTopology#newAgent(String)}</li>
 * <li>Declaring stores: {@link AgentTopology#declareKeyValueStore(String, Class, Class)}, {@link AgentTopology#declareDocumentStore(String, Class, Object...)}, {@link AgentTopology#declarePStateStore(String, Class)}</li>
 * <li>Declaring agent objects: {@link AgentTopology#declareAgentObject(String, Object)}, {@link AgentTopology#declareAgentObjectBuilder(String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring evaluators: {@link AgentTopology#declareEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}, {@link AgentTopology#declareComparativeEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}, {@link AgentTopology#declareSummaryEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring actions: {@link AgentTopology#declareActionBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring cluster agents: {@link AgentTopology#declareClusterAgent(String, String, String)}</li>
 * </ul>
 *
 * Example:
 * <pre>{@code
 * public class MyAgentModule extends AgentModule {
 *   @Override
 *   protected void defineAgents(AgentTopology topology) {
 *     topology.declareKeyValueStore("$$myStore", String.class, Integer.class);
 *
 *     topology.newAgent("myAgent")
 *             .node("start", "process", (AgentNode agentNode, String input) -> {
 *               KeyValueStore<String, Integer> store = agentNode.getStore("$$myStore");
 *               store.put("key", 42);
 *               agentNode.emit("process", "Hello " + input);
 *             })
 *             .node("process", null, (AgentNode agentNode, String input) -> {
 *               agentNode.result("Processing: " + input);
 *             });
 *   }
 * }
 * }</pre>
 */
public abstract class AgentModule implements RamaModule {
  protected abstract void defineAgents(AgentTopology topology);

  @Override
  public void define(Setup setup, Topologies topologies) {
    AgentTopology at = AgentTopology.create(setup, topologies);
    defineAgents(at);
    at.define();
  }
}
