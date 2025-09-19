package com.rpl.agentorama;

import com.rpl.rama.*;

public abstract class AgentsModule implements RamaModule {
  protected abstract void defineAgents(AgentTopology topology);

  @Override
  public void define(Setup setup, Topologies topologies) {
    AgentTopology at = AgentTopology.create(setup, topologies);
    defineAgents(at);
    at.define();
  }
}
