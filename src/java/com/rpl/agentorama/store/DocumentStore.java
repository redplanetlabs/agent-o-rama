package com.rpl.agentorama.store;

import java.util.Map;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.ops.RamaFunction1;

public interface DocumentStore<K> {
  AsyncResult getDocumentAsync(K key);
  AsyncResult getDocumentFieldAsync(K key, Object docKey);
  void putDocumentAsync(K key, Map document);
  void putDocumentFieldAsync(K key, Object docKey, Object value);
  void updateDocumentAsync(K key, RamaFunction1<Map, Map> updateFunction);
  <T, R> void updateDocumentFieldAsync(K key, Object docKey, RamaFunction1<T, R> updateFunction);

}
