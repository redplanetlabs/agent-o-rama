package com.rpl.agentorama.impl;

import java.io.IOException;

import com.rpl.agentorama.*;
import com.rpl.rama.cluster.ClusterManagerBase;
import com.rpl.rama.*;
import com.rpl.rama.integration.*;

import clojure.lang.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import rpl.rama.generated.TopologyDoesNotExistException;

public class AgentDeclaredObjectsTaskGlobal implements TaskGlobalObject {
  public static final String MODULE_GET_STORE_INFO_QUERY_NAME = "_module-get-store-info";
  public static ThreadLocal<Long> ACQUIRE_TIMEOUT_MILLIS = new ThreadLocal<>();
  Map<String, Map<String, Object>> _builders;
  Map<String, Map<Keyword, Object>> _evaluatorBuilders;
  Map<String, Map<Keyword, Object>> _actionBuilders;
  Map<String, Object> _agentGraphs;

  Map<String, WorkerManagedResource> _objects;
  Map<String, List> _evaluators;
  String _thisModuleName;
  WorkerManagedResource<Map<String, AgentClient>> _agents;
  WorkerManagedResource<ConcurrentHashMap<String, AgentManager>> _managers;
  WorkerManagedResource<ConcurrentHashMap<List, AgentClient>> _mirrorAgents;
  ClusterManagerBase _clusterRetriever;
  WorkerManagedResource<AgentManager> _thisManager;
  WorkerManagedResource<ConcurrentHashMap<List, Depot>> _depots;
  WorkerManagedResource<ConcurrentHashMap<List, PState>> _pstates;
  WorkerManagedResource<ConcurrentHashMap<String, Map>> _mirrorStoreInfo;
  WorkerManagedResource<ConcurrentHashMap<List, QueryTopologyClient>> _queries;


  // agents is localName -> [moduleName, agentName] (nil for local module)
  public AgentDeclaredObjectsTaskGlobal(
    Map<String, Map<String, Object>> builders,
    Map<String, Map<Keyword, Object>> evaluatorBuilders,
    Map<String, Map<Keyword, Object>> actionBuilders,
    Map<String, Object> agentGraphs) {
    _builders = builders;
    _evaluatorBuilders = evaluatorBuilders;
    _actionBuilders = actionBuilders;
    _agentGraphs = agentGraphs;
  }

  public String getThisModuleName() {
    return _thisModuleName;
  }

  public AgentManager getThisModuleAgentManager() {
    return _thisManager.getResource();
  }

  public Map getEvaluatorBuilders() {
    return _evaluatorBuilders;
  }

  public Map getActionBuilders() {
    return _actionBuilders;
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

  public AgentClient getMirrorAgentClient(String moduleName, String agentName) {
    ConcurrentHashMap<String, AgentManager> managers = _managers.getResource();
    ConcurrentHashMap<List, AgentClient> mirrors = _mirrorAgents.getResource();

    AgentManager manager = managers.computeIfAbsent(moduleName, new Function<String, AgentManager>() {
      public AgentManager apply(String mn) {
        return AgentManager.create(_clusterRetriever, moduleName);
      }
    });

    return mirrors.computeIfAbsent(Arrays.asList(moduleName, agentName), new Function<List, AgentClient>() {
      public AgentClient apply(List tuple) {
        return manager.getAgentClient(agentName);
      }
    });
  }

  public Depot getForeignDepot(String moduleName, String name) {
    return _depots.getResource().computeIfAbsent(Arrays.asList(moduleName, name), new Function<List, Depot>() {
      public Depot apply(List tuple) {
        return _clusterRetriever.clusterDepot(moduleName, name);
      }
    });
  }

  public PState getForeignPState(String moduleName, String name) {
    return _pstates.getResource().computeIfAbsent(Arrays.asList(moduleName, name), new Function<List, PState>() {
      public PState apply(List tuple) {
        return _clusterRetriever.clusterPState(moduleName, name);
      }
    });
  }

  public Map getMirrorStoreInfo(String moduleName) {
    return _mirrorStoreInfo.getResource().computeIfAbsent(moduleName, new Function<String, Map>() {
      public Map apply(String mn) {
        try {
          QueryTopologyClient<Map> q = _clusterRetriever.clusterQuery(moduleName, MODULE_GET_STORE_INFO_QUERY_NAME);
          return q.invoke();
        } catch(Exception e) {
          if(e instanceof TopologyDoesNotExistException || e.getCause() instanceof TopologyDoesNotExistException)
            return new HashMap();
          else throw e;
        }
      }
    });
  }

  public QueryTopologyClient getForeignQuery(String moduleName, String name) {
    return _queries.getResource().computeIfAbsent(Arrays.asList(moduleName, name), new Function<List, QueryTopologyClient>() {
      public QueryTopologyClient apply(List tuple) {
        return _clusterRetriever.clusterQuery(moduleName, name);
      }
    });
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
    _thisManager = new WorkerManagedResource("_aor-this-agent-manager", context, () -> {
      return AgentManager.create(context.getClusterRetriever(), _thisModuleName);
    });
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
      for(String agentName: _agentGraphs.keySet()) {
        m.put(agentName, _thisManager.getResource().getAgentClient(agentName));
      }
      return m;
    });
    _managers = new WorkerManagedResource("__agentManagers", context, () -> {
      return new CloseableConcurrentMap();
    });
    _mirrorAgents = new WorkerManagedResource("__mirrorAgentClients", context, () -> {
      return new CloseableConcurrentMap();
    });
    _depots = new WorkerManagedResource("__foreignDepotClients", context, () -> {
      return new CloseableConcurrentMap();
    });
    _pstates = new WorkerManagedResource("__foreignPStateClients", context, () -> {
      return new CloseableConcurrentMap();
    });
    _mirrorStoreInfo = new WorkerManagedResource("__mirrorStoreInfo", context, () -> {
      return new CloseableConcurrentMap();
    });
    _queries = new WorkerManagedResource("__foreignQueryClients", context, () -> {
      return new CloseableConcurrentMap();
    });
  }

  @Override
  public void close() throws IOException {
    _thisManager.close();
    for(WorkerManagedResource resource: _objects.values()) {
      resource.close();
    }
    _agents.close();
    _mirrorAgents.close();
    _depots.close();
    _pstates.close();
    _mirrorStoreInfo.close();
    _queries.close();
  }
}
