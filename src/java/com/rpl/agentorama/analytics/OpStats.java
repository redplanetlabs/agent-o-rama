package com.rpl.agentorama.analytics;

/**
 * Statistics for a specific type of operation.
 * 
 * OpStats provides basic metrics for tracking the frequency and duration
 * of operations, such as nested operations or node executions within
 * an agent invocation.
 */
public interface OpStats {
  /**
   * Gets the number of times this operation was executed.
   * 
   * @return operation count
   */
  int getCount();
  
  /**
   * Gets the total time spent on this operation across all executions.
   * 
   * @return total time in milliseconds
   */
  long getTotalTimeMillis();
}
