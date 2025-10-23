package com.rpl.agentorama.analytics;

import java.util.Map;

import com.rpl.agentorama.source.InfoSource;

/**
 * Feedback data for agent execution evaluation and scoring.
 * 
 * Feedback represents evaluation results and scores for agent executions,
 * including information about the source of the feedback and when it was
 * created or last modified.
 */
public interface Feedback {
  /**
   * Gets the evaluation scores for this feedback.
   * 
   * @return map from score name to score value (String, Boolean, or Number)
   */
  Map<String, Object> getScores();
  
  /**
   * Gets the source of this feedback.
   * 
   * @return the information source that generated this feedback
   */
  InfoSource getSource();
  
  /**
   * Gets the timestamp when this feedback was created.
   * 
   * @return creation timestamp in milliseconds since epoch
   */
  long getCreatedAt();
  
  /**
   * Gets the timestamp when this feedback was last modified.
   * 
   * @return modification timestamp in milliseconds since epoch
   */
  long getModifiedAt();
}
