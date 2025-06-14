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
  AgentStream stream(AgentInvoke invoke, String node);
  <T> AgentStream stream(AgentInvoke invoke, String node, RamaVoidFunction2<List, T> callback);
  // TODO: methods to get trace info
  //  - needs to be paginated
  //  - should be exact same as query topology
}
