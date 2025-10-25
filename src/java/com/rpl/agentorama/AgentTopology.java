package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import java.util.*;

/**
 * The agent topology provides the configuration context for defining agents,
 * stores, objects, evaluators, actions, and other infrastructure components
 * within an agent module.
 *
 * The topology provides the configuration context for:
 * <ul>
 * <li>Declaring agents with {@link #newAgent(String)}</li>
 * <li>Declaring stores: {@link #declareKeyValueStore(String, Class, Class)}, {@link #declareDocumentStore(String, Class, Object...)}, {@link #declarePStateStore(String, Class)}</li>
 * <li>Declaring agent objects: {@link #declareAgentObject(String, Object)}, {@link #declareAgentObjectBuilder(String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring evaluators: {@link #declareEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}, {@link #declareComparativeEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}, {@link #declareSummaryEvaluatorBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring actions: {@link #declareActionBuilder(String, String, com.rpl.rama.ops.RamaFunction1)}</li>
 * <li>Declaring cluster agents: {@link #declareClusterAgent(String, String, String)}</li>
 * </ul>
 * 
 * Example:
 * <pre>{@code
 * public class MyAgentModule extends AgentModule {
 *   @Override
 *   protected void defineAgents(AgentTopology topology) {
 *     topology.declareKeyValueStore("$$myStore", String.class, Integer.class);
 *
 *     topology.declareAgentObject("openai-api-key", "sk-...");
 *     topology.declareAgentObjectBuilder("openai-model", setup -> {
 *       String apiKey = setup.getAgentObject("openai-api-key");
 *       return OpenAiChatModel.builder()
 *         .apiKey(apiKey)
 *         .modelName("gpt-4o-mini")
 *         .build();
 *     });
 *
 *     // Create agents using builder pattern
 *     topology.newAgent("my-agent")
 *       .node("start", "process", (AgentNode agentNode, String input) -> {
 *         KeyValueStore<String, Integer> store = agentNode.getStore("$$myStore");
 *         store.put("key", 42);
 *         agentNode.emit("process", "Hello " + input);
 *       })
 *       .node("process", (AgentNode agentNode, String input) -> {
 *         OpenAiChatModel model = agentNode.getAgentObject("openai-model");
 *         agentNode.result(model.chat(input));
 *       });
 *   }
 * }
 * }</pre>
 */
public interface AgentTopology {

  /**
   * Creates an agent topology for defining agents and infrastructure. This is used when adding agents to a regular Rama module.
   *
   * @param setup the setup object for module configuration
   * @param topologies the topologies object for defining dataflow topologies
   * @return the agent topology instance
   */
  public static AgentTopology create(Setup setup, Topologies topologies) {
    return (AgentTopology) AORHelpers.CREATE_AGENT_TOPOLOGY.invoke(setup, topologies);
  }

  /**
   * Creates a new agent with the specified name.
   *
   * @param name the name of the agent
   * @return the agent graph for defining the agent's execution flow
   */
  AgentGraph newAgent(String name);

  /**
   * Creates a new tools agent with the specified name and tools.
   *
   * Tools agents are specialized agents for executing tool calls from AI models.
   * They provide a standardized interface for function calling and tool execution.
   *
   * @param name the name of the agent
   * @param tools the list of tools available to the agent
   * @return the agent graph for defining the agent's execution flow
   */
  AgentGraph newToolsAgent(String name, List<ToolInfo> tools);

  /**
   * Creates a new tools agent with the specified name, tools, and options.
   *
   * @param name the name of the agent
   * @param tools the list of tools available to the agent
   * @param options configuration options for the tools agent
   * @return the agent graph for defining the agent's execution flow
   */
  AgentGraph newToolsAgent(String name, List<ToolInfo> tools, ToolsAgentOptions options);

  /**
   * Declares a key-value store for simple typed persistent storage.
   *
   * Store names must start with "$$". Key-value stores provide basic
   * persistent storage with type safety for simple data structures.
   *
   * @param name the name of the store (must start with "$$")
   * @param keyClass the class type for keys
   * @param valClass the class type for values
   */
  void declareKeyValueStore(String name, Class keyClass, Class valClass);

  /**
   * Declares a document store for schema-flexible persistent storage.
   *
   * Document stores provide flexible storage for nested data structures
   * with schema validation capabilities.
   *
   * @param name the name of the store (must start with "$$")
   * @param keyClass the class type for keys
   * @param keyAndValClasses alternating key and value class types for the document schema
   */
  void declareDocumentStore(String name, Class keyClass, Object... keyAndValClasses);

  /**
   * Declares a PState store for direct access to Rama's built-in PState storage,
   *
   * @param name the name of the store (must start with "$$")
   * @param schema the schema class for the PState
   * @return the PState declaration for further configuration
   */
  PState.Declaration declarePStateStore(String name, Class schema);

  /**
   * Declares a PState store for direct access to Rama's built-in PState storage,
   * which are stores defined as any combination of data structures of any size.
   * PStates are durable, replicated, and scalable
   *
   * @param name the name of the store (must start with "$$")
   * @param schema the custom schema for the PState
   * @return the PState declaration for further configuration
   */
  PState.Declaration declarePStateStore(String name,  PState.Schema schema);

  /**
   * Declares a static agent object that is shared across all agent executions.
   *
   * Static objects are created once and reused for all agent executions.
   * They are suitable for static information like API keys.
   *
   * @param name the name of the agent object
   * @param o the object instance to share
   */
  void declareAgentObject(String name, Object o);

  /**
   * Declares an agent object builder that creates objects on demand.
   *
   * When a node gets an object, it gets exclusive access to it. A pool of up to
   * the configured object limit is created on demand. Exception is when the thread-safe option
   * is set, in which case one object is created and shared for all usage within
   * agents (no pool in this case).
   *
   * @param name the name of the agent object
   * @param builder function that creates the object from setup information
   */
  void declareAgentObjectBuilder(String name, RamaFunction1<AgentObjectSetup, Object> builder);

  /**
   * Declares an agent object builder with configuration options.
   *
   * @param name the name of the agent object
   * @param builder function that creates the object from setup information
   * @param options configuration options for the object builder
   */
  void declareAgentObjectBuilder(String name, RamaFunction1<AgentObjectSetup, Object> builder, AgentObjectOptions options);

  /**
   * Declares an evaluator builder for measuring agent performance.
   *
   * Evaluator builders return a map of scores, score name (string) to score value
   * (string, boolean, or number). The "fetcher" argument can be used to get agent objects.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   * @param <Input> the type of input data
   * @param <RefOutput> the type of reference output data
   * @param <Output> the type of actual output data
   */
  <Input, RefOutput, Output> void declareEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, Output, Map>> builder);

  /**
   * Declares an evaluator builder with configuration options.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   * @param options configuration options for the evaluator builder
   * @param <Input> the type of input data
   * @param <RefOutput> the type of reference output data
   * @param <Output> the type of actual output data
   */
  <Input, RefOutput, Output> void declareEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, Output, Map>> builder,
      EvaluatorBuilderOptions options);

  /**
   * Declares a comparative evaluator builder for comparing multiple outputs.
   *
   * If a comparative evaluator returns with an "index" key, that is treated specially
   * in the comparative experiment results UI to highlight that output as green as the better result.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   * @param <Input> the type of input data
   * @param <RefOutput> the type of reference output data
   * @param <Output> the type of actual output data
   */
  <Input, RefOutput, Output> void declareComparativeEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, List<Output>, Map>> builder);

  /**
   * Declares a comparative evaluator builder with configuration options.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   * @param options configuration options for the evaluator builder
   * @param <Input> the type of input data
   * @param <RefOutput> the type of reference output data
   * @param <Output> the type of actual output data
   */
  <Input, RefOutput, Output> void declareComparativeEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, List<Output>, Map>> builder,
      EvaluatorBuilderOptions options);

  /**
   * Declares a summary evaluator builder for aggregate metrics in experiments.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   */
  void declareSummaryEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction2<AgentObjectFetcher, List<ExampleRun>, Map>> builder);

  /**
   * Declares a summary evaluator builder with configuration options.
   *
   * @param name the name of the evaluator builder
   * @param description description of what the evaluator measures
   * @param builder function that creates the evaluator from parameters
   * @param options configuration options for the evaluator builder
   */
  void declareSummaryEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction2<AgentObjectFetcher, List<ExampleRun>, Map>> builder,
      EvaluatorBuilderOptions options);

  /**
   * Declares an action builder for real-time evaluation on production runs.
   *
   * Actions are user-defined hooks running on live agent executions for
   * real-time evaluation, data capture, etc. They can be parameterized and
   * have concurrency limits controlled by the global config max.limited.actions.concurrency.
   *
   * @param name the name of the action builder
   * @param description description of what the action does
   * @param builder function that creates the action from parameters
   * @param <Input> the type of input data
   * @param <Output> the type of output data
   */
   <Input, Output> void declareActionBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, List<Input>, Output, RunInfo, Map>> builder);

  /**
   * Declares an action builder with configuration options.
   *
   * @param name the name of the action builder
   * @param description description of what the action does
   * @param builder function that creates the action from parameters
   * @param options configuration options for the action builder
   * @param <Input> the type of input data
   * @param <Output> the type of output data
   */
   <Input, Output> void declareActionBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, List<Input>, Output, RunInfo, Map>> builder,
      ActionBuilderOptions options);

  /**
   * Declares a cluster agent that references an agent in another module. This enables agents to invoke agents in other modules.
   *
   * @param localName the local name for the agent
   * @param moduleName the name of the module containing the agent
   * @param agentName the name of the agent in the remote module
   */
  void declareClusterAgent(String localName, String moduleName, String agentName);

  /**
   * Gets the underlying Rama stream topology.
   *
   * @return the stream topology instance
   */
  StreamTopology getStreamTopology();

  /**
   * Defines the topology for deployment to a Rama cluster.
   *
   * This method must be called after all agents and infrastructure have been
   * declared and is only used when adding an AgentTopology to a regular Rama module.
   */
  void define();
}
