package com.rpl.agentorama;

import com.rpl.rama.ops.*;
import com.rpl.agentorama.impl.AORHelpers;

/**
 * Creates an aggregator for use with aggregation nodes that supports multiple dispatch targets.
 * 
 * MultiAgg provides dispatch-based aggregation where different values are processed by
 * different aggregation functions based on a dispatch key. The first argument when emitting
 * to the aggregation node is the dispatch target, which runs the corresponding handler.
 * 
 * Example usage:
 * <pre>{@code
 * MultiAgg.Impl agg = MultiAgg.create()
 *     .init(() -> Map.of("sum", 0, "texts", new ArrayList<>()))
 *     .on("add", (acc, value) -> {
 *         Map<String, Object> newAcc = new HashMap<>(acc);
 *         newAcc.put("sum", (Integer) newAcc.get("sum") + (Integer) value);
 *         return newAcc;
 *     })
 *     .on("text", (acc, text) -> {
 *         Map<String, Object> newAcc = new HashMap<>(acc);
 *         ((List<String>) newAcc.get("texts")).add((String) text);
 *         return newAcc;
 *     });
 * }</pre>
 */
public interface MultiAgg {
  
  /**
   * Creates a new multi-aggregator instance.
   * 
   * @return a new multi-aggregator builder
   */
  public static MultiAgg.Impl create() {
    return (MultiAgg.Impl) AORHelpers.CREATE_MULTI_AGG.invoke();
  }

  /**
   * Creates a multi-aggregator with an initial value function.
   * 
   * @param impl function that returns the initial aggregation value
   * @return a multi-aggregator builder with the initial value set
   */
  public static <S> MultiAgg.Impl init(RamaFunction0<S> impl) {
    return create().init(impl);
  }
  
<% (dofor [i (range 0 (- MAX-ARITY 1))] (str %>
  /**
   * Creates a multi-aggregator with a dispatch handler for <%= (javadoc/args-str i) %>.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus <%= (javadoc/args-str i) %>
   * @return a multi-aggregator builder with the handler set
   */
  public static <%= (mk-agg-node-on-type-decl i) %> MultiAgg.Impl on(String name, RamaFunction<%= (+ i 1) %><%= (mk-agg-node-on-type-arg-decl i) %> impl) {
    return create().on(name, impl);
  }
  <% )) %>

  /**
   * Builder interface for configuring multi-aggregators.
   */
  interface Impl {
    
    /**
     * Sets the initial value function for the aggregation.
     * 
     * @param impl function that returns the initial aggregation value
     * @return this builder for method chaining
     */
    <S> Impl init(RamaFunction0<S> impl);
    
<% (dofor [i (range 0 (- MAX-ARITY 1))] (str %>
    /**
     * Adds a dispatch handler for <%= (javadoc/args-str i) %>.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus <%= (javadoc/args-str i) %>
     * @return this builder for method chaining
     */
    <%= (mk-agg-node-on-type-decl i) %> MultiAgg.Impl on(String name, RamaFunction<%= (+ i 1) %><%= (mk-agg-node-on-type-arg-decl i) %> impl);<% )) %>
  }
}
