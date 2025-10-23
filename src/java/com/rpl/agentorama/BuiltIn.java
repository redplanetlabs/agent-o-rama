package com.rpl.agentorama;

import com.rpl.agentorama.impl.BuiltInAgg;
import com.rpl.rama.Agg;
import com.rpl.rama.impl.AggImpl;

/**
 * Built-in aggregators for use with agent aggregation nodes.
 * 
 * This class provides pre-configured aggregators that can be used with
 * {@link AgentTopology#newAgent(String)} aggregation nodes. These aggregators
 * are wrappers around Rama's built-in aggregation functions, adapted for use
 * in agent-o-rama.
 *
 * @see <a href="https://redplanetlabs.com/docs/~/aggregators.html">Rama Aggregators Documentation</a>
 */
public class BuiltIn {
  
  /**
   * Logical AND aggregator.
   * 
   * Combines boolean values using logical AND. Returns true only if all
   * aggregated values are true.
   */
  public static BuiltInAgg AND_AGG = new BuiltInAgg(((AggImpl) Agg.and("*v")).getAgg());
  
  /**
   * First value aggregator.
   * 
   * Returns the first value encountered during aggregation.
   */
  public static BuiltInAgg FIRST_AGG = new BuiltInAgg(((AggImpl) Agg.first("*v")).getAgg());
  
  /**
   * Last value aggregator.
   * 
   * Returns the last value encountered during aggregation.
   */
  public static BuiltInAgg LAST_AGG = new BuiltInAgg(((AggImpl) Agg.last("*v")).getAgg());
  
  /**
   * List aggregator.
   * 
   * Collects all values into a list in the order they were emitted.
   */
  public static BuiltInAgg LIST_AGG = new BuiltInAgg(((AggImpl) Agg.list("*v")).getAgg());
  
  /**
   * Map aggregator.
   * 
   * Collects key-value pairs into a map. Keys and values are 
   * the first two elements of each emitted value.
   */
  public static BuiltInAgg MAP_AGG = new BuiltInAgg(((AggImpl) Agg.map("*k", "*v")).getAgg());
  
  /**
   * Maximum value aggregator.
   * 
   * Returns the maximum value encountered during aggregation.
   * Values must be comparable.
   */
  public static BuiltInAgg MAX_AGG = new BuiltInAgg(((AggImpl) Agg.max("*v")).getAgg());
  
  /**
   * Merge map aggregator.
   * 
   * Merges multiple maps into a single map. Each emitted value should
   * be a map that gets merged into the aggregated result.
   */
  public static BuiltInAgg MERGE_MAP_AGG = new BuiltInAgg(((AggImpl) Agg.mergeMap("*m")).getAgg());
  
  /**
   * Minimum value aggregator.
   * 
   * Returns the minimum value encountered during aggregation.
   * Values must be comparable.
   */
  public static BuiltInAgg MIN_AGG = new BuiltInAgg(((AggImpl) Agg.min("*v")).getAgg());
  
  /**
   * Multi-set aggregator.
   * 
   * Collects values into a map from element to count.
   */
  public static BuiltInAgg MULTI_SET_AGG = new BuiltInAgg(((AggImpl) Agg.multiSet("*v")).getAgg());
  
  /**
   * Logical OR aggregator.
   * 
   * Combines boolean values using logical OR. Returns true if any
   * aggregated value is true.
   */
  public static BuiltInAgg OR_AGG = new BuiltInAgg(((AggImpl) Agg.or("*v")).getAgg());
  
  /**
   * Set aggregator.
   * 
   * Collects unique values into a set, automatically deduplicating
   * identical values.
   */
  public static BuiltInAgg SET_AGG = new BuiltInAgg(((AggImpl) Agg.set("*v")).getAgg());
  
  /**
   * Sum aggregator.
   * 
   * Sums all numeric values encountered during aggregation.
   * Values must be numeric types (Integer, Long, Double, etc.).
   */
  public static BuiltInAgg SUM_AGG = new BuiltInAgg(((AggImpl) Agg.sum("*v")).getAgg());
}