package com.rpl.agentorama.store;

import com.rpl.rama.ops.RamaFunction1;

public interface KeyValueStore<K, V> extends PStateStore {
  V get(K key);
  V getOrDefault(K key, V defaultValue);
  void put(K key, V value);
  <T extends V, R> void update(K key, RamaFunction1<T, R> updateFunction);
  boolean containsKey(K key);
}
