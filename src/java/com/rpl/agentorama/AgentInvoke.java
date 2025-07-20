package com.rpl.agentorama;

import com.rpl.rama.RamaSerializable;

public class AgentInvoke implements RamaSerializable {
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
