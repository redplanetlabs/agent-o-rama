package com.rpl.agent.research.model;

import java.util.HashMap;
import java.util.Map;

public class ResearchOptions {
  private String topic;
  private int maxAnalysts;
  private int maxTurns;

  public ResearchOptions() {
    this.maxAnalysts = 4;
    this.maxTurns = 2;
  }

  public ResearchOptions(String topic) {
    this();
    this.topic = topic;
  }

  public ResearchOptions(String topic, int maxAnalysts, int maxTurns) {
    this.topic = topic;
    this.maxAnalysts = maxAnalysts;
    this.maxTurns = maxTurns;
  }

  public String getTopic() {
    return topic;
  }

  public void setTopic(String topic) {
    this.topic = topic;
  }

  public int getMaxAnalysts() {
    return maxAnalysts;
  }

  public void setMaxAnalysts(int maxAnalysts) {
    this.maxAnalysts = maxAnalysts;
  }

  public int getMaxTurns() {
    return maxTurns;
  }

  public void setMaxTurns(int maxTurns) {
    this.maxTurns = maxTurns;
  }

  public Map<String, Object> toMap() {
    Map<String, Object> map = new HashMap<>();
    map.put("topic", topic);
    map.put("max-analysts", maxAnalysts);
    map.put("max-turns", maxTurns);
    return map;
  }

  public static ResearchOptions fromMap(Map<String, Object> map) {
    ResearchOptions options = new ResearchOptions();
    options.topic = (String) map.get("topic");
    options.maxAnalysts = (Integer) map.getOrDefault("max-analysts", 4);
    options.maxTurns = (Integer) map.getOrDefault("max-turns", 2);
    return options;
  }
}
