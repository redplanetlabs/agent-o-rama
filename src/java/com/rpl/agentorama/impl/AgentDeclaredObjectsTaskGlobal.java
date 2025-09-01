package com.rpl.agentorama.impl;

import java.io.IOException;

import com.rpl.agentorama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.integration.*;

import clojure.lang.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class AgentDeclaredObjectsTaskGlobal implements TaskGlobalObject {
  public static ThreadLocal<Long> ACQUIRE_TIMEOUT_MILLIS = new ThreadLocal<>();
  Map<String, Map<String, Object>> _builders;
  Map<String, Map<Keyword, Object>> _evaluatorBuilders;
  Map<String, List<String>> _agentsInfo;
  Map<String, Object> _agentGraphs;

  Map<String, WorkerManagedResource> _objects;
  Map<String, List> _evaluators;
  String _thisModuleName;
  WorkerManagedResource<Map<String, AgentClient>> _agents;
  ClusterManagerBase _clusterRetriever;


  // agents is localName -> [moduleName, agentName] (nil for local module)
  public AgentDeclaredObjectsTaskGlobal(
    Map<String, Map<String, Object>> builders,
    Map<String, Map<Keyword, Object>> evaluatorBuilders,
    Map<String, List<String>> agentsInfo,
    Map<String, Object> agentGraphs) {
    _builders = builders;
    _evaluatorBuilders = evaluatorBuilders;
    _agentsInfo = agentsInfo;
    _agentGraphs = agentGraphs;
  }

  public String getThisModuleName() {
    return _thisModuleName;
  }

  public Map getEvaluatorBuilders() {
    return _evaluatorBuilders;
  }

  public Map getAgentGraphs() {
    return _agentGraphs;
  }

  public IFn getEvaluator(String name, String builderName, Map<String, Object> params) {
    List curr = _evaluators.get(name);
    if(curr!=null) {
      String prevBuilderName = (String) curr.get(0);
      Map prevParams = (Map) curr.get(1);
      if(builderName.equals(prevBuilderName) && params.equals(prevParams)) {
        return (IFn) curr.get(2);
      }
    }
    synchronized(_evaluators) {
      Map<Keyword, Object> eparams = _evaluatorBuilders.get(builderName);
      if(eparams==null) eparams = (Map<Keyword,Object>) ((Map)AORHelpers.BUILT_IN_EVAL_BUILDERS.deref()).get(builderName);
      if(eparams==null) throw new RuntimeException("Invalid evaluator builder name: " + builderName);
      IFn builderFn = (IFn) eparams.get(Keyword.intern(null, "builder-fn"));
      IFn ret = (IFn) builderFn.invoke(params);
      _evaluators.put(name, Arrays.asList(builderName, params, ret));
      return ret;
    }
  }

  public Object getAgentObjectFromResource(String name) {
    if(!_objects.containsKey(name)) {
      throw new RuntimeException("Agent object does not exist: " + name);
    }
    Object resource = _objects.get(name).getResource();
    if(resource instanceof LazyObjectPool) {
      return ((LazyObjectPool) resource).acquire(ACQUIRE_TIMEOUT_MILLIS.get());
    } else {
      return resource;
    }
  }

  public void releaseAgentObject(String name, Object o) {
      Object res = _objects.get(name).getResource();
      if(res instanceof LazyObjectPool) {
        ((LazyObjectPool) res).release(o);
      }
  }

  public AgentClient getAgentClient(String localName) {
    AgentClient ret = _agents.getResource().get(localName);
    if(ret==null) throw new RuntimeException("Tried to fetch non-existent agent: " + localName);
    return ret;
  }

  public List<String> getAgentInfo(String localName) {
    List<String> ret = _agentsInfo.get(localName);
    if(ret==null) throw new RuntimeException("Could not find agent " + localName);
    if(ret.get(0)==null) {
      return Arrays.asList(_thisModuleName, ret.get(1));
    } else {
      return ret;
    }
  }

  public ClusterManagerBase getClusterRetriever() {
    return _clusterRetriever;
  }

  private static Object makeObject(String name, IFn afn, AgentObjectSetup setup, boolean autoTracing) {
    Object o = afn.invoke(setup);
    return autoTracing ? AORHelpers.WRAP_AGENT_OBJECT.invoke(name, o) : o;
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _thisModuleName = context.getModuleInstanceInfo().getModuleName();
    _evaluators = new ConcurrentHashMap();
    _clusterRetriever = context.getClusterRetriever();

    _objects = new HashMap();
    for(String name: _builders.keySet()) {
      Map info = _builders.get(name);
      int limit = ((Number) info.get("limit")).intValue();
      boolean threadSafe = (boolean) info.get("threadSafe");
      boolean autoTracing = (boolean) info.get("autoTracing");
      IFn afn = (IFn) info.get("builderFn");
      AgentObjectSetup setup = new AgentObjectSetup() {
        @Override
        public <T> T getAgentObject(String otherName) {
          return (T) getAgentObjectFromResource(otherName);
        }

        @Override
        public String getObjectName() {
          return name;
        }
      };
      _objects.put(name, new WorkerManagedResource(name, context, () -> {
        if(threadSafe) return makeObject(name, afn, setup, autoTracing);
        else return new LazyObjectPool(limit, () -> makeObject(name, afn, setup, autoTracing));
      }));
    }

    _agents = new WorkerManagedResource("__agentClients", context, () -> {
      Map m = new CloseableMap();
      Set<String> moduleNames = new HashSet();
      for(List<String> tuple: _agentsInfo.values()) {
        String moduleName = tuple.get(0);
        moduleNames.add(moduleName);
      }
      Map<String, AgentManager> managers = new HashMap();
      for(String moduleName: moduleNames) {
        String mn = moduleName == null ? context.getModuleInstanceInfo().getModuleName() : moduleName;
        managers.put(moduleName, AgentManager.create(context.getClusterRetriever(), mn));
      }
      for(String localName: _agentsInfo.keySet()) {
        List<String> tuple = _agentsInfo.get(localName);
        String moduleName = tuple.get(0);
        String agentName = tuple.get(1);
        m.put(localName, managers.get(moduleName).getAgentClient(agentName));
      }
      return m;
    });
  }

  @Override
  public void close() throws IOException {
    for(WorkerManagedResource resource: _objects.values()) {
      resource.close();
    }
    _agents.close();
  }
}
