package com.rpl.agentorama.impl;

import java.io.*;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.*;
import com.rpl.rama.integration.*;

public class RamaClientsTaskGlobal implements TaskGlobalObject {
  public static String agentDepotName(String agentName) {
    return "*_agent-depot-" + agentName;
  }

  public static String AGENT_PSTATE_WRITE_DEPOT = "*_agent-pstate-write";

  private static class MirrorClientInfo implements Closeable {
    private String moduleName;
    public Map<List, PState> mirrorClients;
    public Map<String, Depot> agentDepots;
    public ConcurrentHashMap<String, PState> localPStates;
    public Depot pstateWritesDepot;
    RamaClusterManager manager;

    public MirrorClientInfo(String moduleName, Map mirrorClients, Map agentDepots, Depot pstateWritesDepot, RamaClusterManager manager) {
      this.moduleName = moduleName;
      this.mirrorClients = mirrorClients;
      this.agentDepots = agentDepots;
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

  WorkerManagedResource<MirrorClientInfo> _mirrorClientInfo;

  final Collection<String> _agentNames;
  final List<List> _mirrorTuples;


  public PState getMirrorPState(String moduleName, String pstateName) {
    List tuple = new ArrayList();
    tuple.add(moduleName);
    tuple.add(pstateName);
    PState ret = _mirrorClientInfo.getResource().mirrorClients.get(tuple);
    if(ret==null) throw new RuntimeException("Mirror PState is not a dependency:" + moduleName + "/" + pstateName);
    return ret;
  }

  public Depot getPStateWriteDepot() {
    return _mirrorClientInfo.getResource().pstateWritesDepot;
  }

  public Depot getAgentContinueDepot(String agentName) {
    return _mirrorClientInfo.getResource().agentDepots.get(agentName);
  }

  public PState getLocalPState(String pstateName) {
    return _mirrorClientInfo.getResource().getLocalPState(pstateName);
  }

  // TODO: maybe this should contain store info as well?
  public RamaClientsTaskGlobal(Collection<String> agentNames, List<List> mirrorTuples) {
    _agentNames = agentNames;
    _mirrorTuples = mirrorTuples;
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _mirrorClientInfo = new WorkerManagedResource("agentClients", context,
                          () -> {
                            String moduleName = context.getModuleInstanceInfo().getModuleName();
                            RamaClusterManager manager = RamaClusterManager.openInternal();
                            Map agentDepots = new HashMap();
                            for(String name: _agentNames) {
                              agentDepots.put(name, manager.clusterDepot(moduleName, agentDepotName(name)));
                            }
                            Map clients = new HashMap();
                            for(List<String> tuple: _mirrorTuples) {
                              String mm = tuple.get(0);
                              String pstateName = tuple.get(1);
                              clients.put(tuple, manager.clusterPState(mm, pstateName));
                            }
                            return new MirrorClientInfo(
                                     moduleName,
                                     clients,
                                     agentDepots,
                                     manager.clusterDepot(moduleName, AGENT_PSTATE_WRITE_DEPOT),
                                     manager);
                          });
  }

  @Override
  public void close() throws IOException {
    _mirrorClientInfo.close();
  }
}
