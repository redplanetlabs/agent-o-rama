package com.rpl.agentorama;

public interface AgentObjectFetcher {
  <T> T getAgentObject(String name);
}
