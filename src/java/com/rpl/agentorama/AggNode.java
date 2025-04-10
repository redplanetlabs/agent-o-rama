// this file is auto-generated
package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.ops.*;
import java.util.*;

public interface AggNode {
  <T0> AggNode on(String name, RamaVoidFunction2<AgentNode,T0> impl);
  <T0,T1> AggNode on(String name, RamaVoidFunction3<AgentNode,T0,T1> impl);
  <T0,T1,T2> AggNode on(String name, RamaVoidFunction4<AgentNode,T0,T1,T2> impl);
  <T0,T1,T2,T3> AggNode on(String name, RamaVoidFunction5<AgentNode,T0,T1,T2,T3> impl);
  <T0,T1,T2,T3,T4> AggNode on(String name, RamaVoidFunction6<AgentNode,T0,T1,T2,T3,T4> impl);
  <T0,T1,T2,T3,T4,T5> AggNode on(String name, RamaVoidFunction7<AgentNode,T0,T1,T2,T3,T4,T5> impl);
  <T0,T1,T2,T3,T4,T5,T6> AggNode on(String name, RamaVoidFunction8<AgentNode,T0,T1,T2,T3,T4,T5,T6> impl);
  <T> AggNode onAny(RamaFunction3<AgentNode, T, List, T> impl);
  <T> void onComplete(RamaVoidFunction2<AgentNode, T> impl);
}
