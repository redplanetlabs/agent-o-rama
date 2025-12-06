package com.rpl.agentorama.impl;

import java.io.Closeable;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class CloseableConcurrentMap extends ConcurrentHashMap implements Closeable {
  @Override
  public void close() throws IOException {
    for(Object v: this.values()) {
      if(v instanceof Closeable) ((Closeable) v).close();
    }
  }
}
