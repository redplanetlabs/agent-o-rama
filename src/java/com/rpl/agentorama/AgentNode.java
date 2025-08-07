package com.rpl.agentorama;

import com.rpl.agentorama.impl.*;
import com.rpl.agentorama.store.Store;
import java.util.Map;

public interface AgentNode extends IFetchAgentObject, IFetchAgentClient {
  void emit(String node, Object... args);
  void result(Object arg);
  <T extends Store> T getStore(String name);
  void streamChunk(Object chunk);
  void recordNestedOp(NestedOpType nestedOpType, long startTimeMillis, long finishTimeMillis, Map<String, Object> info);
  String getHumanInput(String prompt);

}
