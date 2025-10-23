package com.rpl.agentorama.analytics;

import com.rpl.agentorama.NestedOpType;
import java.util.Map;

/**
 * Basic statistics for an agent invocation including token counts and operation metrics.
 * 
 * BasicAgentInvokeStats provides fundamental performance and usage metrics for a single
 * agent execution, including token consumption, nested operation statistics, and
 * per-node performance data.
 */
public interface BasicAgentInvokeStats {
  /**
   * Gets statistics for nested operations by type.
   * 
   * @return map from nested operation type to operation statistics
   */
  Map<NestedOpType, OpStats> getNestedOpStats();
  
  /**
   * Gets the total number of input tokens consumed during this execution.
   * 
   * @return input token count
   */
  int getInputTokenCount();
  
  /**
   * Gets the total number of output tokens generated during this execution.
   * 
   * @return output token count
   */
  int getOutputTokenCount();
  
  /**
   * Gets the total number of tokens for this execution.
   * 
   * @return total token count
   */
  int getTotalTokenCount();
  
  /**
   * Gets statistics for each node execution within this agent.
   * 
   * @return map from node name to node execution statistics
   */
  Map<String, OpStats> getNodeStats();
}
