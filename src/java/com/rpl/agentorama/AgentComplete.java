package com.rpl.agentorama;

public interface AgentComplete<T> extends AgentStep {
  T getResult();
}
