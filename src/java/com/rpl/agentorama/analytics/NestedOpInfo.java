package com.rpl.agentorama.analytics;

import java.util.Map;

import com.rpl.agentorama.NestedOpType;

/**
 * Information about a nested operation within an agent execution.
 * 
 * NestedOpInfo represents a single nested operation (such as a model call,
 * database access, or tool call) that occurred during agent execution.
 * It includes timing information, operation type, and operation-specific metadata.
 */
public interface NestedOpInfo {
  /**
   * Gets the start time of this operation.
   * 
   * @return start timestamp in milliseconds since epoch
   */
  long getStartTimeMillis();
  
  /**
   * Gets the finish time of this operation.
   * 
   * @return finish timestamp in milliseconds since epoch
   */
  long getFinishTimeMillis();
  
  /**
   * Gets the type of this nested operation.
   * 
   * @return the nested operation type
   */
  NestedOpType getType();
  
  /**
   * Gets operation-specific metadata and information.
   * 
   * @return map from string key to operation-specific value
   */
  Map<String, Object> getInfo();
}
