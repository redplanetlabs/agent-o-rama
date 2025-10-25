package com.rpl.agentorama;

import java.util.UUID;

/**
 * Handle representing a specific execution instance of a node within an agent.
 * 
 * Node invoke handles are used to track and identify individual node executions
 * within an agent execution. They provide access to execution metadata and are
 * used for streaming, forking, and other node-specific operations.
 */
public interface NodeInvoke {
  /**
   * Gets the task ID for this node execution.
   * 
   * @return the task ID
   */
  long getTaskId();
  
  /**
   * Gets the unique node invoke ID for this execution.
   * 
   * @return the node invoke ID
   */
  UUID getNodeInvokeId();
}
