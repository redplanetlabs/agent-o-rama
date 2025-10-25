package com.rpl.agentorama.store;

import java.util.Map;

import com.rpl.rama.ops.RamaFunction1;

/**
 * DocumentStore is like a key-value store where each value is a document (map) that can contain
 * nested fields. Stores are distributed, durable, and replicated.
 * 
 * Document stores are created using {@link com.rpl.agentorama.AgentTopology#declareDocumentStore(String, Class, Object...)}.
 * 
 * @param <K> the type of keys
 */
public interface DocumentStore<K> extends KeyValueStore<K, Map> {
  /**
   * Gets a field value from a document.
   * 
   * @param key the document key
   * @param docKey the field key within the document
   * @return the field value, or null if not found
   */
  Object getDocumentField(K key, Object docKey);
  
  /**
   * Gets a field value from a document, or returns a default value if not found.
   * 
   * @param key the document key
   * @param docKey the field key within the document
   * @param defaultValue the default value to return if field is not found
   * @return the field value, or the default value if not found
   */
  Object getDocumentFieldOrDefault(K key, Object docKey, Object defaultValue);
  
  /**
   * Checks if a document contains a specific field.
   * 
   * @param key the document key
   * @param docKey the field key to check
   * @return true if the document contains the field, false otherwise
   */
  boolean containsDocumentField(K key, Object docKey);
  
  /**
   * Sets a field value in a document.
   * 
   * @param key the document key
   * @param docKey the field key within the document
   * @param value the value to set
   */
  void putDocumentField(K key, Object docKey, Object value);
  
  /**
   * Updates a field value in a document using the provided function.
   * 
   * @param key the document key
   * @param docKey the field key within the document
   * @param updateFunction function to apply to the current field value
   */
  <T, R> void updateDocumentField(K key, Object docKey, RamaFunction1<T, R> updateFunction);
}
