package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule.*;
import com.rpl.rama.module.*;

import clojure.lang.IFn;

public interface AgentsTopology {

  public static AgentsTopology create(String name, Setup setup, Topologies topologies) {
    return (AgentsTopology) AORHelpers.CREATE_AGENTS_TOPOLOGY.invoke(name, setup, topologies);
  }

  AgentBuilder newAgent(String name);

  void declareKeyValueStore(String name, Class keyClass, Class valClass);
  void declareDocumentStore(String name, Class keyClass, Class... keyValClasses);
  // TODO: what other stores? column-oriented?
  //    - there should be a text search store
  PState.Declaration declarePState(String name, Class schema);
  PState.Declaration declarePState(String name,  PState.Schema schema);

  // TODO: document how to make LLMs
  void declareObject(Object o);

  StreamTopology getStreamTopology();

  // TODO: methods to define objects and query topologies
  //    - have this extend "Objects"?
  //    - and also implement "Agents"?

  // TODO: this should error if called twice
  void define();
}
