package com.rpl.agentorama;

import com.rpl.agentorama.analytics.*;
import java.util.*;

public interface RunInfo {
  /**
   * Returns name of the rule for the action.
   *
   * @return rule name
   */
  String getRuleName();

  /**
   * Returns name of the action.
   *
   * @return action name
   */
  String getActionName();

  /**
   * Returns agent name this run was from.
   *
   * @return agent name
   */
  String getAgentName();

  /**
   * Get agent invoke for the run
   *
   * @return agent invoke
   */
  AgentInvoke getAgentInvoke();

  /**
   * If this is RunInfo for a node, returns node name this run was from. Otherwise, returns null.
   *
   * @return node name
   */
  String getNodeName();

  /**
   * If this is RunInfo for a node, returns node invoke information. Otherwise, returns null.
   *
   * @return node invoke
   */
  NodeInvoke getNodeInvoke();

  /**
   * Returns whether this is a run info for an agent or a node.
   *
   * @return run type
   */
  RunType getRunType();
  /**
   * Return latency of this run. May be null if the node failed and never completed.
   *
   * @return latency in milliseconds
   */
  Long getLatencyMillis();
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
