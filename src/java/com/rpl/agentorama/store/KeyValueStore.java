package com.rpl.agentorama.store;

import com.rpl.rama.ops.RamaFunction1;

/**
 * Simple typed persistent storage for key-value pairs. Stores are distributed, durable, and replicated.
 *
 * Key-value stores are created using {@link com.rpl.agentorama.AgentTopology#declareKeyValueStore(String, Class, Class)}.
 * 
 * @param <K> the type of keys
 * @param <V> the type of values
 */
public interface KeyValueStore<K, V> extends PStateStore {
  /**
   * Gets the value associated with the given key.
   * 
   * @param key the key to look up
   * @return the value associated with the key, or null if not found
   */
  V get(K key);
  
  /**
   * Gets the value associated with the given key, or returns a default value if not found.
   * 
   * @param key the key to look up
   * @param defaultValue the default value to return if key is not found
   * @return the value associated with the key, or the default value if not found
   */
  V getOrDefault(K key, V defaultValue);
  
  /**
   * Associates the specified value with the specified key.
   * 
   * @param key the key
   * @param value the value to associate with the key
   */
  void put(K key, V value);
  
  /**
   * Updates the value associated with the given key using the provided function.
   * 
   * @param key the key to update
   * @param updateFunction function to apply to the current value
   */
  <T extends V, R> void update(K key, RamaFunction1<T, R> updateFunction);
  
  /**
   * Checks if the store contains the specified key.
   * 
   * @param key the key to check
   * @return true if the store contains the key, false otherwise
   */
  boolean containsKey(K key);
}
