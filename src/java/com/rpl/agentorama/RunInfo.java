package com.rpl.agentorama;

import com.rpl.agentorama.analytics.*;
import java.util.*;

public interface RunInfo {
  /**
   * Returns whether this is a run info for an agent or a node.
   *
   * @return run type
   */
  RunType getRunType();
  /**
   * Return latency of this run.
   *
   * @return latency in milliseconds
   */
  long getLatencyMillis();
  /**
   * Get all feedback on this run.
   *
   * @return List of feedback in order in which they were given
   */
  List<Feedback> getFeedback();
  /**
   * Returns stats for agent run. This method returns null if this is a RunInfo for a node.
   *
   * @return agent invoke stats
   */
  AgentInvokeStats getAgentStats();
  /**
   * Returns nested op info for node run. This method returns null if this is a RunInfo for an agent.
   *
   * @return list of nested op infos in order of their execution
   */
  List<NestedOpInfo> getNodeNestedOps();
}
