package com.rpl.agentorama.store;

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

}
