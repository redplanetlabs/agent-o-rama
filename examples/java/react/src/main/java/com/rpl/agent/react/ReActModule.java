package com.rpl.agent.react;

import com.rpl.agentorama.*;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.util.Arrays;

/**
 * ReAct (Reasoning and Acting) Agent Module using agent-o-rama framework.
 *
 * <p>This module implements a conversational agent that can reason about user queries and take
 * actions using web search tools. It demonstrates the ReAct pattern where the agent alternates
 * between reasoning about what to do and taking actions.
 */
public class ReActModule extends AgentsModule {

  @Override
  protected void defineAgents(AgentsTopology topology) {
    // Declare OpenAI API key from environment
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

    // Declare Tavily API key from environment
    topology.declareAgentObject("tavily-api-key", System.getenv("TAVILY_API_KEY"));

    // Declare OpenAI chat model builder
    topology.declareAgentObjectBuilder(
        "openai",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("openai-api-key");
          return OpenAiChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
        });

    // Declare Tavily web search engine builder
    topology.declareAgentObjectBuilder(
        "tavily",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("tavily-api-key");
          return TavilyWebSearchEngine.builder()
              .apiKey(apiKey)
              .excludeDomains(Arrays.asList("en.wikipedia.org"))
              .build();
        });

    // Create the main ReAct agent
    topology.newAgent("ReActAgent").node("chat", "chat", new ChatNodeFunction());

    // Create tools agent with web search capability
    topology.newToolsAgent("tools", ToolsFactory.createTools());
  }
}
