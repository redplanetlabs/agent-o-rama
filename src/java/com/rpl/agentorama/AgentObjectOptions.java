package com.rpl.agentorama;

/**
 * Configuration options for agent object builders.
 * 
 * AgentObjectOptions provides configuration for how agent objects are created,
 * managed, and accessed within agent executions.
 * 
 * Example:
 * <pre>{@code
 * AgentObjectOptions options = AgentObjectOptions.create()
 *   .threadSafe()
 *   .workerObjectLimit(10)
 *   .disableAutoTracing();
 * 
 * topology.declareAgentObjectBuilder("myObject", builder, options);
 * }</pre>
 */
public interface AgentObjectOptions {
  /**
   * Creates a new instance of agent object options.
   * 
   * @return new options instance
   */
  public static Impl create() {
    return new Impl();
  }

  /**
   * Creates options with thread-safe configuration.
   * 
   * When this is set, one object is created and shared for all usage
   * within agents (no pool in this case).
   * 
   * @return options with thread safety enabled
   */
  public static Impl threadSafe() {
    return create().threadSafe();
  }

  /**
   * Creates options with auto-tracing disabled. When auto-tracing is enabled, chat models and
   * embedding stores from Langchain4j are automatically wrapped to record all calls as nested operations.
   * 
   * @return options with auto-tracing disabled
   */
  public static Impl disableAutoTracing() {
    return create().disableAutoTracing();
  }

  /**
   * Creates options with a specific worker object limit.
   * 
   * A pool of up to this many objects is created on demand at runtime in each Rama worker running the agent module.
   * 
   * @param amt the maximum number of objects to create
   * @return options with the specified worker object limit
   */
  public static Impl workerObjectLimit(int amt) {
    return create().workerObjectLimit(amt);
  }


  class Impl implements AgentObjectOptions {
    public Boolean threadSafe;
    public Boolean autoTracing;
    public Long workerObjectLimit;

    /**
     * Creates options with thread-safe configuration.
     * 
     * When this is set, one object is created and shared for all usage
     * within agents (no pool in this case).
     * 
     * @return options with thread safety enabled
     */
    public Impl threadSafe() {
      this.threadSafe = true;
      return this;
    }

    /**
     * Creates options with auto-tracing disabled. When auto-tracing is enabled, chat models and
     * embedding stores from Langchain4j are automatically wrapped to record all calls as nested operations.
     * 
     * @return options with auto-tracing disabled
     */
    public Impl disableAutoTracing() {
      this.autoTracing = false;
      return this;
    }

    /**
     * Creates options with a specific worker object limit.
     * 
     * A pool of up to this many objects is created on demand at runtime in each Rama worker running the agent module.
     * 
     * @param amt the maximum number of objects to create
     * @return options with the specified worker object limit
     */
    public Impl workerObjectLimit(int amt) {
      this.workerObjectLimit = (long) amt;
      return this;
    }
  }
}
