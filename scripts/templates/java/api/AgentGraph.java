package com.rpl.agentorama;

import com.rpl.agentorama.impl.BuiltInAgg;
import com.rpl.agentorama.ops.*;
import com.rpl.rama.ops.*;

public interface AgentGraph {
  AgentGraph setUpdateMode(UpdateMode mode);<% (dofor [i (range 0 (dec MAX-ARITY))] (str %>
  <%= (mk-full-type-decl i) %> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i []) %> impl);<% )) %><% (dofor [i (range 1 (dec MAX-ARITY))] (str %>
  <%= (mk-full-type-decl i) %> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction<%= (inc i) %><%= (mk-full-type-decl ["AgentNode"] i ["Object"]) %> impl);<% )) %>
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaAccumulatorAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaCombinerAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, MultiAgg.Impl agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, BuiltInAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
}
