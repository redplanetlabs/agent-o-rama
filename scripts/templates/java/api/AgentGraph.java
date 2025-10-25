package com.rpl.agentorama;

import com.rpl.agentorama.impl.BuiltInAgg;
import com.rpl.agentorama.ops.*;
import com.rpl.rama.ops.*;

/**
 * Builder interface for defining agent execution graphs.
 * 
 * AgentGraph provides a fluent API for building agent execution graphs with nodes,
 * aggregation subgraphs, and control flow. Each agent is defined as a directed
 * graph where nodes represent computational units and edges represent data flow.
 * 
 * Example usage:
 * <pre>{@code
 * topology.newAgent("my-agent")
 *         .node("start", "process", (AgentNode agentNode, String input) -> {
 *             agentNode.emit("process", "Hello " + input);
 *         })
 *         .node("process", null, (AgentNode agentNode, String data) -> {
 *             agentNode.result("Processed: " + data);
 *         });
 * }</pre>
 */
public interface AgentGraph {
  
  /**
   * Sets the update mode for this agent graph.
   * 
   * Controls how in-flight agent executions are handled after the module is updated.
   * 
   * @param mode the update mode (CONTINUE, RESTART, or DROP)
   * @return this agent graph for method chaining
   */
  AgentGraph setUpdateMode(UpdateMode mode);
  
<% (dofor [i (range 0 (dec MAX-ARITY))] (str %>
  /**
   * Adds a node to the agent graph with <%= (javadoc/args-str i) %>.
   * 
   * Nodes are the fundamental computation units in agent graphs. Each node
   * receives data from upstream nodes and can emit data to downstream nodes
   * or return a final result.
   * 
   * @param name the name of the node (must be unique within the agent)
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param impl the function that implements the node logic
   * @return this agent graph for method chaining
   */
  <%= (mk-full-type-decl i) %> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i []) %> impl);<% )) %>
  
<% (dofor [i (range 0 (dec MAX-ARITY))] (str %>
  /**
   * Adds an aggregation start node with <%= (javadoc/args-str i) %> that scopes aggregation within a subgraph.
   * 
   * Aggregation start nodes work like regular nodes but define the beginning
   * of an aggregation subgraph. They must have a corresponding {@link #aggNode(String, Object, RamaAccumulatorAgg, RamaVoidFunction3)}
   * downstream. Within the aggregation subgraph, edges must stay within
   * the subgraph and cannot connect to nodes outside of it.
   * 
   * The return value of the node function is passed to the downstream aggregation node
   * as its last argument, allowing propagation of non-aggregated information
   * downstream post-aggregation.
   * 
   * @param name the name of the node
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param impl the function that implements the node logic. Return value is passed
   *             to downstream aggregation node as last argument.
   * @return this agent graph for method chaining
   */
  <%= (mk-full-type-decl i) %> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i ["Object"]) %> impl);<% )) %>
  
  /**
   * Adds an aggregation node that collects and combines results using a Rama accumulator aggregator.
   * 
   * Aggregation nodes gather results from parallel processing nodes and combine
   * them using a specified aggregation function. They receive both the collected
   * results and any return value from the aggregation start node.
   * 
   * @param name the name of the node
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param agg the Rama accumulator aggregator for combining results
   * @param impl the function that processes the aggregated results. Takes
   *             (agentNode, aggregatedValue, aggStartResult) where aggStartResult
   *             is the return value from the corresponding aggregation start node
   * @return this agent graph for method chaining
   */
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaAccumulatorAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  
  /**
   * Adds an aggregation node that collects and combines results using a Rama combiner aggregator.
   * 
   * Aggregation nodes gather results from parallel processing nodes and combine
   * them using a specified aggregation function. They receive both the collected
   * results and any return value from the aggregation start node.
   * 
   * @param name the name of the node
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param agg the Rama combiner aggregator for combining results
   * @param impl the function that processes the aggregated results. Takes
   *             (agentNode, aggregatedValue, aggStartResult) where aggStartResult
   *             is the return value from the corresponding aggregation start node
   * @return this agent graph for method chaining
   */
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaCombinerAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  
  /**
   * Adds an aggregation node that collects and combines results using a multi-aggregator.
   * 
   * Aggregation nodes gather results from parallel processing nodes and combine
   * them using a specified aggregation function. They receive both the collected
   * results and any return value from the aggregation start node.
   * 
   * Multi-aggregators provide dispatch-based aggregation where different values
   * are processed by different aggregation functions based on a dispatch key.
   * 
   * @param name the name of the node
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param agg the multi-aggregator for dispatch-based aggregation
   * @param impl the function that processes the aggregated results. Takes
   *             (agentNode, aggregatedValue, aggStartResult) where aggStartResult
   *             is the return value from the corresponding aggregation start node
   * @return this agent graph for method chaining
   */
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, MultiAgg.Impl agg, RamaVoidFunction3<AgentNode, S, T> impl);
  
  /**
   * Adds an aggregation node that collects and combines results using a built-in aggregator.
   * 
   * Aggregation nodes gather results from parallel processing nodes and combine
   * them using a specified aggregation function. They receive both the collected
   * results and any return value from the aggregation start node.
   * 
   * Built-in aggregators provide common aggregation patterns like sum, count, min, max, etc.
   * See {@link BuiltIn} for available built-in aggregators.
   * 
   * @param name the name of the node
   * @param outputNodesSpec target node name(s) for emissions, or null for terminal nodes.
   *                        Can be a string, array of strings, or null. Calls to
   *                        {@link AgentNode#emit(String, Object...)} inside the node function
   *                        must target one of these declared nodes.
   * @param agg the built-in aggregator for combining results
   * @param impl the function that processes the aggregated results. Takes
   *             (agentNode, aggregatedValue, aggStartResult) where aggStartResult
   *             is the return value from the corresponding aggregation start node
   * @return this agent graph for method chaining
   */
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, BuiltInAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
}
