package com.rpl.agentorama;

import java.io.Closeable;
import java.util.*;
import java.util.concurrent.CompletableFuture;

public interface AgentClient extends Closeable {
  interface StreamCallback<T> {
    void onUpdate(List<T> allChunks, List<T> newChunks, boolean isReset, boolean isComplete);
  }

  interface StreamAllCallback<T> {
    void onUpdate(Map<UUID, List<T>> allChunks, Map<UUID, List<T>> newChunks, Set<UUID> resetInvokeIds, boolean isComplete);
  }

  <T> T invoke(Object... args);
  <T> CompletableFuture<T> invokeAsync(Object... args);
  <T> T invokeWithContext(AgentContext context, Object... args);
  <T> CompletableFuture<T> invokeWithContextAsync(AgentContext context, Object... args);
  AgentInvoke initiate(Object... args);
  CompletableFuture<AgentInvoke> initiateAsync(Object... args);
  AgentInvoke initiateWithContext(AgentContext context, Object... args);
  CompletableFuture<AgentInvoke> initiateWithContextAsync(AgentContext context, Object... args);

  <T> T fork(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);
  <T> CompletableFuture<T> forkAsync(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);
  AgentInvoke initiateFork(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);
  CompletableFuture<AgentInvoke> initiateForkAsync(AgentInvoke invoke, Map<UUID, List> nodeInvokeIdToNewArgs);


  AgentStep nextStep(AgentInvoke invoke);
  CompletableFuture<AgentStep> nextStepAsync(AgentInvoke invoke);

  <T> T result(AgentInvoke invoke);
  <T> CompletableFuture<T> resultAsync(AgentInvoke invoke);

  boolean isAgentInvokeComplete(AgentInvoke invoke);
  void setMetadata(AgentInvoke invoke, String key, int value);
  void setMetadata(AgentInvoke invoke, String key, long value);
  void setMetadata(AgentInvoke invoke, String key, float value);
  void setMetadata(AgentInvoke invoke, String key, double value);
  void setMetadata(AgentInvoke invoke, String key, String value);
  void setMetadata(AgentInvoke invoke, String key, boolean value);
  void removeMetadata(AgentInvoke invoke, String key);
  Map<String, Object> getMetadata(AgentInvoke invoke);

  AgentStream stream(AgentInvoke invoke, String node);
  <T> AgentStream stream(AgentInvoke invoke, String node, StreamCallback<T> callback);
  AgentStream streamSpecific(AgentInvoke invoke, String node, UUID nodeInvokeId);
  <T> AgentStream streamSpecific(AgentInvoke invoke, String node, UUID nodeInvokeId, StreamCallback<T> callback);
  AgentStreamByInvoke streamAll(AgentInvoke invoke, String node);
  <T> AgentStreamByInvoke streamAll(AgentInvoke invoke,
                                    String node,
                                    StreamAllCallback<T> callback);
  List<HumanInputRequest> pendingHumanInputs(AgentInvoke invoke);
  CompletableFuture<List<HumanInputRequest>> pendingHumanInputsAsync(AgentInvoke invoke);
  void provideHumanInput(HumanInputRequest request, String response);
  CompletableFuture<Void> provideHumanInputAsync(HumanInputRequest request, String response);
}
