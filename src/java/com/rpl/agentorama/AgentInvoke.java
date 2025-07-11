package com.rpl.agentorama;

public class AgentInvoke {
  long _taskId;
  long _agentInvokeId;

  public AgentInvoke(long taskId, long agentInvokeId) {
    _taskId = taskId;
    _agentInvokeId = agentInvokeId;
  }

  public long getTaskId() {
    return _taskId;
  }

  public long getAgentInvokeId() {
    return _agentInvokeId;
  }
}
