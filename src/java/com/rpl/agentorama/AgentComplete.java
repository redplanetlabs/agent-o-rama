package com.rpl.agentorama;

/**
 * Represents the completion of an agent execution with a result.
 * 
 * When an agent execution completes successfully, it returns an AgentComplete
 * containing the final result value.
 * 
 * Example:
 * <pre>{@code
 * AgentStep step = client.nextStep(invoke);
 * if (step instanceof AgentComplete) {
 *   AgentComplete<String> complete = (AgentComplete<String>) step;
 *   String result = complete.getResult();
 *   System.out.println("Agent result: " + result);
 * }
 * }</pre>
 * 
 * @param <T> the type of the result value
 */
public interface AgentComplete<T> extends AgentStep {
  /**
   * Gets the final result of the agent execution.
   * 
   * @return the agent's result value
   */
  T getResult();
}
