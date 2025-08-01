package com.rpl.agentorama.impl;

import java.io.*;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.integration.*;

public class RamaClientsTaskGlobal implements TaskGlobalObject {
  public static String agentDepotName(String agentName) {
    return "*_agent-depot-" + agentName;
  }

  public static String agentStreamingDepotName(String agentName) {
    return "*_agent-streaming-depot-" + agentName;
  }

  public static String agentHumanDepotName(String agentName) {
    return "*_agent-human-depot-" + agentName;
  }

  public static String AGENT_PSTATE_WRITE_DEPOT = "*_agent-pstate-write";

  private static class ClientInfo implements Closeable {
    private String moduleName;
    public Map<List, PState> mirrorClients;
    public Map<String, Depot> agentDepots;
    public Map<String, Depot> streamingDepots;
    public Map<String, Depot> humanDepots;
    public ConcurrentHashMap<String, PState> localPStates;
    public Depot pstateWritesDepot;
    ClusterManagerBase manager;

    public ClientInfo(String moduleName, Map mirrorClients, Map agentDepots, Map streamingDepots, Map humanDepots, Depot pstateWritesDepot, ClusterManagerBase manager) {
      this.moduleName = moduleName;
      this.mirrorClients = mirrorClients;
      this.agentDepots = agentDepots;
      this.streamingDepots = streamingDepots;
      this.humanDepots = humanDepots;
      this.pstateWritesDepot = pstateWritesDepot;
      this.localPStates = new ConcurrentHashMap();
      this.manager = manager;
    }

    public PState getLocalPState(String pstateName) {
      PState ret = localPStates.get(pstateName);
      if(ret==null) {
        synchronized(this) {
          ret = localPStates.get(pstateName);
          if(ret==null) {
            ret = manager.clusterPState(moduleName, pstateName);
            localPStates.put(pstateName, ret);
          }
        }
      }
      return ret;
    }

    @Override
    public void close() throws IOException {
      manager.close();
    }
  }

  WorkerManagedResource<ClientInfo> _clientInfo;

  final Collection<String> _agentNames;
  final List<List> _mirrorTuples;


  public PState getMirrorPState(String moduleName, String pstateName) {
    List tuple = new ArrayList();
    tuple.add(moduleName);
    tuple.add(pstateName);
    PState ret = _clientInfo.getResource().mirrorClients.get(tuple);
    if(ret==null) throw new RuntimeException("Mirror PState is not a dependency:" + moduleName + "/" + pstateName);
    return ret;
  }

  public Depot getPStateWriteDepot() {
    return _clientInfo.getResource().pstateWritesDepot;
  }

  public Depot getAgentDepot(String agentName) {
    return _clientInfo.getResource().agentDepots.get(agentName);
  }

  public Depot getAgentStreamingDepot(String agentName) {
    return _clientInfo.getResource().streamingDepots.get(agentName);
  }

  public Depot getAgentHumanDepot(String agentName) {
    return _clientInfo.getResource().humanDepots.get(agentName);
  }

  public PState getLocalPState(String pstateName) {
    return _clientInfo.getResource().getLocalPState(pstateName);
  }

  public RamaClientsTaskGlobal(Collection<String> agentNames, List<List> mirrorTuples) {
    _agentNames = agentNames;
    _mirrorTuples = mirrorTuples;
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _clientInfo = new WorkerManagedResource("agentClients", context,
                    () -> {
                      String moduleName = context.getModuleInstanceInfo().getModuleName();
                      ClusterManagerBase manager = context.getClusterRetriever();
                      Map agentDepots = new HashMap();
                      for(String name: _agentNames) {
                        agentDepots.put(name, manager.clusterDepot(moduleName, agentDepotName(name)));
                      }
                      Map streamingDepots = new HashMap();
                      for(String name: _agentNames) {
                        streamingDepots.put(name, manager.clusterDepot(moduleName, agentStreamingDepotName(name)));
                      }
                      Map humanDepots = new HashMap();
                      for(String name: _agentNames) {
                        humanDepots.put(name, manager.clusterDepot(moduleName, agentHumanDepotName(name)));
                      }
                      Map clients = new HashMap();
                      for(List<String> tuple: _mirrorTuples) {
                        String mm = tuple.get(0);
                        String pstateName = tuple.get(1);
                        clients.put(tuple, manager.clusterPState(mm, pstateName));
                      }
                      return new ClientInfo(
                               moduleName,
                               clients,
                               agentDepots,
                               streamingDepots,
                               humanDepots,
                               manager.clusterDepot(moduleName, AGENT_PSTATE_WRITE_DEPOT),
                               manager);
                    });
  }

  @Override
  public void close() throws IOException {
    _clientInfo.close();
  }
}
