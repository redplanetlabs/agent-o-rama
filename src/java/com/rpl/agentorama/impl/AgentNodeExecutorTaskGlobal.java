package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.integration.*;

public class AgentNodeExecutorTaskGlobal implements TaskGlobalObject {
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

  public void putHumanFuture(long invokeId, String uuid, CompletableFuture cf) {
    _runningInvokeIds.put(invokeId, Arrays.asList(uuid, cf));
  }

  public CompletableFuture getHumanFuture(long invokeId, String uuid) {
    List tuple = _runningInvokeIds.get(invokeId);
    if(tuple!=null && !tuple.isEmpty() && tuple.get(0).equals(uuid)) {
      return (CompletableFuture) tuple.get(1);
    } else return null;
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
