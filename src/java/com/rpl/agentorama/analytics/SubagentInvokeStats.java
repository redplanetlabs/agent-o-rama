package com.rpl.agentorama.analytics;

/**
 * Statistics for subagent invocations within an agent execution.
 * 
 * SubagentInvokeStats provides metrics for tracking how many times a specific
 * subagent was invoked and the aggregated statistics across all those invocations.
 */
public interface SubagentInvokeStats {
  /**
   * Gets the number of times this subagent was invoked.
   * 
   * @return invocation count
   */
  int getCount();
  
  /**
   * Gets the aggregated basic statistics across all invocations of this subagent.
   * 
   * @return aggregated basic agent invocation statistics
   */
  BasicAgentInvokeStats getBasicStats();
}
