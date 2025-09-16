package com.rpl.agent.basic;

import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentNodeFunction;

/**
 * Node function that processes input and creates a welcome message.
 *
 * <p>This function demonstrates:
 *
 * <ul>
 *   <li>Implementing AgentNodeFunction interface
 *   <li>Processing agent node input arguments
 *   <li>Creating and returning final results via result! method
 *   <li>Basic string manipulation and formatting
 * </ul>
 *
 * <p>The function takes a user name as input and returns a personalized welcome message.
 */
public class ProcessNodeFunction implements AgentNodeFunction {

  @Override
  public void invoke(AgentNode agentNode, Object... args) {
    // Extract user name from arguments (corresponds to the value in agent-invoke)
    String userName = (String) args[0];

    // Create a welcome message for the user
    String result = "Welcome to agent-o-rama, " + userName + "!";

    // Return the final result
    agentNode.result(result);
  }
}
