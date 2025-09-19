package com.rpl.agentorama.analytics;

import java.util.Map;

import com.rpl.agentorama.source.InfoSource;

public interface Feedback {
  Map<String, Object> getScores();
  InfoSource getSource();
  long getCreatedAt();
  long getModifiedAt();
}
