// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;
import java.util.*;

public interface AggNode {
  public static AggNode.Impl create() {
    return (AggNode.Impl) AORHelpers.CREATE_AGG_NODE.invoke();
  }
  
  public static <S> AggNode.Impl on(String name, RamaFunction2<AgentNode,S,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0> AggNode.Impl on(String name, RamaFunction3<AgentNode,S,T0,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0,T1> AggNode.Impl on(String name, RamaFunction4<AgentNode,S,T0,T1,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0,T1,T2> AggNode.Impl on(String name, RamaFunction5<AgentNode,S,T0,T1,T2,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0,T1,T2,T3> AggNode.Impl on(String name, RamaFunction6<AgentNode,S,T0,T1,T2,T3,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0,T1,T2,T3,T4> AggNode.Impl on(String name, RamaFunction7<AgentNode,S,T0,T1,T2,T3,T4,S> impl) {
    return create().on(name, impl);
  }
  public static <S,T0,T1,T2,T3,T4,T5> AggNode.Impl on(String name, RamaFunction8<AgentNode,S,T0,T1,T2,T3,T4,T5,S> impl) {
    return create().on(name, impl);
  }

  public static <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl) {
    return create().onAny(impl);
  }

  public static <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl) {
    return create().onComplete(impl);
  }

  interface Impl {
    <S> AggNode.Impl on(String name, RamaFunction2 <AgentNode,S,S> impl);
    <S,T0> AggNode.Impl on(String name, RamaFunction3 <AgentNode,S,T0,S> impl);
    <S,T0,T1> AggNode.Impl on(String name, RamaFunction4 <AgentNode,S,T0,T1,S> impl);
    <S,T0,T1,T2> AggNode.Impl on(String name, RamaFunction5 <AgentNode,S,T0,T1,T2,S> impl);
    <S,T0,T1,T2,T3> AggNode.Impl on(String name, RamaFunction6 <AgentNode,S,T0,T1,T2,T3,S> impl);
    <S,T0,T1,T2,T3,T4> AggNode.Impl on(String name, RamaFunction7 <AgentNode,S,T0,T1,T2,T3,T4,S> impl);
    <S,T0,T1,T2,T3,T4,T5> AggNode.Impl on(String name, RamaFunction8 <AgentNode,S,T0,T1,T2,T3,T4,T5,S> impl);
    <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl);
    <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl);
  }
}
