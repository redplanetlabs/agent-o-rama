package com.rpl.agentorama.store;

import com.rpl.agentorama.AsyncResult;
import com.rpl.rama.Path;

public interface PStateStore extends Store {
  AsyncResult select(Path path);
  void transform(Path path);
}
