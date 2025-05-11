package com.rpl.agentorama;

public interface AgentNode {
  void emit(String node, Object... args);
  void result(Object arg);

  // TODO: does this get a mirror agent as well?
  //  - probably not since that would be scoped with different task global name?
  <T> T getObject(String name);
}
