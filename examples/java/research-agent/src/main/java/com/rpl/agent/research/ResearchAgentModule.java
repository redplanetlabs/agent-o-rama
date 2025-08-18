package com.rpl.agent.research;

import com.rpl.agent.research.model.*;
import com.rpl.agent.research.service.*;
import com.rpl.agentorama.*;
import com.rpl.agentorama.ops.RamaVoidFunction3;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.util.List;
import java.util.Map;

public class ResearchAgentModule extends AgentsModule {

  @Override
  protected void defineAgents(AgentsTopology topology) {
    // Declare API keys from environment
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));
    topology.declareAgentObject("tavily-api-key", System.getenv("TAVILY_API_KEY"));

    // Declare OpenAI chat model
    topology.declareAgentObjectBuilder(
        "openai",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("openai-api-key");
          return OpenAiChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
        });

    // Declare Tavily web search engine
    topology.declareAgentObjectBuilder(
        "tavily",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("tavily-api-key");
          return TavilyWebSearchEngine.builder()
              .apiKey(apiKey)
              .excludeDomains(List.of("en.wikipedia.org"))
              .build();
        });

    // Create a simple research agent for now
    topology.newAgent("researcher").node("start", null, new StartNode());
  }

  // Simple node implementation
  static class StartNode implements RamaVoidFunction3<AgentNode, String, Map<String, Object>> {
    @Override
    public void invoke(AgentNode agentNode, String humanFeedback, Map<String, Object> options) {
      try {
        ResearchOptions opts = ResearchOptions.fromMap(options);
        ChatModel openai = agentNode.getAgentObject("openai");

        String prompt =
            String.format(
                "You are a research assistant. Please provide a brief analysis of the topic: %s",
                opts.getTopic());

        ChatRequest request =
            ChatRequest.builder().messages(List.of(SystemMessage.from(prompt))).build();

        String response = openai.chat(request).aiMessage().text();
        agentNode.result("Research Analysis:\n\n" + response);
      } catch (Exception e) {
        throw new RuntimeException("Error in research", e);
      }
    }
  }
}
