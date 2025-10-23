package com.rpl.agentorama;

/**
 * Exception thrown when an agent execution fails.
 * 
 * This exception is thrown when an agent encounters an error during execution
 * and cannot complete its task. It wraps the underlying cause of the failure.
 * 
 * Example:
 * <pre>{@code
 * try {
 *   String result = client.invoke("Hello world");
 * } catch (AgentFailedException e) {
 *   System.err.println("Agent failed: " + e.getMessage());
 *   e.getCause().printStackTrace();
 * }
 * }</pre>
 */
public class AgentFailedException extends RuntimeException {
  /**
   * Creates a new AgentFailedException with the specified message.
   * 
   * @param message the detail message
   */
  public AgentFailedException(String message) {
    super(message);
  }

  /**
   * Creates a new AgentFailedException with the specified message and cause.
   * 
   * @param message the detail message
   * @param cause the cause of the failure
   */
  public AgentFailedException(String message, Throwable cause) {
    super(message, cause);
  }
}
