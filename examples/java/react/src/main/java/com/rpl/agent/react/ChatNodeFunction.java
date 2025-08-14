package com.rpl.agent.react;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;

/**
 * Chat node function that implements the core ReAct reasoning loop.
 *
 * <p>This function handles the conversation flow by:
 *
 * <ol>
 *   <li>Taking a list of chat messages as input
 *   <li>Calling the OpenAI model with available tools
 *   <li>If the model wants to use tools, executing them and continuing the conversation
 *   <li>If the model provides a final response, returning it as the result
 * </ol>
 */
public class ChatNodeFunction implements RamaVoidFunction2<AgentNode, List<ChatMessage>> {

  @Override
  public void invoke(AgentNode agentNode, List<ChatMessage> messages) {
    try {
      // Get the OpenAI model and tools agent client
      ChatModel openai = agentNode.getAgentObject("openai");
      AgentClient tools = agentNode.getAgentClient("tools");

      // Create chat request with tools
      ChatRequest request =
          ChatRequest.builder()
              .messages(messages)
              .toolSpecifications(ToolsFactory.createToolSpecifications())
              .build();

      ChatResponse response = openai.chat(request);
      AiMessage aiMessage = response.aiMessage();
      List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();

      if (toolCalls != null && !toolCalls.isEmpty()) {
        // Execute tool calls directly using the tools agent client
        List<ToolExecutionResultMessage> toolResults = tools.invoke(toolCalls);

        // Create next set of messages including AI message and tool
        // results
        List<ChatMessage> nextMessages = new ArrayList<>(messages);
        nextMessages.add(aiMessage);
        nextMessages.addAll(toolResults);

        // Continue the conversation with updated messages
        agentNode.emit("chat", nextMessages);
      } else {
        // No tools needed, return the final response
        agentNode.result(aiMessage.text());
      }

    } catch (Exception e) {
      throw new RuntimeException("Error in chat node: " + e.getMessage(), e);
    }
  }
}
