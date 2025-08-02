package com.rpl.agentorama;

public interface HumanInputRequest extends AgentStep {
  String getNode();
  long getNodeInvokeId();
  String getPrompt();
}
