package com.rpl.agentorama.analytics;

import com.rpl.agentorama.NestedOpType;
import java.util.Map;

public interface BasicAgentInvokeStats {
  Map<NestedOpType, OpStats> getNestedOpStats();
  int getInputTokenCount();
  int getOutputTokenCount();
  int getTotalTokenCount();
  Map<String, OpStats> getNodeStats();
}
