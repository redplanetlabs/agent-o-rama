package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;

public interface AgentStreamByInvoke extends Closeable {
  <T> Map<Long, List<T>> get();
  Map<Long, Long> numResetsByInvoke();
}
