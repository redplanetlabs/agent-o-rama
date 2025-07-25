package com.rpl.agentorama;

public interface AgentObjectSetup {
  <T> T getAgentObject(String name);
  String getObjectName();
}
