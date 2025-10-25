package com.rpl.agentorama.analytics;

import com.rpl.agentorama.AgentRef;
import java.util.Map;

/**
 * Statistics for an agent invocation including subagent calls and basic metrics.
 * 
 * AgentInvokeStats provides comprehensive analytics for a single agent execution,
 * including performance metrics for the main agent and all subagent invocations
 * that occurred during execution.
 */
public interface AgentInvokeStats {
  /**
   * Gets statistics for all subagent invocations that occurred during this execution.
   * 
   * @return map from agent reference to subagent invocation statistics
   */
  Map<AgentRef, SubagentInvokeStats> getSubagentStats();
  
  /**
   * Gets basic statistics for the main agent execution.
   * 
   * @return basic agent invocation statistics
   */
  BasicAgentInvokeStats getBasicStats();
}
