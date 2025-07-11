package com.rpl.agentorama;

import java.io.Closeable;
import java.util.List;

public interface AgentStream extends Closeable {
  <T> List<T> get();
  int numResets();
}
