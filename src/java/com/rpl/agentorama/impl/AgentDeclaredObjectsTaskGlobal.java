package com.rpl.agentorama.impl;

import java.io.Closeable;
import java.io.IOException;

import com.rpl.agentorama.AgentObjectSetup;
import com.rpl.rama.integration.*;
import com.rpl.rama.ops.RamaFunction0;

import clojure.lang.IFn;
import java.util.*;

public class AgentDeclaredObjectsTaskGlobal implements TaskGlobalObject {
  Map<String, IFn> _builders;
  Map<String, Object> _objects;
  Map<String, Object> _wrapped;
  Map<String, WorkerManagedResource> _shared;
  Set<String> _inProgress;
  // - this leaks memory in test environment / REPL that's creating/tearing down multiple clusters,
  // but not in production since task globals are only set up once in a real worker
  // - the leaking shouldn't matter is it should take a very long time to cause any issues
  static final IdentityHashMap _sharedUnderlying = new IdentityHashMap();

  public AgentDeclaredObjectsTaskGlobal(Map<String, IFn> builders) {
    _builders = builders;
    _objects = new HashMap();
    _shared = new HashMap();
    _inProgress = new HashSet();
  }

  public Map<String, Object> getAgentObjects() {
    return _wrapped;
  }

  private void buildObject(String name, TaskGlobalContext context) {
    _inProgress.add(name);
    Object obj = _builders.get(name).invoke(new AgentObjectSetup() {
      @Override
      public Object getAgentObject(String otherName) {
        if(!_wrapped.containsKey(otherName)) {
          if(_inProgress.contains(otherName))
            throw new RuntimeException("Detected cycle when building agent object " + name + " -> " + otherName);
          buildObject(otherName, context);
        }
       return _wrapped.get(otherName);
      }

      @Override
      public Object getSharedScopedInstance(String key, RamaFunction0 builder) {
        String resourceId = "" + name.length() + ":" + name + key.length() + ":" + key;
        WorkerManagedResource resource = new WorkerManagedResource(
                                            resourceId,
                                            context,
                                            () -> {
                                              Object shared = builder.invoke();
                                              synchronized(_sharedUnderlying) {
                                                _sharedUnderlying.put(shared, null);
                                              }
                                              return shared;
                                            });
        _shared.put(resourceId, resource);
        return resource.getResource();
      }

      @Override
      public String getObjectName() {
        return name;
      }
    });
    _objects.put(name, obj);
    _wrapped.put(name, AORHelpers.WRAP_AGENT_OBJECT.invoke(obj));
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    for(String name: _builders.keySet()) {
      if(!_objects.containsKey(name)) buildObject(name, context);
    }
  }

  @Override
  public void close() throws IOException {
    for(WorkerManagedResource resource: _shared.values()) resource.close();
    for(Object o: _objects.values()) {
      if(!_sharedUnderlying.containsKey(o) && o instanceof Closeable) {
        ((Closeable) o).close();
      }
    }
  }
}
