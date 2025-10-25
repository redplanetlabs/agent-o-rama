package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;

/**
 * Stream for accessing data emitted from all invocations of a specific node.
 * 
 * The returned object can be closed to immediately stop streaming.
 * 
 * Example:
 * <pre>{@code
 * AgentStreamByInvoke stream = client.streamAll(invoke, "myNode");
 * 
 * // Get current chunks grouped by invoke ID
 * Map<UUID, List<String>> chunksByInvoke = stream.get();
 * 
 * // Check resets per invoke
 * Map<UUID, Long> resetsByInvoke = stream.numResetsByInvoke();
 * 
 * stream.close();
 * }</pre>
 */
public interface AgentStreamByInvoke extends Closeable {
  /**
   * Gets the current streamed chunks grouped by node invoke ID.
   * 
   * @param <T> the type of chunks being streamed
   * @return map from node invoke ID to list of chunks
   */
  <T> Map<UUID, List<T>> get();
  
  /**
   * Gets the number of resets per node invoke ID.
   * 
   * Resets occur when nodes fail and retry, causing the stream to start over.
   * 
   * @return map from node invoke ID to number of resets
   */
  Map<UUID, Long> numResetsByInvoke();
}
