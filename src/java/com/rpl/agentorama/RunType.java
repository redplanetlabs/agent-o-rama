package com.rpl.agentorama;

/**
 * Types of runs that can be tracked in the system.
 * 
 * RunType distinguishes between agent-level runs and node-level runs
 * for tracking and analytics purposes.
 */
public enum RunType {
  /**
   * Agent-level run.
   * 
   * Represents a complete agent execution from start to finish.
   */
  AGENT,
  
  /**
   * Node-level run.
   * 
   * Represents the execution of a single node within an agent.
   */
  NODE
}
