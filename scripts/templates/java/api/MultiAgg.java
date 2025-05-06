package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;

public interface MultiAgg {
  public static MultiAgg.Impl create() {
    return (MultiAgg.Impl) AORHelpers.CREATE_MULTI_AGG.invoke();
  }

  public static <S> MultiAgg.Impl init(RamaFunction0<S> impl) {
    return create().init(impl);
  }
  <% (dofor [i (range 0 (- MAX-ARITY 1))] (str %>
  public static <%= (mk-agg-node-on-type-decl i) %> MultiAgg.Impl on(String name, RamaFunction<%= (+ i 1) %><%= (mk-agg-node-on-type-arg-decl i) %> impl) {
    return create().on(name, impl);
  }
  <% )) %>

  interface Impl {
    <S> Impl init(RamaFunction0<S> impl);<% (dofor [i (range 0 (- MAX-ARITY 1))] (str %>
    <%= (mk-agg-node-on-type-decl i) %> MultiAgg.Impl on(String name, RamaFunction<%= (+ i 1) %><%= (mk-agg-node-on-type-arg-decl i) %> impl);<% )) %>
  }
}
