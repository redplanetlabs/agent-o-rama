package com.rpl.agent.research.util;

import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.UserMessage;
import java.util.List;

public class MessageExtractor {

  public static String extractInterview(List<ChatMessage> messages) {
    StringBuilder interview = new StringBuilder();

    for (ChatMessage message : messages) {
      if (message instanceof UserMessage userMessage) {
        String name = userMessage.name();
        if ("expert".equals(name)) {
          interview.append("Expert: ");
        } else {
          interview.append("Human: ");
        }
        interview.append(userMessage.singleText()).append("\n\n");
      } else if (message instanceof AiMessage aiMessage) {
        interview.append("AI: ").append(aiMessage.text()).append("\n\n");
      }
    }

    return interview.toString();
  }

  public static boolean isExpertMessage(ChatMessage message) {
    if (message instanceof UserMessage userMessage) {
      return "expert".equals(userMessage.name());
    }
    return false;
  }

  public static int countExpertMessages(List<ChatMessage> messages) {
    return (int) messages.stream().filter(MessageExtractor::isExpertMessage).count();
  }
}
