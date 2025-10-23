package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

/**
 * Client for interacting with a specific agent.
 *
 * Agent clients provide the interface for invoking agents, streaming data,
 * handling human input, and managing agent executions.
 *
 * When called from within an agent node function, this enables subagent execution:
 * <ul>
 * <li>Can invoke any other agent in the same module (including the current agent)</li>
 * <li>Enables recursive agent execution patterns</li>
 * <li>Enables mutually recursive agent execution between different agents</li>
 * <li>Subagent calls are tracked and displayed in the UI trace</li>
 * </ul>
 *
 * Example:
 * <pre>{@code
 * // From client code
 * AgentClient client = manager.getAgentClient("my-agent");
 * String result = client.invoke("Hello world");
 *
 * // From within an agent node (subagent execution)
 * AgentClient subClient = agentNode.getAgentClient("other-agent");
 * String subResult = subClient.invoke("Process this data");
 * }</pre>
 */
public interface AgentClient extends Closeable {
  /**
   * Callback interface for streaming data from a single node invoke.
   *
   * @param <T> the type of chunks being streamed
   */
  interface StreamCallback<T> {
    /**
     * Called when new data chunks are available.
     *
     * @param allChunks all chunks received so far
     * @param newChunks new chunks in this update
     * @param isReset true if the stream was reset because the node failed and retried
     * @param isComplete true if streaming is finished
     */
    void onUpdate(List<T> allChunks, List<T> newChunks, boolean isReset, boolean isComplete);
  }

  /**
   * Callback interface for streaming data from all invocations of a specific node.
   *
   * @param <T> the type of data being streamed
   */
  interface StreamAllCallback<T> {
    /**
     * Called when new data chunks are available from any node invocation.
     *
     * @param allChunks all chunks received so far, grouped by invoke ID
     * @param newChunks new chunks in this update, grouped by invoke ID
     * @param resetInvokeIds set of invoke IDs that were reset because nodes failed and retried
     * @param isComplete true if streaming is finished across all node invocations for the agent invoke
     */
    void onUpdate(Map<UUID, List<T>> allChunks, Map<UUID, List<T>> newChunks, Set<UUID> resetInvokeIds, boolean isComplete);
  }

  /**
   * Synchronously invokes an agent with the provided arguments.
   *
   * This method blocks until the agent execution completes and returns
   * the final result. For long-running agents, consider using initiate()
   * with result() for better control.
   *
   * @param args arguments to pass to the agent
   * @return the final result from the agent execution
   */
  <T> T invoke(Object... args);

  /**
   * Asynchronously invokes an agent with the provided arguments.
   *
   * Returns a CompletableFuture that will complete with the agent's result.
   * This allows for non-blocking agent execution and better resource utilization.
   *
   * @param args arguments to pass to the agent
   * @return future that completes with the agent result
   */
  <T> CompletableFuture<T> invokeAsync(Object... args);

  /**
   * Synchronously invokes an agent with context metadata.
   *
   * Metadata allows attaching custom key-value data to agent executions.
   * Metadata is an additional optional parameter to agent execution, and
   * is also used for analytics. Metadata can be accessed anywhere inside
   * agents by calling getMetadata() within node functions.
   *
   * @param context context containing metadata for the execution
   * @param args arguments to pass to the agent
   * @return the final result from the agent execution
   */
  <T> T invokeWithContext(AgentContext context, Object... args);

  /**
   * Asynchronously invokes an agent with context metadata.
   *
   * @param context context containing metadata for the execution
   * @param args arguments to pass to the agent
   * @return future that completes with the agent result
   */
  <T> CompletableFuture<T> invokeWithContextAsync(AgentContext context, Object... args);

  /**
   * Initiates an agent execution and returns a handle for tracking.
   *
   * This method starts an agent execution but doesn't wait for completion.
   * Use the returned result handle with result(), nextStep(), or
   * streaming methods to interact with the running agent.
   *
   * @param args arguments to pass to the agent
   * @return agent invoke handle for tracking and interacting with the execution
   */
  AgentInvoke initiate(Object... args);

  /**
   * Asynchronously initiates an agent execution and returns a CompletableFuture with a handle for tracking.
   *
   * @param args arguments to pass to the agent
   * @return future that completes with the agent invoke handle
   */
  CompletableFuture<AgentInvoke> initiateAsync(Object... args);

  /**
   * Initiates an agent execution with context metadata.
   *
   * @param context context containing metadata for the execution
   * @param args arguments to pass to the agent
   * @return agent invoke handle for tracking and interacting with the execution
   */
  AgentInvoke initiateWithContext(AgentContext context, Object... args);

  /**
   * Asynchronously initiates an agent execution with context metadata.
   *
   * @param context context containing metadata for the execution
   * @param args arguments to pass to the agent
   * @return future that completes with the agent invoke handle
   */
  CompletableFuture<AgentInvoke> initiateWithContextAsync(AgentContext context, Object... args);

  /**
   * Synchronously forks an agent execution with new arguments for specific nodes.
   *
   * Forking creates new execution branches from an existing agent invocation.
   * The nodeInvokeIdToNewArgs map specifies which nodes to fork and what new
   * arguments to use for each fork. Node invoke IDs can be found in the trace UI.
   *
   * @param invoke the agent invoke handle to fork from
   * @param nodeInvokeIdToNewArgs map from node invoke ID to new arguments for that node
   * @return the result from the forked execution
   */
  <T> T fork(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);

  /**
   * Asynchronously forks an agent execution with new arguments for specific nodes.
   *
   * @param invoke the agent invoke handle to fork from
   * @param nodeInvokeIdToNewArgs map from node invoke ID to new arguments for that node
   * @return future that completes with the result from the forked execution
   */
  <T> CompletableFuture<T> forkAsync(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);

  /**
   * Initiates a fork of an agent execution and returns a handle for tracking.
   *
   * @param invoke the agent invoke to fork from
   * @param nodeInvokeIdToNewArgs map from node invoke ID to new arguments for that node
   * @return agent invoke handle for the forked execution
   */
  AgentInvoke initiateFork(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);

  /**
   * Asynchronously initiates a fork of an agent execution.
   *
   * @param invoke the agent invoke handle to fork from
   * @param nodeInvokeIdToNewArgs map from node invoke ID to new arguments for that node
   * @return future that completes with the agent invoke handle for the forked execution
   */
  CompletableFuture<AgentInvoke> initiateForkAsync(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);

  /**
   * Gets the next execution step of an agent.
   *
   * The next execution step is either a human input request or an agent result.
   * Check which one by checking if the returned object is an instance of {@link HumanInputRequest} or {@link AgentComplete}.
   * If the agent fails, it will throw an exception.
   *
   * @param invoke the agent invoke handle to get the next step for
   * @return the next execution step
   */
  AgentStep nextStep(AgentInvoke invoke);

  /**
   * Asynchronously gets the next execution step of an agent.
   *
   * @param invoke the agent invoke handle to get the next step for
   * @return future that completes with the next execution step
   */
  CompletableFuture<AgentStep> nextStepAsync(AgentInvoke invoke);

  /**
   * Gets the final result of an agent execution.
   *
   * This method blocks until the agent execution completes and returns
   * the final result. If the agent fails, it will throw an exception.
   *
   * @param invoke the agent invoke handle to get the result for
   * @return the final result from the agent execution
   */
  <T> T result(AgentInvoke invoke);

  /**
   * Asynchronously gets the final result of an agent execution.
   *
   * @param invoke the agent invoke handle to get the result for
   * @return future that completes with the final result from the agent execution
   */
  <T> CompletableFuture<T> resultAsync(AgentInvoke invoke);

  /**
   * Checks if an agent execution has completed.
   *
   * @param invoke the agent invoke handle to check
   * @return true if the agent execution is complete
   */
  boolean isAgentInvokeComplete(AgentInvoke invoke);

  /**
   * Sets metadata for an agent execution.
   *
   * Note: For agent execution, only the metadata that was set at the start
   * with the *WithContext functions is used.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, int value);

  /**
   * Sets metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, long value);

  /**
   * Sets metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, float value);

  /**
   * Sets metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, double value);

  /**
   * Sets metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, String value);

  /**
   * Sets metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to set metadata for
   * @param key the metadata key
   * @param value the metadata value
   */
  void setMetadata(AgentInvoke invoke, String key, boolean value);

  /**
   * Removes metadata from an agent execution.
   *
   * @param invoke the agent invoke handle to remove metadata from
   * @param key the metadata key to remove
   */
  void removeMetadata(AgentInvoke invoke, String key);

  /**
   * Gets all metadata for an agent execution.
   *
   * @param invoke the agent invoke handle to get metadata for
   * @return map of metadata key-value pairs
   */
  Map<String, Object> getMetadata(AgentInvoke invoke);

  /**
   * Creates a stream for data emitted from a specific node.
   *
   * The returned object can have close() called on it to immediately stop streaming.
   *
   * @param invoke the agent invoke handle to stream from
   * @param node the node name to stream data from
   * @return stream object for accessing chunks and controlling streaming
   */
  AgentStream stream(AgentInvoke invoke, String node);

  /**
   * Creates a stream for data emitted from a specific node with a callback.
   *
   * @param invoke the agent invoke handle to stream from
   * @param node the node name to stream data from
   * @param callback callback function for handling stream updates
   * @return stream object for accessing chunks and controlling streaming
   */
  <T> AgentStream stream(AgentInvoke invoke, String node, StreamCallback<T> callback);

  /**
   * Creates a stream for data emitted from a specific node invocation.
   *
   * @param invoke the agent invoke to stream from
   * @param node the node name to stream data from
   * @param nodeInvokeId the specific node invocation ID to stream from, which can be found in the trace UI.
   * @return stream object for accessing chunks and controlling streaming
   */
  AgentStream streamSpecific(AgentInvoke invoke, String node, UUID nodeInvokeId);

  /**
   * Creates a stream for data emitted from a specific node invocation with a callback.
   *
   * @param invoke the agent invoke to stream from
   * @param node the node name to stream data from
   * @param nodeInvokeId the specific node invocation ID to stream from, which can be found in the trace UI.
   * @param callback callback function for handling stream updates
   * @return stream object for accessing chunks and controlling streaming
   */
  <T> AgentStream streamSpecific(AgentInvoke invoke, String node, UUID nodeInvokeId, StreamCallback<T> callback);

  /**
   * Creates a stream for data emitted from all invocations of a specific node.
   *
   * The returned object can have close() called on it to immediately stop streaming.
   *
   * @param invoke the agent invoke handle to stream from
   * @param node the node name to stream data from
   * @return stream object for accessing chunks grouped by invoke ID
   */
  AgentStreamByInvoke streamAll(AgentInvoke invoke, String node);

  /**
   * Creates a stream for data emitted from all invocations of a specific node with a callback.
   *
   * @param invoke the agent invoke to stream from
   * @param node the node name to stream data from
   * @param callback callback function for handling stream updates
   * @return stream object for accessing chunks grouped by invoke ID
   */
  <T> AgentStreamByInvoke streamAll(AgentInvoke invoke,
                                    String node,
                                    StreamAllCallback<T> callback);

  /**
   * Gets all pending human input requests for an agent execution.
   *
   * @param invoke the agent invoke to get pending inputs for
   * @return list of pending human input requests
   */
  List<HumanInputRequest> pendingHumanInputs(AgentInvoke invoke);

  /**
   * Asynchronously gets all pending human input requests for an agent execution.
   *
   * @param invoke the agent invoke to get pending inputs for
   * @return future that completes with the list of pending human input requests
   */
  CompletableFuture<List<HumanInputRequest>> pendingHumanInputsAsync(AgentInvoke invoke);

  /**
   * Provides a response to a human input request.
   *
   * @param request the human input request to respond to
   * @param response the response text
   */
  void provideHumanInput(HumanInputRequest request, String response);

  /**
   * Asynchronously provides a response to a human input request.
   *
   * @param request the human input request to respond to
   * @param response the response text
   * @return future that completes when the response is provided
   */
  CompletableFuture<Void> provideHumanInputAsync(HumanInputRequest request, String response);
}
