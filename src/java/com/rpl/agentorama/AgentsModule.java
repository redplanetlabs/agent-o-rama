package com.rpl.agentorama;

import com.rpl.rama.*;

public abstract class AgentsModule implements RamaModule {
  abstract void defineAgents(AgentsTopology topology);

  @Override
  public void define(Setup setup, Topologies topologies) {
    AgentsTopology at = AgentsTopology.create(setup, topologies);
    defineAgents(at);
    at.define();
  }
}
