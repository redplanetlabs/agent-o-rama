package com.rpl.agentorama.impl;

import java.io.IOException;
import java.util.concurrent.*;
import java.util.*;

import com.rpl.rama.integration.*;

import clojure.lang.ILookup;
import clojure.lang.Keyword;

public class AgentNodeExecutorTaskGlobal implements TaskGlobalObject {
  private static final Keyword UUID_KW = Keyword.intern(null, "uuid");

  // A node invoke id is reused across retry attempts (a :continue retry re-runs
  // an incomplete node under the same id), so each entry records the attempt
  // it tracks. An attempt only ever removes or replaces its own entry, so a
  // late finish from an invalidated attempt can't untrack the current one.
  private static final class Attempt {
    final long retryNum;
    final Object humanRequest;
    final CompletableFuture humanFuture;

    Attempt(long retryNum, Object humanRequest, CompletableFuture humanFuture) {
      this.retryNum = retryNum;
      this.humanRequest = humanRequest;
      this.humanFuture = humanFuture;
    }
  }

  WorkerManagedResource<ExecutorService> _execServResource;
  ConcurrentHashMap<UUID, Attempt> _runningInvokeIds;

  // type is opaque here - only passed into clojure
  private Object _throttler;

  private static final ThreadLocal<Object> LOG_THROTTLER = new ThreadLocal<>();

  // invokeId is null for work that isn't a node execution; it is not tracked
  // and retryNum is ignored
  public void submitTask(UUID invokeId, long retryNum, clojure.lang.AFn f) {
    if(invokeId!=null) _runningInvokeIds.put(invokeId, new Attempt(retryNum, null, null));
    Runnable wrappedTask = () -> {
      try {
	      LOG_THROTTLER.set(_throttler);
        f.run();
	      LOG_THROTTLER.remove();
      } catch (Throwable t) {
        if(invokeId!=null) removeTrackedInvokeId(invokeId, retryNum);
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

  // Removes the entry only if it tracks the given attempt.
  public void removeTrackedInvokeId(UUID invokeId, long retryNum) {
    _runningInvokeIds.computeIfPresent(invokeId, (id, a) -> a.retryNum==retryNum ? null : a);
  }

  // Removes the entry whatever attempt it tracks.
  public void removeTrackedInvokeId(UUID invokeId) {
    _runningInvokeIds.remove(invokeId);
  }

  // Doesn't replace an entry of another attempt: a stale attempt asking for
  // input must not hide the current attempt's pending request.
  public void putHumanFuture(UUID invokeId, long retryNum, Object request, CompletableFuture cf) {
    _runningInvokeIds.compute(invokeId, (id, a) ->
      (a==null || a.retryNum==retryNum) ? new Attempt(retryNum, request, cf) : a);
  }

  public CompletableFuture getHumanFuture(UUID invokeId, String uuid) {
    Attempt a = _runningInvokeIds.get(invokeId);
    if(a!=null && a.humanRequest!=null) {
      ILookup m = (ILookup) a.humanRequest;
      if(m.valAt(UUID_KW).equals(uuid)) {
        return a.humanFuture;
      }
    }
    return null;
  }

  public Object getHumanRequest(UUID invokeId) {
    Attempt a = _runningInvokeIds.get(invokeId);
    return a==null ? null : a.humanRequest;
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
