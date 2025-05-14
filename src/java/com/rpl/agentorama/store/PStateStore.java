package com.rpl.agentorama.store;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.Path;

public interface PStateStore {
  AsyncResult select(Path path);
  void transform(Path path);
}
