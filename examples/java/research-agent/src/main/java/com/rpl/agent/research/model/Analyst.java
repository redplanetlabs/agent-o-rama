package com.rpl.agent.research.model;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Analyst {
  @JsonProperty("name")
  private String name;

  @JsonProperty("role")
  private String role;

  @JsonProperty("affiliation")
  private String affiliation;

  @JsonProperty("description")
  private String description;

  public Analyst() {}

  public Analyst(String name, String role, String affiliation, String description) {
    this.name = name;
    this.role = role;
    this.affiliation = affiliation;
    this.description = description;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getRole() {
    return role;
  }

  public void setRole(String role) {
    this.role = role;
  }

  public String getAffiliation() {
    return affiliation;
  }

  public void setAffiliation(String affiliation) {
    this.affiliation = affiliation;
  }

  public String getDescription() {
    return description;
  }

  public void setDescription(String description) {
    this.description = description;
  }

  public String formatPersona() {
    return String.format(
        "Name: %s\nRole: %s\nAffiliation: %s\nDescription: %s",
        name, role, affiliation, description);
  }

  @Override
  public String toString() {
    return "Analyst{"
        + "name='"
        + name
        + '\''
        + ", role='"
        + role
        + '\''
        + ", affiliation='"
        + affiliation
        + '\''
        + ", description='"
        + description
        + '\''
        + '}';
  }
}
