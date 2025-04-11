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
  
  public static <T0> AggNode.Impl on(String name, RamaVoidFunction2<AgentNode,T0> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1> AggNode.Impl on(String name, RamaVoidFunction3<AgentNode,T0,T1> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1,T2> AggNode.Impl on(String name, RamaVoidFunction4<AgentNode,T0,T1,T2> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1,T2,T3> AggNode.Impl on(String name, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1,T2,T3,T4> AggNode.Impl on(String name, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1,T2,T3,T4,T5> AggNode.Impl on(String name, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl) {
    return create().on(name, impl);
  }
  public static <T0,T1,T2,T3,T4,T5,T6> AggNode.Impl on(String name, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl) {
    return create().on(name, impl);
  }

  public static <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl) {
    return create().onAny(impl);
  }

  public static <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl) {
    return create().onComplete(impl);
  }

  interface Impl {
    <T0> AggNode.Impl on(String name, RamaVoidFunction2<AgentNode,T0> impl);
    <T0,T1> AggNode.Impl on(String name, RamaVoidFunction3<AgentNode,T0,T1> impl);
    <T0,T1,T2> AggNode.Impl on(String name, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
    <T0,T1,T2,T3> AggNode.Impl on(String name, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
    <T0,T1,T2,T3,T4> AggNode.Impl on(String name, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
    <T0,T1,T2,T3,T4,T5> AggNode.Impl on(String name, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
    <T0,T1,T2,T3,T4,T5,T6> AggNode.Impl on(String name, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
    <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl);
    <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl);
  }
}
