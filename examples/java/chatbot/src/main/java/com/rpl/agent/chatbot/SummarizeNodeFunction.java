package com.rpl.agent.chatbot;

import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.ops.RamaVoidFunction5;
import com.rpl.agentorama.store.KeyValueStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Summarize node function that creates or updates conversation summaries.
 *
 * <p>This function:
 *
 * <ol>
 *   <li>Takes the conversation messages and creates a summarization prompt
 *   <li>Calls OpenAI to generate or extend the conversation summary
 *   <li>Stores the new summary with reduced message history
 *   <li>Returns the original AI message as the result
 * </ol>
 */
public class SummarizeNodeFunction
    implements RamaVoidFunction5<
        AgentNode, String, List<ChatMessage>, AiMessage, Map<String, Object>> {

  @Override
  public void invoke(
      AgentNode agentNode,
      String summary,
      List<ChatMessage> messages,
      AiMessage aiMessage,
      Map<String, Object> config) {
    try {
      // Get dependencies
      ChatModel openai = agentNode.getAgentObject("openai");
      KeyValueStore<Long, Object> store = agentNode.getStore("$$kv-store");

      // Get thread ID from config
      Long threadId = (Long) config.get("thread-id");
      if (threadId == null) {
        throw new IllegalArgumentException("thread-id is required in config");
      }

      // Create summarization prompt
      List<ChatMessage> chatMessages = new ArrayList<>(messages);

      String summaryPrompt;
      if (summary != null && !summary.isEmpty()) {
        summaryPrompt =
            String.format(
                "This is summary of the conversation to date: %s\n\n"
                    + "Extend the summary by taking into account the new messages above.",
                summary);
      } else {
        summaryPrompt = "Create a summary of the conversation above.";
      }

      chatMessages.add(UserMessage.from(summaryPrompt));

      // Call OpenAI to generate summary
      ChatRequest request = ChatRequest.builder().messages(chatMessages).build();
      ChatResponse response = openai.chat(request);
      String newSummary = response.aiMessage().text();

      // Create reduced message list (keep only the last 2 messages)
      List<ChatMessage> newMessages = new ArrayList<>();
      int messagesToKeep = Math.min(2, messages.size());
      if (messagesToKeep > 0) {
        int startIndex = messages.size() - messagesToKeep;
        newMessages.addAll(messages.subList(startIndex, messages.size()));
      }

      // Store updated conversation state
      Map<String, Object> newCheckpoint = Map.of("messages", newMessages, "summary", newSummary);
      store.put(threadId, newCheckpoint);

      // Return the original AI message
      agentNode.result(Map.of("messages", List.of(aiMessage)));

    } catch (Exception e) {
      throw new RuntimeException("Error in summarize node: " + e.getMessage(), e);
    }
  }
}
