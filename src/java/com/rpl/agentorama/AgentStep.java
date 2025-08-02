package com.rpl.agentorama;

/**
 * Represents the result of invoking {@link AgentClient#nextStep(AgentInvoke)} or
 * {@link AgentClient#nextStepAsync(AgentInvoke)}.
 *
 * This is either a {@link HumanInputRequest} (indicating the agent requires input from a human)
 * or an {@link AgentComplete} (indicating the agent has completed its task).
 */
public interface AgentStep {
}
