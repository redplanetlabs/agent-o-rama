// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.agentorama.impl.BuiltInAgg;
import com.rpl.agentorama.ops.*;
import com.rpl.rama.ops.*;

public interface AgentGraph {
  AgentGraph setUpdateMode(UpdateMode mode);
   AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction1<AgentNode> impl);
  <T0> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction2<AgentNode,T0> impl);
  <T0,T1> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction3<AgentNode,T0,T1> impl);
  <T0,T1,T2> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
  <T0,T1,T2,T3> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
  <T0,T1,T2,T3,T4> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
  <T0,T1,T2,T3,T4,T5> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph node(String name, Object outputNodesSpec, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
   AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction1<AgentNode,Object> impl);
  <T0> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction2<AgentNode,T0,Object> impl);
  <T0,T1> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction3<AgentNode,T0,T1,Object> impl);
  <T0,T1,T2> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction4<AgentNode,T0,T1,T2,Object> impl);
  <T0,T1,T2,T3> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction5<AgentNode,T0,T1,T2,T3,Object> impl);
  <T0,T1,T2,T3,T4> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction6<AgentNode,T0,T1,T2,T3,T4,Object> impl);
  <T0,T1,T2,T3,T4,T5> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction7<AgentNode,T0,T1,T2,T3,T4,T5,Object> impl);
  <T0,T1,T2,T3,T4,T5,T6> AgentGraph aggStartNode(String name, Object outputNodesSpec, RamaFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6,Object> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaAccumulatorAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, RamaCombinerAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, MultiAgg.Impl agg, RamaVoidFunction3<AgentNode, S, T> impl);
  <S, T> AgentGraph aggNode(String name, Object outputNodesSpec, BuiltInAgg agg, RamaVoidFunction3<AgentNode, S, T> impl);
}
