package com.rpl.agentorama.analytics;

import com.rpl.agentorama.AgentRef;
import java.util.Map;

public interface AgentInvokeStats {
  Map<AgentRef, SubagentInvokeStats> getSubagentStats();
  BasicAgentInvokeStats getBasicStats();
}
