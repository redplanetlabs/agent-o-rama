package com.rpl.agentorama.store;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.ops.RamaFunction1;

public interface KeyValueStore<K, V> {
  AsyncResult getAsync(K key);
  void putAsync(K key, V value);
  <T, R> void updateAsync(K key, RamaFunction1<T, R> updateFunction);
}
