package com.rpl.agentorama.store;

import java.util.Map;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.ops.RamaFunction1;

public interface DocumentStore<K> extends KeyValueStore<K, Map> {
  AsyncResult getDocumentFieldAsync(K key, Object docKey);
  AsyncResult getDocumentFieldOrDefaultAsync(K key, Object docKey, Object defaultValue);
  AsyncResult containsDocumentFieldAsync(K key, Object docKey);
  void putDocumentFieldAsync(K key, Object docKey, Object value);
  <T, R> void updateDocumentFieldAsync(K key, Object docKey, RamaFunction1<T, R> updateFunction);
}
