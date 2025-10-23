// this file is auto-generated
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
  

  /**
   * Adds a node to the agent graph with zero arguments.
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
   AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction1<AgentNode> impl);
  /**
   * Adds a node to the agent graph with one argument.
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
  <T0> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction2<AgentNode,T0> impl);
  /**
   * Adds a node to the agent graph with two arguments.
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
  <T0,T1> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction3<AgentNode,T0,T1> impl);
  /**
   * Adds a node to the agent graph with three arguments.
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
  <T0,T1,T2> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
  /**
   * Adds a node to the agent graph with four arguments.
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
  <T0,T1,T2,T3> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
  /**
   * Adds a node to the agent graph with five arguments.
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
  <T0,T1,T2,T3,T4> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
  /**
   * Adds a node to the agent graph with six arguments.
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
  <T0,T1,T2,T3,T4,T5> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
  /**
   * Adds a node to the agent graph with seven arguments.
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
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
  

  /**
   * Adds an aggregation start node with zero arguments that scopes aggregation within a subgraph.
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
   AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction1<AgentNode,Object> impl);
  /**
   * Adds an aggregation start node with one argument that scopes aggregation within a subgraph.
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
  <T0> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction2<AgentNode,T0,Object> impl);
  /**
   * Adds an aggregation start node with two arguments that scopes aggregation within a subgraph.
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
  <T0,T1> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction3<AgentNode,T0,T1,Object> impl);
  /**
   * Adds an aggregation start node with three arguments that scopes aggregation within a subgraph.
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
  <T0,T1,T2> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction4<AgentNode,T0,T1,T2,Object> impl);
  /**
   * Adds an aggregation start node with four arguments that scopes aggregation within a subgraph.
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
  <T0,T1,T2,T3> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction5<AgentNode,T0,T1,T2,T3,Object> impl);
  /**
   * Adds an aggregation start node with five arguments that scopes aggregation within a subgraph.
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
  <T0,T1,T2,T3,T4> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction6<AgentNode,T0,T1,T2,T3,T4,Object> impl);
  /**
   * Adds an aggregation start node with six arguments that scopes aggregation within a subgraph.
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
  <T0,T1,T2,T3,T4,T5> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction7<AgentNode,T0,T1,T2,T3,T4,T5,Object> impl);
  /**
   * Adds an aggregation start node with seven arguments that scopes aggregation within a subgraph.
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
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6,Object> impl);
  
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
