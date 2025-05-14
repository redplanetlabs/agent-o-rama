package com.rpl.agentorama.store;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.ops.RamaFunction1;

public interface KeyValueStore<K, V> extends PStateStore {
  AsyncResult getAsync(K key);
  AsyncResult getOrDefaultAsync(K key, Object defaultValue);
  void putAsync(K key, V value);
  <T extends V, R> void updateAsync(K key, RamaFunction1<T, R> updateFunction);
  AsyncResult containsAsync(K key);
}
