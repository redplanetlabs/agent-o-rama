package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;
import java.util.*;

public interface AggNode {
  public static AggNode.Impl create() {
    return (AggNode.Impl) AORHelpers.CREATE_AGG_NODE.invoke();
  }
  <% (dofor [i (range 0 (- MAX-ARITY 2))] (str %>
  public static <%= (mk-agg-node-on-type-decl i) %> AggNode.Impl on(String name, RamaFunction<%= (+ i 2) %><%= (mk-agg-node-on-type-arg-decl i) %> impl) {
    return create().on(name, impl);
  }<% )) %>

  public static <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl) {
    return create().onAny(impl);
  }

  public static <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl) {
    return create().onComplete(impl);
  }

  interface Impl {<% (dofor [i (range 0 (- MAX-ARITY 2))] (str %>
    <%= (mk-agg-node-on-type-decl i) %> AggNode.Impl on(String name, RamaFunction<%= (+ i 2) %><%= (mk-agg-node-on-type-arg-decl i) %> impl);<% )) %>
    <T> AggNode.Impl onAny(RamaFunction3<AgentNode, T, List, T> impl);
    <T> AggNode.Impl onComplete(RamaVoidFunction2<AgentNode, T> impl);
  }
}
