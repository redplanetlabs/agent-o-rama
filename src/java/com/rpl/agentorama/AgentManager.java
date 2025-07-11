package com.rpl.agentorama;

import java.util.Set;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.cluster.ClusterManagerBase;

public interface AgentManager {
  public static AgentManager create(ClusterManagerBase cluster, String moduleName) {
    return (AgentManager) AORHelpers.CREATE_AGENT_MANAGER.invoke(cluster, moduleName);
  }

  AgentClient getAgentClient(String agentName);
  Set<String> getAgentNames();
}
