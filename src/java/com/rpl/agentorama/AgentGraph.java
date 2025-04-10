// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.ops.*;

public interface AgentGraph {
  <T0> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction2<AgentNode,T0> impl);
  <T0,T1> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction3<AgentNode,T0,T1> impl);
  <T0,T1,T2> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
  <T0,T1,T2,T3> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
  <T0,T1,T2,T3,T4> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
  <T0,T1,T2,T3,T4,T5> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
  <T0> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction2<AgentNode,T0> impl);
  <T0,T1> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction3<AgentNode,T0,T1> impl);
  <T0,T1,T2> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
  <T0,T1,T2,T3> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
  <T0,T1,T2,T3,T4> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
  <T0,T1,T2,T3,T4,T5> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
  AgentGraph aggNode(String name, Object outputNodesSpec, AggNode aggNode);
}
