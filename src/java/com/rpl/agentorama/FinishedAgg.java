package com.rpl.agentorama;

/**
 * Signals that an aggregator should immediately finish with a final value.
 *
 * When an aggregator implementation returns a FinishedAgg, the aggregation
 * immediately completes with the specified value. All future values sent to
 * the aggregator will be ignored.
 *
 * This is useful for implementing aggregators that can determine their final
 * result early based on certain conditions.
 */
public class FinishedAgg {
  private Object value;

  /**
   * Creates a FinishedAgg with the specified final value.
   *
   * @param value the final value for the aggregation
   */
  public FinishedAgg(Object value) {
      this.value = value;
  }

  /**
   * Gets the final value for the aggregation.
   *
   * @return the final value
   */
  public Object getValue() {
    return value;
  }
}
