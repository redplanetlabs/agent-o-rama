package com.rpl.agentorama.source;

import com.rpl.agentorama.AgentInvoke;

public interface AgentRunSource extends InfoSource {
  String getModuleName();
  String getAgentName();
  AgentInvoke getAgentInvoke();
}
