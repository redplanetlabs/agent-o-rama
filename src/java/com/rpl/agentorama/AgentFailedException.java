package com.rpl.agentorama;

/**
 * Exception for an agent execution that failed.
 *
 * The message describes the failure, such as "Max retry limit exceeded", and
 * when nodes threw, ends with the first line of the last exception thrown, as
 * in "Max retry limit exceeded (last failure: ...)". It carries no cause.
 *
 * Asynchronous methods complete their future exceptionally with this
 * exception. Synchronous methods such as {@code invoke} and {@code result}
 * wait on that future, so they throw an {@code ExecutionException} (not
 * declared) with this exception as its cause. Agent clients used inside a node
 * throw it directly.
 *
 * Example:
 * <pre>{@code
 * try {
 *   String result = client.invoke("Hello world");
 * } catch (Exception e) {
 *   Throwable failure = e instanceof ExecutionException ? e.getCause() : e;
 *   if (failure instanceof AgentFailedException) {
 *     System.err.println("Agent failed: " + failure.getMessage());
 *   } else {
 *     throw e;
 *   }
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
