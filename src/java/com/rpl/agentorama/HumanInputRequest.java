package com.rpl.agentorama;

import java.util.UUID;

public interface HumanInputRequest extends AgentStep {
  String getNode();
  UUID getNodeInvokeId();
  String getPrompt();
}
