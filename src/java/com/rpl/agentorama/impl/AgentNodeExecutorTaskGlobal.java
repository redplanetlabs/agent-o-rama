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
  ConcurrentHashMap<UUID, List> _runningInvokeIds;

  // type is opaque here - only passed into clojure
  private Object _throttler;

  private static final ThreadLocal<Object> LOG_THROTTLER = new ThreadLocal<>();

  public void submitTask(UUID invokeId, clojure.lang.AFn f) {
    if(invokeId!=null) _runningInvokeIds.put(invokeId, Arrays.asList());
    Runnable wrappedTask = () -> {
      try {
	      LOG_THROTTLER.set(_throttler);
        f.run();
	      LOG_THROTTLER.remove();
      } catch (Throwable t) {
        if(invokeId!=null) _runningInvokeIds.remove(invokeId);
        throw t;
      }
    };
    _execServResource.getResource().submit(wrappedTask);
  }

  public Set<UUID> getRunningInvokeIds() {
    return new HashSet(_runningInvokeIds.keySet());
  }

  @Override
  public void prepareForTask(int taskId, TaskGlobalContext context) {
    _execServResource = new WorkerManagedResource("agentVirtualThreads", context, () -> Executors.newVirtualThreadPerTaskExecutor());
    _runningInvokeIds = new ConcurrentHashMap();
    _throttler = context.getLogThrottler();
  }

  public void removeTrackedInvokeId(UUID invokeId) {
    _runningInvokeIds.remove(invokeId);
  }

  public void putHumanFuture(UUID invokeId, Object request, CompletableFuture cf) {
    _runningInvokeIds.put(invokeId, Arrays.asList(request, cf));
  }

  public CompletableFuture getHumanFuture(UUID invokeId, String uuid) {
    List tuple = _runningInvokeIds.get(invokeId);
    if(tuple!=null && !tuple.isEmpty()) {
      ILookup m = (ILookup) tuple.get(0);
      if(m.valAt(UUID_KW).equals(uuid)) {
        return (CompletableFuture) tuple.get(1);
      }
    }
    return null;
  }

  public Object getHumanRequest(UUID invokeId) {
    List tuple = _runningInvokeIds.get(invokeId);
    if(tuple!=null && !tuple.isEmpty()) return tuple.get(0);
    return null;
  }

  public static Object getLogThrottler() {
    return LOG_THROTTLER.get();
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
