package com.rpl.agentorama;

public interface AgentNode {
  void emit(String node, Object... args);
  void emitParallel(String node, Object... args);
  <T> T getObject(String name);
}
