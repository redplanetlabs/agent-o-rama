package com.rpl.agentorama;

import java.util.List;

public interface AgentStream extends Closeable {
  <T> List<T> get();
}
