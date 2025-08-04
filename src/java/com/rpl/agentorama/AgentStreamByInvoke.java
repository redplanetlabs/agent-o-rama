package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;

public interface AgentStreamByInvoke extends Closeable {
  <T> Map<UUID, List<T>> get();
  Map<UUID, Long> numResetsByInvoke();
}
