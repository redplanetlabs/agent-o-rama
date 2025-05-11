package com.rpl.agentorama;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import com.rpl.agentorama.ops.*;

public interface AgentClient {
  <T> T invoke(Object... args);
  <T> CompletableFuture<T> invokeAsync(Object... args);
  AgentInvoke initiate(Object... args);
  CompletableFuture<AgentInvoke> initiateAsync(Object... args);
  <T> T agentResult(AgentInvoke invoke);
  <T> CompletableFuture<T> agentResultAsync(AgentInvoke invoke);
  AgentStream stream(AgentInvoke invoke, String node, String asyncInvokeName);
  <T> AgentStream stream(AgentInvoke invoke, String node, String asyncInvokeName, RamaVoidFunction2<List, T> callback);
  AgentStream streamInstance(AgentInvoke invoke, String node, String asyncInvokeName, long nodeInvokeId);
  <T> AgentStream streamInstance(AgentInvoke invoke, String node, String asyncInvokeName, long nodeInvokeId, RamaVoidFunction2<List, T> callback);
  // TODO: methods to get trace info
  //  - needs to be paginated
}
