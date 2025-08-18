package com.rpl.agent.research.service;

import dev.langchain4j.data.document.Document;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.util.List;

public class TavilySearchService {
  private final TavilyWebSearchEngine searchEngine;

  public TavilySearchService(String apiKey) {
    this.searchEngine =
        TavilyWebSearchEngine.builder()
            .apiKey(apiKey)
            .excludeDomains(List.of("en.wikipedia.org"))
            .build();
  }

  public TavilySearchService(TavilyWebSearchEngine searchEngine) {
    this.searchEngine = searchEngine;
  }

  public List<Document> search(String query, int maxResults) {
    WebSearchRequest request = WebSearchRequest.from(query, maxResults);
    return searchEngine.search(request).toDocuments();
  }
}
