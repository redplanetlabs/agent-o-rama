package com.rpl.agentorama;

public interface HumanInputRequest {
  String getNode();
  long getNodeInvokeId();
  String getPrompt();
}
