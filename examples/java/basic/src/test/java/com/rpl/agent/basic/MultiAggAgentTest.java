package com.rpl.agent.basic;

import org.junit.Test;

/**
 * Test class for MultiAggAgent demonstrating custom aggregation logic.
 *
 * <p>This test demonstrates:
 *
 * <ul>
 *   <li>Testing that the multi-agg agent main method runs without errors
 *   <li>Verifying custom aggregation with multiple tagged input streams executes successfully
 * </ul>
 */
public class MultiAggAgentTest {

  @Test
  public void testMultiAggAgent() throws Exception {
    // Test that the multi-agg agent runs without errors
    MultiAggAgent.main(new String[] {});
  }
}
