package com.rpl.agentorama;

public enum NestedOpType {
  STORE_READ,
  STORE_WRITE,
  DB_READ,
  DB_WRITE,
  MODEL_CALL,
  TOOL_CALL,
  AGENT_CALL,
  HUMAN_INPUT,
  OTHER
}
