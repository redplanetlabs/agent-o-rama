package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

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


  AgentStep nextStep(AgentInvoke invoke);
  CompletableFuture<AgentStep> nextStepAsync(AgentInvoke invoke);

  <T> T result(AgentInvoke invoke);
  <T> CompletableFuture<T> resultAsync(AgentInvoke invoke);

  AgentStream stream(AgentInvoke invoke, String node);
  <T> AgentStream stream(AgentInvoke invoke, String node, StreamCallback<T> callback);
  AgentStream streamSpecific(AgentInvoke invoke, String node, long nodeInvokeId);
  <T> AgentStream streamSpecific(AgentInvoke invoke, String node, long nodeInvokeId, StreamCallback<T> callback);
  AgentStreamByInvoke streamAll(AgentInvoke invoke, String node);
  <T> AgentStreamByInvoke streamAll(AgentInvoke invoke,
                                    String node,
                                    StreamAllCallback<T> callback);
  List<HumanInputRequest> pendingHumanInputs(AgentInvoke invoke);
  CompletableFuture<List<HumanInputRequest>> pendingHumanInputsAsync(AgentInvoke invoke);
  void provideHumanInput(HumanInputRequest request, String response);
  CompletableFuture<Void> provideHumanInputAsync(HumanInputRequest request, String response);
}
