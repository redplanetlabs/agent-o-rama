package com.rpl.agent.research.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rpl.agent.research.model.WikipediaDocument;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class WikipediaService {
  private final HttpClient httpClient;
  private final ObjectMapper objectMapper;

  public WikipediaService() {
    this.httpClient = HttpClient.newHttpClient();
    this.objectMapper = new ObjectMapper();
  }

  public List<String> search(String query) throws IOException, InterruptedException {
    String encodedQuery = URLEncoder.encode(query, StandardCharsets.UTF_8);
    String url =
        "https://en.wikipedia.org/w/api.php?action=query&list=search&format=json&srsearch="
            + encodedQuery;

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new RuntimeException("Wikipedia search failed with status: " + response.statusCode());
    }

    JsonNode root = objectMapper.readTree(response.body());
    JsonNode searchResults = root.path("query").path("search");

    List<String> titles = new ArrayList<>();
    if (searchResults.isArray()) {
      for (JsonNode result : searchResults) {
        titles.add(result.path("title").asText());
      }
    }

    return titles;
  }

  public WikipediaDocument extract(String title) throws IOException, InterruptedException {
    String encodedTitle = URLEncoder.encode(title, StandardCharsets.UTF_8);
    String url =
        "https://en.wikipedia.org/w/api.php?action=query&prop=extracts&explaintext=true&format=json&titles="
            + encodedTitle;

    HttpRequest request = HttpRequest.newBuilder().uri(URI.create(url)).GET().build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() != 200) {
      throw new RuntimeException("Wikipedia extract failed with status: " + response.statusCode());
    }

    JsonNode root = objectMapper.readTree(response.body());
    JsonNode pages = root.path("query").path("pages");

    String content = "";
    if (pages.isObject()) {
      JsonNode firstPage = pages.elements().next();
      if (firstPage != null) {
        content = firstPage.path("extract").asText("");
      }
    }

    String source = "https://en.wikipedia.org/wiki/" + title.replace(" ", "_");
    return new WikipediaDocument(content, source, title);
  }

  public List<WikipediaDocument> searchAndExtract(String query, int maxDocs) {
    try {
      List<String> titles = search(query);
      List<WikipediaDocument> documents = new ArrayList<>();

      int limit = Math.min(maxDocs, titles.size());
      for (int i = 0; i < limit; i++) {
        try {
          WikipediaDocument doc = extract(titles.get(i));
          documents.add(doc);
        } catch (Exception e) {
          System.err.println(
              "Failed to extract Wikipedia article: " + titles.get(i) + " - " + e.getMessage());
        }
      }

      return documents;
    } catch (Exception e) {
      throw new RuntimeException("Wikipedia search and extract failed", e);
    }
  }
}
