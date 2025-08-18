package com.rpl.agent.research.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;

public class AnalystResponse {
  @JsonProperty("analysts")
  private List<Analyst> analysts;

  public AnalystResponse() {}

  public AnalystResponse(List<Analyst> analysts) {
    this.analysts = analysts;
  }

  public List<Analyst> getAnalysts() {
    return analysts;
  }

  public void setAnalysts(List<Analyst> analysts) {
    this.analysts = analysts;
  }
}
