package com.rpl.agentorama;

/**
 * Setup context for agent object builders.
 * 
 * AgentObjectSetup provides access to other agent objects and the current object's
 * name during the object building process. This is passed to agent object builder
 * functions to enable objects to depend on other objects or access their own name.
 * 
 * This interface is used when declaring agent object builders with:
 * {@link AgentTopology#declareAgentObjectBuilder(String, com.rpl.rama.ops.RamaFunction1)}
 * 
 * Example:
 * <pre>{@code
 * topology.declareAgentObjectBuilder("myService", (AgentObjectSetup setup) -> {
 *   // Get the name of the object being built
 *   String objectName = setup.getObjectName();
 *   
 *   // Access other agent objects if needed
 *   DatabaseConnection db = setup.getAgentObject("database");
 *   Logger logger = setup.getAgentObject("logger");
 *   
 *   // Build the service with dependencies
 *   return new MyService(objectName, db, logger);
 * });
 * }</pre>
 */
public interface AgentObjectSetup extends AgentObjectFetcher {
  /**
   * Gets the name of the agent object being built.
   * 
   * This is the name that was specified when declaring the agent object builder
   * in the topology.
   * 
   * @return the name of the agent object being built
   */
  String getObjectName();
}
