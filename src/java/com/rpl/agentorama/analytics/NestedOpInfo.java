package com.rpl.agentorama.analytics;

import java.util.Map;

import com.rpl.agentorama.NestedOpType;

public interface NestedOpInfo {
  long getStartTimeMillis();
  long getFinishTimeMillis();
  NestedOpType getType();
  Map<String, Object> getInfo();
}
