package com.rpl.agentorama;

import java.util.UUID;

/**
 * Handle representing a specific execution instance of an agent.
 * 
 * Agent invoke handles are used to track and interact with running agent
 * executions. They provide access to execution metadata and are used with
 * streaming, forking, and result retrieval methods.
 * 
 * Example:
 * <pre>{@code
 * AgentInvoke invoke = client.initiate("Hello world");
 * // Use invoke with streaming, forking, or result methods
 * String result = client.result(invoke);
 * }</pre>
 */
public interface AgentInvoke {
  /**
   * Gets the task ID for this agent execution.
   * 
   * @return the task ID
   */
  long getTaskId();
  
  /**
   * Gets the unique agent invoke ID for this execution.
   * 
   * @return the agent invoke ID
   */
  UUID getAgentInvokeId();
}
