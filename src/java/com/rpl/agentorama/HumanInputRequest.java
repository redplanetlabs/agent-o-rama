package com.rpl.agentorama;

import java.util.UUID;

/**
 * Represents a request for human input during agent execution.
 * 
 * When an agent calls {@link AgentNode#getHumanInput(String)} within a node function, it creates
 * a human input request that must be responded to before the agent can continue.
 * 
 * Example:
 * <pre>{@code
 * AgentStep step = client.nextStep(invoke);
 * if (step instanceof HumanInputRequest) {
 *   HumanInputRequest request = (HumanInputRequest) step;
 *   client.provideHumanInput(request, "Yes, proceed");
 * }
 * }</pre>
 */
public interface HumanInputRequest extends AgentStep {
  /**
   * Gets the name of the node that requested human input.
   * 
   * @return the node name
   */
  String getNode();
  
  /**
   * Gets the unique ID of the node invocation that requested human input.
   * 
   * @return the node invoke ID
   */
  UUID getNodeInvokeId();
  
  /**
   * Gets the prompt text shown to the human.
   * 
   * @return the prompt text
   */
  String getPrompt();
}
