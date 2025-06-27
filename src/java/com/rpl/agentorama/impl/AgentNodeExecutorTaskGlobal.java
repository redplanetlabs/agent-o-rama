package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.integration.*;

public class AgentNodeExecutorTaskGlobal implements TaskGlobalObject {
  WorkerManagedResource<ExecutorService> _execServResource;

  // TODO: <<<<>>>> have it remove from here on exception during execution
  Set<Long> _runningInvokeIds;

  public void submitTask(long invokeId, clojure.lang.AFn f) {
    _runningInvokeIds.add(invokeId);
    _execServResource.getResource().submit((Runnable) f);
  }

  public Set<Long> getRunningInvokeIds() {
    return _runningInvokeIds;
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _execServResource = new WorkerManagedResource("agentVirtualThreads", context, () -> Executors.newVirtualThreadPerTaskExecutor());
    _runningInvokeIds = new HashSet();
  }

  public void removeTrackedInvokeId(long invokeId) {
    _runningInvokeIds.remove(invokeId);
  }

  @Override
  public void gainedLeadership() {
    _runningInvokeIds = new HashSet();
  }

  @Override
  public void close() throws IOException {
    _execServResource.close();
  }
}
