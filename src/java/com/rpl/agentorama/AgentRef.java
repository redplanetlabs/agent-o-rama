package com.rpl.agentorama;

/**
 * Reference to an agent in a specific module.
 */
public interface AgentRef {
  /**
   * Gets the name of the module containing the agent.
   * 
   * @return the module name
   */
  public String getModuleName();
  
  /**
   * Gets the name of the agent within the module.
   * 
   * @return the agent name
   */
  public String getAgentName();
}
