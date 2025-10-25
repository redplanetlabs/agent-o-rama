package com.rpl.agentorama;

/**
 * Controls how in-flight agent executions should be handled after a module is updated.
 * 
 * When a module is updated while agent executions are running, this enum determines
 * what happens to those executions.
 */
public enum UpdateMode {
  /**
   * Continue in-flight executions where they left off.
   * 
   * Executions will continue from their current state after the module update.
   */
  CONTINUE,
  
  /**
   * Restart in-flight executions from the beginning.
   * 
   * Executions will be restarted from the start after the module update.
   */
  RESTART,
  
  /**
   * Drop in-flight executions.
   * 
   * Executions will be terminated and not restarted after the module update.
   */
  DROP
}
