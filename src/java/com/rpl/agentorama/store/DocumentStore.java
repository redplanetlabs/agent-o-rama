package com.rpl.agentorama.store;

import java.util.Map;

import com.rpl.rama.ops.RamaFunction1;

public interface DocumentStore<K> extends KeyValueStore<K, Map> {
  Object getDocumentField(K key, Object docKey);
  Object getDocumentFieldOrDefault(K key, Object docKey, Object defaultValue);
  boolean containsDocumentField(K key, Object docKey);
  void putDocumentField(K key, Object docKey, Object value);
  <T, R> void updateDocumentField(K key, Object docKey, RamaFunction1<T, R> updateFunction);
}
