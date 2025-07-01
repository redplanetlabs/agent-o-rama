package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.integration.*;

public class AgentNodeExecutorTaskGlobal implements TaskGlobalObject {
  WorkerManagedResource<ExecutorService> _execServResource;
  ConcurrentHashMap<Long, Object> _runningInvokeIds;

  public void submitTask(long invokeId, clojure.lang.AFn f) {
    _runningInvokeIds.put(invokeId, true);
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

  @Override
  public void gainedLeadership() {
    _runningInvokeIds = new ConcurrentHashMap();
  }

  @Override
  public void close() throws IOException {
    _execServResource.close();
  }
}
