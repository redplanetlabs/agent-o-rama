package com.rpl.agent.chatbot;

import com.rpl.agentorama.*;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;

/**
 * Chatbot Agent Module using agent-o-rama framework.
 *
 * <p>This module implements a conversational agent with memory management and automatic
 * summarization. It demonstrates conversation persistence across multiple turns and intelligent
 * memory management to handle long conversations.
 */
public class ChatbotModule extends AgentsModule {

  @Override
  protected void defineAgents(AgentsTopology topology) {
    // Declare OpenAI API key from environment
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

    // Declare OpenAI streaming chat model builder
    topology.declareAgentObjectBuilder(
        "openai",
        (AgentObjectSetup setup) -> {
          String apiKey = (String) setup.getAgentObject("openai-api-key");
          return OpenAiStreamingChatModel.builder().apiKey(apiKey).modelName("gpt-4o-mini").build();
        });

    // Declare key-value store for conversation memory
    topology.declareKeyValueStore("$$kv-store", Long.class, Object.class);

    // Create the main chatbot agent with chat and summarize nodes
    topology
        .newAgent("ChatbotAgent")
        .node("chat", "summarize", new ChatNodeFunction())
        .node("summarize", null, new SummarizeNodeFunction());
  }
}
