package com.rpl.agent.research.model;

public class WikipediaDocument {
  private String content;
  private String source;
  private String page;

  public WikipediaDocument() {}

  public WikipediaDocument(String content, String source, String page) {
    this.content = content;
    this.source = source;
    this.page = page;
  }

  public String getContent() {
    return content;
  }

  public void setContent(String content) {
    this.content = content;
  }

  public String getSource() {
    return source;
  }

  public void setSource(String source) {
    this.source = source;
  }

  public String getPage() {
    return page;
  }

  public void setPage(String page) {
    this.page = page;
  }
}
