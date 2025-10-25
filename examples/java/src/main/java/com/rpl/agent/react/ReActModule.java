package com.rpl.agent.react;

import com.rpl.agentorama.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.*;

/**
 * ReAct (Reasoning and Acting) Agent Module using agent-o-rama framework.
 *
 * <p>This module implements a conversational agent that can reason about user queries and take
 * actions using web search tools. It demonstrates the ReAct pattern where the agent alternates
 * between reasoning about what to do and taking actions.
 */
public class ReActModule extends AgentModule {

  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));
    topology.declareAgentObject("tavily-api-key", System.getenv("TAVILY_API_KEY"));

    topology.declareAgentObjectBuilder(
        "openai",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("openai-api-key");
          return OpenAiChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
        });

    topology.declareAgentObjectBuilder(
        "tavily",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("tavily-api-key");
          return TavilyWebSearchEngine.builder()
              .apiKey(apiKey)
              .excludeDomains(Arrays.asList("en.wikipedia.org"))
              .build();
        });

    topology.newToolsAgent("tools", ToolsFactory.createTools());

    topology.newAgent("ReActAgent")
            .node("chat", "chat", (AgentNode agentNode, List<ChatMessage> messages) -> {
              ChatModel openai = agentNode.getAgentObject("openai");
              AgentClient tools = agentNode.getAgentClient("tools");

              ChatRequest request =
                  ChatRequest.builder()
                      .messages(messages)
                      .toolSpecifications(ToolsFactory.createToolSpecifications())
                      .build();
              ChatResponse response = openai.chat(request);
              AiMessage aiMessage = response.aiMessage();
              List<ToolExecutionRequest> toolCalls = aiMessage.toolExecutionRequests();

              if (toolCalls != null && !toolCalls.isEmpty()) {
                List<ToolExecutionResultMessage> toolResults = tools.invoke(toolCalls);

                List<ChatMessage> nextMessages = new ArrayList<>(messages);
                nextMessages.add(aiMessage);
                nextMessages.addAll(toolResults);
                agentNode.emit("chat", nextMessages);
              } else {
                agentNode.result(aiMessage.text());
              }
            });
  }
}
