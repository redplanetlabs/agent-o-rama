package com.rpl.agentorama;

import com.rpl.agentorama.impl.*;
import com.rpl.agentorama.store.Store;
import java.util.Map;

/**
 * Interface for agent node functions to interact with the agent execution environment.
 *
 * Agent nodes are the computational units within an agent graph. This interface
 * provides access to agent objects, stores, streaming, recording trace information, and other execution capabilities.
 *
 * Example:
 * <pre>{@code
 * topology.newAgent("myAgent")
 *         .node("start", "process", (AgentNode agentNode, String input) -> {
 *           ChatModel model = agentNode.getAgentObject("openai-model");
 *           KeyValueStore<String, Integer> store = agentNode.getStore("$$myStore");
 *           store.put("key", 42);
 *           agentNode.emit("process", "Hello " + input);
 *         })
 *         .node("process", null, (AgentNode agentNode, String input) -> {
 *           agentNode.result("Processing: " + input);
 *         });
 * }</pre>
 */
public interface AgentNode extends AgentObjectFetcher, IFetchAgentClient {
  /**
   * Emits data to another node in the agent graph.
   *
   * The target node must be declared in the outputNodesSpec when creating the agent.
   *
   * @param node the name of the target node
   * @param args arguments to pass to the target node
   */
  void emit(String node, Object... args);

  /**
   * Sets the final result of the agent execution.
   *
   * This is a first-one wins situation: if multiple nodes return results in parallel,
   * only the first one will be the agent result and others will be dropped.
   *
   * @param arg the final result value
   */
  void result(Object arg);

  /**
   * Gets a store by name for persistent data access.
   *
   * Store names must start with "$$". Stores are declared in the agent topology using:
   * - {@link AgentTopology#declareKeyValueStore(String, Class, Class)} for simple key-value storage
   * - {@link AgentTopology#declareDocumentStore(String, Class, Object...)} for schema-flexible nested data
   * - {@link AgentTopology#declarePStateStore(String, Class)} for direct Rama PState access
   *
   * @param name the name of the store (must start with "$$")
   * @return the store instance
   */
  <T extends Store> T getStore(String name);


  AgentClient getMirrorAgentClient(String moduleName, String agentName);

  // TODO: add methods to get local and mirror depots and query topologies
  //  - what about mirror PStates... or mirror stores?
  //    - probably don't want to be able to write to mirror stores?
  //      - can enforce that, which is fine
  //  - for mirrors:
  //    - need to specify the dependency and give it a unique name
  //    - API here is the same
  //  - what about for depots / query topologies? those could be declared normally...
  //    - same can be said of PState mirrors as well...
  //    - not the worst thing in the world to require it to be specified twice
  //      - no, Rama doesn't allow duplicate mirrors...
  //    - but doesn't Rama error if you specify the same mirror multiple times?
  //  - either have to be permissive and dynamic without declaration, or need to use rpl.rama.distributed.core/thread-module-
  //    instance-info
  //    - even with using thread-module-instance-info, without mirrors it's not enforced at deploy time, including for the other
  //     module! so probably should just make it dynamic
  //      - only issue with this is it's a bit inconsistent with declare-cluster-agent
  //        - could remove that API pretty safely
  // - so make:
  //   - getMirrorStore
  //      - this queries other module for store info (if it exists) to determine what type to make
  //        - returned store should NOT allow writes
  //   - getDepot
  //   - getMirrorDepot
  //   - getQuery
  //   - getMirrorQuery
  //   - getMirrorAgent?
  //      - and remove declareClusterAgent, making it dynamic instead

  /**
   * Streams a chunk of data to clients.
   *
   * @param chunk the data chunk to stream
   */
  void streamChunk(Object chunk);

  /**
   * Records a nested operation for tracing and analytics.
   *
   * Nested operations track internal operations like model calls, database access,
   * and tool calls within an agent execution. The info map provides type-specific
   * metadata for the operation.
   *
   * Special info map usage for certain operation types that gets incorporated into analytics:
   * - model call: "inputTokenCount", "outputTokenCount", "totalTokenCount", "failure" (exception string for failures)
   */
  void recordNestedOp(NestedOpType nestedOpType, long startTimeMillis, long finishTimeMillis, Map<String, Object> info);

  /**
   * Requests human input during agent execution.
   *
   * This method blocks until human input is provided. The agent execution
   * will pause until the input is received. The agent will remain in a waiting state unti
   * the human provides a response through the client API or web UI. Since nodes run on virtual threads, this is efficient.
   *
   * @param prompt the prompt to show to the human
   * @return the human's response
   */
  String getHumanInput(String prompt);

  /**
   * Gets metadata associated with this agent execution.
   *
   * @return map of metadata key-value pairs
   */
  Map<String, Object> getMetadata();
}
