package com.rpl.agentorama;

import com.rpl.agentorama.store.Store;

public interface AgentNode {
  void emit(String node, Object... args);
  void result(Object arg);
  <T> T getAgentObject(String name);
  <T extends Store> T getStore(String name);
  void streamChunk(Object chunk);
}
