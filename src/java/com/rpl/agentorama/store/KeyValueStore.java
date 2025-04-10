package com.rpl.agentorama.store;

import com.rpl.agentorama.AsyncResult;

public interface KeyValueStore {
  AsyncResult get(Object key);
  void putAsync(Object key, Object value);
}
