package com.rpl.agent.react;

import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.ToolInfo;
import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Factory class for creating tools used by the ReAct agent.
 *
 * <p>This class provides web search capabilities using the Tavily search engine, allowing the agent
 * to search for information on the web and incorporate the results into its reasoning process.
 */
public class ToolsFactory {

  /** Creates the list of available tools for the ReAct agent. */
  public static List<ToolInfo> createTools() {
    return Arrays.asList(createTavilySearchTool());
  }

  /** Creates the tool specifications for LangChain4j integration. */
  public static List<ToolSpecification> createToolSpecifications() {
    return createTools().stream().map(ToolInfo::getToolSpecification).collect(Collectors.toList());
  }

  /** Creates a web search tool using Tavily search engine. */
  private static ToolInfo createTavilySearchTool() {
    ToolSpecification spec =
        ToolSpecification.builder()
            .name("tavily")
            .description("Search the web")
            .parameters(
                JsonObjectSchema.builder()
                    .addStringProperty("terms", "The terms to search for")
                    .build())
            .build();

    return ToolInfo.createWithContext(spec, ToolsFactory::executeTavilySearch);
  }

  /**
   * Executes a web search using the Tavily search engine.
   *
   * @param agentNode The agent node for accessing shared objects
   * @param unused Unused parameter for compatibility
   * @param arguments The tool arguments containing search terms
   * @return Search results as concatenated text
   */
  private static String executeTavilySearch(
      AgentNode agentNode, Object unused, Map<String, Object> arguments) {
    try {
      String terms = (String) arguments.get("terms");
      if (terms == null || terms.trim().isEmpty()) {
        return "Error: No search terms provided";
      }

      // Get the Tavily search engine from agent objects
      TavilyWebSearchEngine tavily = agentNode.getAgentObject("tavily");

      // Create search request with max 3 results
      WebSearchRequest searchRequest = WebSearchRequest.from(terms, 3);

      // Execute the search and convert results to documents
      List<Document> searchResults = tavily.search(searchRequest).toDocuments();

      if (searchResults.isEmpty()) {
        return "No search results found for: " + terms;
      }

      // Concatenate search results with separators
      return searchResults.stream().map(Document::text).collect(Collectors.joining("\n---\n"));

    } catch (Exception e) {
      return "Error during search: " + e.getMessage();
    }
  }
}
