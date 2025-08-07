package com.rpl.agentorama;

public class AgentFailedException extends RuntimeException {
  public AgentFailedException(String message) {
    super(message);
  }

  public AgentFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
