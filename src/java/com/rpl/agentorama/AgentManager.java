package com.rpl.agentorama;

import java.util.Set;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.agentorama.impl.IFetchAgentClient;
import com.rpl.rama.cluster.ClusterManagerBase;

public interface AgentManager extends IFetchAgentClient {
  public static AgentManager create(ClusterManagerBase cluster, String moduleName) {
    return (AgentManager) AORHelpers.CREATE_AGENT_MANAGER.invoke(cluster, moduleName);
  }

  Set<String> getAgentNames();
}
