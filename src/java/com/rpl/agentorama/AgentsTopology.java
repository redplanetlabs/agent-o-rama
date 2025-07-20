package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule.*;
import com.rpl.rama.module.*;

public interface AgentsTopology {

  public static AgentsTopology create(Setup setup, Topologies topologies) {
    return (AgentsTopology) AORHelpers.CREATE_AGENTS_TOPOLOGY.invoke(setup, topologies);
  }

  AgentGraph newAgent(String name);

  void declareKeyValueStore(String name, Class keyClass, Class valClass);
  void declareDocumentStore(String name, Class keyClass, Object... keyAndValClasses);
  PState.Declaration declarePStateStore(String name, Class schema);
  PState.Declaration declarePStateStore(String name,  PState.Schema schema);

  // TODO: document how to make LLMs
  void declareAgentObject(String name, Object o);

  StreamTopology getStreamTopology();
  
  void define();
}
