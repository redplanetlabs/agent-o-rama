package com.rpl.agent.basic;

import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.AgentsTopology;

/**
 * Basic Agent Module demonstrating fundamental agent-o-rama concepts.
 *
 * <p>This module implements a simple agent with a single node that processes input and returns a
 * result. It demonstrates:
 *
 * <ul>
 *   <li>Agent module definition extending AgentsModule
 *   <li>Single-node agent topology
 *   <li>Basic node function implementation
 *   <li>Result emission from agent nodes
 * </ul>
 *
 * <p>The agent creates a welcome message for any input string and returns it as the final result.
 */
public class BasicAgentModule extends AgentsModule {

  @Override
  protected void defineAgents(AgentsTopology topology) {
    // Create agent with single node that processes input and returns result
    topology.newAgent("BasicAgent").node("process", null, new ProcessNodeFunction());
  }
}
