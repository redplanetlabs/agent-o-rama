package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.integration.*;

import clojure.lang.ILookup;
import clojure.lang.Keyword;

public class AgentNodeExecutorTaskGlobal implements TaskGlobalObject {
  private static final Keyword UUID_KW = Keyword.intern(null, "uuid");

  WorkerManagedResource<ExecutorService> _execServResource;
  ConcurrentHashMap<Long, List> _runningInvokeIds;

  public void submitTask(long invokeId, clojure.lang.AFn f) {
    _runningInvokeIds.put(invokeId, Arrays.asList());
    Runnable wrappedTask = () -> {
      try {
        f.run();
      } catch (Throwable t) {
        _runningInvokeIds.remove(invokeId);
        throw t;
      }
    };
    _execServResource.getResource().submit(wrappedTask);
  }

  public Set<Long> getRunningInvokeIds() {
    return new HashSet(_runningInvokeIds.keySet());
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _execServResource = new WorkerManagedResource("agentVirtualThreads", context, () -> Executors.newVirtualThreadPerTaskExecutor());
    _runningInvokeIds = new ConcurrentHashMap();
  }

  public void removeTrackedInvokeId(long invokeId) {
    _runningInvokeIds.remove(invokeId);
  }

  public void putHumanFuture(long invokeId, Object request, CompletableFuture cf) {
    _runningInvokeIds.put(invokeId, Arrays.asList(request, cf));
  }

  public CompletableFuture getHumanFuture(long invokeId, String uuid) {
    List tuple = _runningInvokeIds.get(invokeId);
    if(tuple!=null && !tuple.isEmpty()) {
      ILookup m = (ILookup) tuple.get(0);
      if(m.valAt(UUID_KW).equals(uuid)) {
        return (CompletableFuture) tuple.get(1);
      }
    }
    return null;
  }

  public Object getHumanRequest(long invokeId) {
    List tuple = _runningInvokeIds.get(invokeId);
    if(tuple!=null && !tuple.isEmpty()) return tuple.get(0);
    return null;
  }

  @Override
  public void gainedLeadership() {
    _runningInvokeIds = new ConcurrentHashMap();
  }

  @Override
  public void close() throws IOException {
    _execServResource.close();
  }
}
