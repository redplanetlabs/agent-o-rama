package com.rpl.agentorama;

/**
 * Interface for accessing agent objects by name.
 *
 * Agent objects are shared resources (LLMs, APIs, databases, etc.) that are
 * accessible by agent nodes during execution. They can be static objects or
 * built on-demand with pooling and thread-safety considerations.
 *
 * Agent objects are declared in the agent topology using:
 * <ul>
 * <li>{@link AgentTopology#declareAgentObject(String, Object)} for static objects</li>
 * <li>{@link AgentTopology#declareAgentObjectBuilder(String, com.rpl.rama.ops.RamaFunction1)} for on-demand objects</li>
 * </ul>
 *
 * Implemented by:
 * - {@link AgentNode} - provides access to agent objects within node functions
 * - {@link AgentObjectSetup} - provides access to agent objects during object builder setup
 */
public interface AgentObjectFetcher {
  /**
   * Gets an agent object by name.
   *
   * When a node gets an object, it gets exclusive access to it. A pool of up to
   * of size configured by the agent object builder is created on demand. Exception is when builder
   * is configured to be thread-safe, in which case one object is created and shared for all usage within
   * agents (no pool in this case).
   *
   * @param name the name of the agent object
   * @param <T> the type of the agent object
   * @return the agent object instance
   */
  <T> T getAgentObject(String name);
}
