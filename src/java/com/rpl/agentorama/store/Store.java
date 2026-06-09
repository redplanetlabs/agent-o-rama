package com.rpl.agentorama.store;

import com.rpl.rama.PState;

/**
 * Base interface for built-in persistent stores accessible from agent nodes.
 *
 * Store names must start with "$$" and are declared in the agent topology.
 *
 * Stores are distributed, durable, and replicated.
 *
 * Available store types:
 * <ul>
 * <li>{@link KeyValueStore} - Simple typed key-value storage</li>
 * <li>{@link DocumentStore} - Schema-flexible storage for nested data</li>
 * <li>{@link PStateStore} - Direct access to Rama's built-in PState storage</li>
 * </ul>
 */
public interface Store {

  /**
   * Returns the underlying Rama PState backing this store, if any.
   *
   * <p>Key-value, document, and PState stores all return their backing PState. This enables
   * standard Rama foreign operations (e.g. {@code foreign-select-one-async}) on agent stores.
   *
   * @return the underlying PState client, or null if this store has no PState backing
   */
  PState getUnderlyingPState();
}
