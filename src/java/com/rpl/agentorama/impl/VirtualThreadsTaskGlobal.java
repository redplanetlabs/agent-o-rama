package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;

import com.rpl.rama.integration.*;

public class VirtualThreadsTaskGlobal implements TaskGlobalObject {
  WorkerManagedResource<ExecutorService> _execServResource;

  public void submitTask(clojure.lang.AFn f) {
    _execServResource.getResource().submit((Runnable) f);
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _execServResource = new WorkerManagedResource("agentVirtualThreads", context, () -> Executors.newVirtualThreadPerTaskExecutor());
  }

  @Override
  public void close() throws IOException {
    _execServResource.close();
  }
}
