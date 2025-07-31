package com.rpl.agentorama.impl;

import java.io.IOException;

import com.rpl.agentorama.AgentObjectSetup;
import com.rpl.rama.integration.*;

import clojure.lang.IFn;
import java.util.*;

public class AgentDeclaredObjectsTaskGlobal implements TaskGlobalObject {
  public static ThreadLocal<Long> ACQUIRE_TIMEOUT_MILLIS = new ThreadLocal<>();

  Map<String, Map<String, Object>> _builders;
  Map<String, WorkerManagedResource> _objects;


  public AgentDeclaredObjectsTaskGlobal(Map<String, Map<String, Object>> builders) {
    _builders = builders;
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

  private static Object makeObject(String name, IFn afn, AgentObjectSetup setup, boolean autoTracing) {
    Object o = afn.invoke(setup);
    return autoTracing ? AORHelpers.WRAP_AGENT_OBJECT.invoke(name, o) : o;
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
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
  }

  @Override
  public void close() throws IOException {
    for(WorkerManagedResource resource: _objects.values()) {
      resource.close();
    }
  }
}
