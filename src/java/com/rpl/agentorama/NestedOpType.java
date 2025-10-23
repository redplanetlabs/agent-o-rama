package com.rpl.agentorama;

/**
 * Types of nested operations that can be tracked within agent executions.
 * 
 * Nested operations are internal operations like model calls, database access,
 * and tool calls that are tracked for tracing and analytics purposes.
 */
public enum NestedOpType {
  /**
   * Store read operation.
   */
  STORE_READ,
  
  /**
   * Store write operation.
   */
  STORE_WRITE,
  
  /**
   * Database read operation.
   */
  DB_READ,
  
  /**
   * Database write operation.
   */
  DB_WRITE,
  
  /**
   * Model call operation (e.g., LLM API call).
   */
  MODEL_CALL,
  
  /**
   * Tool call operation.
   */
  TOOL_CALL,
  
  /**
   * Agent call operation (subagent execution).
   */
  AGENT_CALL,
  
  /**
   * Human input request operation.
   */
  HUMAN_INPUT,
  
  /**
   * Other operation type.
   */
  OTHER
}
