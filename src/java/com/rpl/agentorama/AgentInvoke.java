package com.rpl.agentorama;

public class AgentInvoke {
  int _taskId;
  long _agentInvokeId;

  public AgentInvoke(int taskId, long agentInvokeId) {
    _taskId = taskId;
    _agentInvokeId = agentInvokeId;
  }

  public int getTaskId() {
    return _taskId;
  }

  public long getAgentInvokeId() {
    return _agentInvokeId;
  }
}
