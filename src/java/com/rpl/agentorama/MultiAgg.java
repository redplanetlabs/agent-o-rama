// this file is auto-generated
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
  

  /**
   * Creates a multi-aggregator with a dispatch handler for zero arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus zero arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S> MultiAgg.Impl on(String name, RamaFunction1<S,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for one argument.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus one argument
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0> MultiAgg.Impl on(String name, RamaFunction2<S,T0,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for two arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus two arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1> MultiAgg.Impl on(String name, RamaFunction3<S,T0,T1,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for three arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus three arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1,T2> MultiAgg.Impl on(String name, RamaFunction4<S,T0,T1,T2,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for four arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus four arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1,T2,T3> MultiAgg.Impl on(String name, RamaFunction5<S,T0,T1,T2,T3,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for five arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus five arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1,T2,T3,T4> MultiAgg.Impl on(String name, RamaFunction6<S,T0,T1,T2,T3,T4,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for six arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus six arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1,T2,T3,T4,T5> MultiAgg.Impl on(String name, RamaFunction7<S,T0,T1,T2,T3,T4,T5,Object> impl) {
    return create().on(name, impl);
  }
  
  /**
   * Creates a multi-aggregator with a dispatch handler for seven arguments.
   * 
   * @param name the dispatch target name
   * @param impl the handler function that takes the current aggregation value plus seven arguments
   * @return a multi-aggregator builder with the handler set
   */
  public static <S,T0,T1,T2,T3,T4,T5,T6> MultiAgg.Impl on(String name, RamaFunction8<S,T0,T1,T2,T3,T4,T5,T6,Object> impl) {
    return create().on(name, impl);
  }
  

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
    

    /**
     * Adds a dispatch handler for zero arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus zero arguments
     * @return this builder for method chaining
     */
    <S> MultiAgg.Impl on(String name, RamaFunction1<S,Object> impl);
    /**
     * Adds a dispatch handler for one argument.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus one argument
     * @return this builder for method chaining
     */
    <S,T0> MultiAgg.Impl on(String name, RamaFunction2<S,T0,Object> impl);
    /**
     * Adds a dispatch handler for two arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus two arguments
     * @return this builder for method chaining
     */
    <S,T0,T1> MultiAgg.Impl on(String name, RamaFunction3<S,T0,T1,Object> impl);
    /**
     * Adds a dispatch handler for three arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus three arguments
     * @return this builder for method chaining
     */
    <S,T0,T1,T2> MultiAgg.Impl on(String name, RamaFunction4<S,T0,T1,T2,Object> impl);
    /**
     * Adds a dispatch handler for four arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus four arguments
     * @return this builder for method chaining
     */
    <S,T0,T1,T2,T3> MultiAgg.Impl on(String name, RamaFunction5<S,T0,T1,T2,T3,Object> impl);
    /**
     * Adds a dispatch handler for five arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus five arguments
     * @return this builder for method chaining
     */
    <S,T0,T1,T2,T3,T4> MultiAgg.Impl on(String name, RamaFunction6<S,T0,T1,T2,T3,T4,Object> impl);
    /**
     * Adds a dispatch handler for six arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus six arguments
     * @return this builder for method chaining
     */
    <S,T0,T1,T2,T3,T4,T5> MultiAgg.Impl on(String name, RamaFunction7<S,T0,T1,T2,T3,T4,T5,Object> impl);
    /**
     * Adds a dispatch handler for seven arguments.
     * 
     * @param name the dispatch target name
     * @param impl the handler function that takes the current aggregation value plus seven arguments
     * @return this builder for method chaining
     */
    <S,T0,T1,T2,T3,T4,T5,T6> MultiAgg.Impl on(String name, RamaFunction8<S,T0,T1,T2,T3,T4,T5,T6,Object> impl);
  }
}
