package com.rpl.agentorama;

import java.io.Closeable;
import java.util.List;

/**
 * Stream for accessing data emitted from a single agent node invoke.
 * 
 * The returned object can be closed to immediately stop streaming. The stream automatically closes when the node invoke completes.
 * 
 * Example:
 * <pre>{@code
 * AgentStream stream = client.stream(invoke, "myNode");
 * 
 * // Get current chunks
 * List<String> chunks = stream.get();
 * 
 * // Check for resets
 * int resets = stream.numResets();
 * }</pre>
 */
public interface AgentStream extends Closeable {
  /**
   * Gets the current streamed chunks.
   * 
   * @param <T> the type of chunks being streamed
   * @return list of current chunks
   */
  <T> List<T> get();
  
  /**
   * Gets the number of times the stream has been reset.
   * 
   * Resets occur when nodes fail and retry, causing the stream to start over.
   * 
   * @return number of resets
   */
  int numResets();
}
