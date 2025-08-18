package com.rpl.agent.chatbot;

import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.ops.RamaVoidFunction3;
import com.rpl.agentorama.store.KeyValueStore;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Chat node function that handles conversation with memory management.
 *
 * <p>This function:
 *
 * <ol>
 *   <li>Retrieves conversation state from the key-value store
 *   <li>Constructs the full conversation context including summary if present
 *   <li>Calls the OpenAI model to generate a response
 *   <li>Determines whether to summarize (if > 6 messages) or continue conversation
 * </ol>
 */
public class ChatNodeFunction
    implements RamaVoidFunction3<AgentNode, List<ChatMessage>, Map<String, Object>> {

  @SuppressWarnings("unchecked")
  @Override
  public void invoke(AgentNode agentNode, List<ChatMessage> messages, Map<String, Object> config) {
    try {
      // Get dependencies
      ChatModel openai = agentNode.getAgentObject("openai");
      KeyValueStore<Long, Object> store = agentNode.getStore("$$kv-store");

      // Get thread ID from config
      Long threadId = (Long) config.get("thread-id");
      if (threadId == null) {
        throw new IllegalArgumentException("thread-id is required in config");
      }

      // Get conversation checkpoint from store
      Map<String, Object> checkpoint = (Map<String, Object>) store.get(threadId);
      String summary = null;
      List<ChatMessage> storedMessages = new ArrayList<>();

      if (checkpoint != null) {
        summary = (String) checkpoint.get("summary");
        List<Object> rawMessages = (List<Object>) checkpoint.get("messages");
        if (rawMessages != null) {
          // Convert each object to ChatMessage
          for (Object rawMessage : rawMessages) {
            if (rawMessage instanceof ChatMessage) {
              storedMessages.add((ChatMessage) rawMessage);
            }
          }
        }
      }

      // Construct full conversation context
      List<ChatMessage> chatMessages = new ArrayList<>();

      // Add summary as system message if present
      if (summary != null && !summary.isEmpty()) {
        chatMessages.add(SystemMessage.from("Summary of conversation earlier: " + summary));
      }

      // Add stored messages
      chatMessages.addAll(storedMessages);

      // Add current messages
      chatMessages.addAll(messages);

      // Call OpenAI model
      ChatRequest request = ChatRequest.builder().messages(chatMessages).build();
      ChatResponse response = openai.chat(request);
      AiMessage aiMessage = response.aiMessage();

      // Prepare new message list for storage
      List<ChatMessage> newMessages = new ArrayList<>();
      newMessages.addAll(storedMessages);
      newMessages.addAll(messages);
      newMessages.add(aiMessage);

      // Decide whether to summarize or continue
      if (newMessages.size() > 6) {
        // Emit to summarize node
        agentNode.emit("summarize", summary, newMessages, aiMessage, config);
      } else {
        // Store updated conversation and return result
        Map<String, Object> newCheckpoint =
            Map.of("messages", newMessages, "summary", summary != null ? summary : "");
        store.put(threadId, newCheckpoint);

        // Return just the AI message
        agentNode.result(Map.of("messages", List.of(aiMessage)));
      }

    } catch (Exception e) {
      throw new RuntimeException("Error in chat node: " + e.getMessage(), e);
    }
  }
}
