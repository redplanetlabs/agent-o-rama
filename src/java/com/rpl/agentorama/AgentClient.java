package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import com.rpl.agentorama.ops.*;

public interface AgentClient extends Closeable {
  interface StreamCallback<T> {
    void onUpdate(List<T> allChunks, List<T> newChunks, boolean isReset, boolean isComplete);
  }

  interface StreamAllCallback<T> {
    void onUpdate(Map<Long, List<T>> allChunks, Map<Long, List<T>> newChunks, Set<Long> resetInvokeIds, boolean isComplete);
  }

  <T> T invoke(Object... args);
  <T> CompletableFuture<T> invokeAsync(Object... args);
  AgentInvoke initiate(Object... args);
  CompletableFuture<AgentInvoke> initiateAsync(Object... args);

  <T> T fork(AgentInvoke invoke, Map<Long, List> nodeInvokeIdToNewArgs);
  <T> CompletableFuture<T> forkAsync(AgentInvoke invoke, Map<Long, List> nodeInvokeIdToNewArgs);
  AgentInvoke initiateFork(AgentInvoke invoke, Map<Long, List> nodeInvokeIdToNewArgs);
  CompletableFuture<AgentInvoke> initiateForkAsync(AgentInvoke invoke, Map<Long, List> nodeInvokeIdToNewArgs);

  <T> T agentResult(AgentInvoke invoke);
  <T> CompletableFuture<T> agentResultAsync(AgentInvoke invoke);
  AgentStream stream(AgentInvoke invoke, String node);
  <T> AgentStream stream(AgentInvoke invoke, String node, StreamCallback<T> callback);
  AgentStreamByInvoke streamAll(AgentInvoke invoke, String node);
  <T> AgentStreamByInvoke streamAll(AgentInvoke invoke,
                                    String node,
                                    StreamAllCallback<T> callback);
}
