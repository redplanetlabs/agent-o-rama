package com.rpl.agent.research;

import com.rpl.agentorama.*;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.message.AiMessage;
import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.SystemMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.ResponseFormat;
import dev.langchain4j.model.chat.request.ResponseFormatType;
import dev.langchain4j.model.chat.request.json.JsonSchema;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.request.json.JsonArraySchema;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import dev.langchain4j.web.search.WebSearchRequest;
import dev.langchain4j.web.search.tavily.TavilyWebSearchEngine;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

public class ResearchAgentModule extends AgentModule {

  private static final String ANALYST_INSTRUCTIONS =
    "You are tasked with creating a set of AI analyst personas. Follow these instructions carefully:\n\n" +
    "1. First, review the research topic: %s\n\n" +
    "2. Examine any editorial feedback that has been optionally provided to guide creation of the analysts:\n\n" +
    "%s\n\n" +
    "3. Determine the most interesting themes based upon documents and / or feedback above.\n\n" +
    "4. Pick the top %s themes.\n\n" +
    "5. Assign one analyst to each theme.";

  private static final String GENERATE_QUESTION_INSTRUCTIONS =
    "You are an analyst tasked with interviewing an expert to learn about a specific topic.\n\n" +
    "Your goal is boil down to interesting and specific insights related to your topic.\n\n" +
    "1. Interesting: Insights that people will find surprising or non-obvious.\n\n" +
    "2. Specific: Insights that avoid generalities and include specific examples from the expert.\n\n" +
    "Here is your topic of focus and set of goals: %s\n\n" +
    "Begin by introducing yourself using a name that fits your persona, and then ask your question.\n\n" +
    "Continue to ask questions to drill down and refine your understanding of the topic.\n\n" +
    "When you are satisfied with your understanding, complete the interview with: \"Thank you so much for your help!\"\n\n" +
    "Remember to stay in character throughout your response, reflecting the persona and goals provided to you.";

  private static final String SEARCH_INSTRUCTIONS =
    "You will be given a conversation between an analyst and an expert.\n\n" +
    "Your goal is to generate a well-structured query for use in retrieval and / or web-search related to the conversation.\n\n" +
    "First, analyze the full conversation.\n\n" +
    "Pay particular attention to the final question posed by the analyst.\n\n" +
    "Convert this final question into a well-structured web search query no more than 400 characters.";

  private static final String WEB_DOCUMENT_TEMPLATE =
    "<Document href=\"%s\">\n%s\n</Document>";

  private static final String WIKIPEDIA_DOCUMENT_TEMPLATE =
    "<Document source=\"%s\" page=\"%s\">\n%s\n</Document>";

  private static final String ANSWER_INSTRUCTIONS =
    "You are an expert being interviewed by an analyst.\n\n" +
    "Here is analyst area of focus: %s.\n\n" +
    "You goal is to answer a question posed by the interviewer.\n\n" +
    "To answer question, use this context:\n\n" +
    "%s\n\n" +
    "When answering questions, follow these guidelines:\n\n" +
    "1. Use only the information provided in the context.\n\n" +
    "2. Do not introduce external information or make assumptions beyond what is explicitly stated in the context.\n\n" +
    "3. The context contain sources at the topic of each individual document.\n\n" +
    "4. Include these sources your answer next to any relevant statements. For example, for source # 1 use [1].\n\n" +
    "5. List your sources in order at the bottom of your answer. [1] Source 1, [2] Source 2, etc\n\n" +
    "6. If the source is: <Document source=\"assistant/docs/llama3_1.pdf\" page=\"7\"/>' then just list:\n\n" +
    "[1] assistant/docs/llama3_1.pdf, page 7\n\n" +
    "And skip the addition of the brackets as well as the Document source preamble in your citation.";

  private static final String SECTION_WRITER_INSTRUCTIONS =
    "You are an expert technical writer.\n\n" +
    "Your task is to create a short, easily digestible section of a report based on a set of source documents.\n\n" +
    "1. Analyze the content of the source documents:\n" +
    "- The name of each source document is at the start of the document, with the <Document tag.\n\n" +
    "2. Create a report structure using markdown formatting:\n" +
    "- Use ## for the section title\n" +
    "- Use ### for sub-section headers\n\n" +
    "3. Write the report following this structure:\n" +
    "a. Title (## header)\n" +
    "b. Summary (### header)\n" +
    "c. Sources (### header)\n\n" +
    "4. Make your title engaging based upon the focus area of the analyst:\n" +
    "%s\n\n" +
    "5. For the summary section:\n" +
    "- Set up summary with general background / context related to the focus area of the analyst\n" +
    "- Emphasize what is novel, interesting, or surprising about insights gathered from the interview\n" +
    "- Create a numbered list of source documents, as you use them\n" +
    "- Do not mention the names of interviewers or experts\n" +
    "- Aim for approximately 400 words maximum\n" +
    "- Use numbered sources in your report (e.g., [1], [2]) based on information from source documents\n\n" +
    "6. In the Sources section:\n" +
    "- Include all sources used in your report\n" +
    "- Provide full links to relevant websites or specific document paths\n" +
    "- Separate each source by a newline. Use two spaces at the end of each line to create a newline in Markdown.\n" +
    "- It will look like:\n\n" +
    "### Sources\n" +
    "[1] Link or Document name\n" +
    "[2] Link or Document name\n\n" +
    "7. Be sure to combine sources. For example this is not correct:\n\n" +
    "[3] https://ai.meta.com/blog/meta-llama-3-1/\n" +
    "[4] https://ai.meta.com/blog/meta-llama-3-1/\n\n" +
    "There should be no redundant sources. It should simply be:\n\n" +
    "[3] https://ai.meta.com/blog/meta-llama-3-1/\n\n" +
    "8. Final review:\n" +
    "- Ensure the report follows the required structure\n" +
    "- Include no preamble before the title of the report\n" +
    "- Check that all guidelines have been followed";

  private static final String REPORT_WRITER_INSTRUCTIONS =
    "You are a technical writer creating a report on this overall topic:\n\n" +
    "%s\n\n" +
    "You have a team of analysts. Each analyst has done two things:\n\n" +
    "1. They conducted an interview with an expert on a specific sub-topic.\n" +
    "2. They write up their finding into a memo.\n\n" +
    "Your task:\n\n" +
    "1. You will be given a collection of memos from your analysts.\n" +
    "2. Think carefully about the insights from each memo.\n" +
    "3. Consolidate these into a crisp overall summary that ties together the central ideas from all of the memos.\n" +
    "4. Summarize the central points in each memo into a cohesive single narrative.\n\n" +
    "To format your report:\n\n" +
    "1. Use markdown formatting.\n" +
    "2. Include no pre-amble for the report.\n" +
    "3. Use no sub-heading.\n" +
    "4. Start your report with a single title header: ## Insights\n" +
    "5. Do not mention any analyst names in your report.\n" +
    "6. Preserve any citations in the memos, which will be annotated in brackets, for example [1] or [2].\n" +
    "7. Create a final, consolidated list of sources and add to a Sources section with the `## Sources` header.\n" +
    "8. List your sources in order and do not repeat.\n\n" +
    "[1] Source 1\n" +
    "[2] Source 2\n\n" +
    "Here are the memos from your analysts to build your report from:\n\n" +
    "%s";

  private static final String INTRO_CONCLUSION_INSTRUCTIONS =
    "You are a technical writer finishing a report on %s\n\n" +
    "You will be given all of the sections of the report.\n\n" +
    "You job is to write a crisp and compelling introduction or conclusion section.\n\n" +
    "The user will instruct you whether to write the introduction or conclusion.\n\n" +
    "Include no pre-amble for either section.\n\n" +
    "Target around 100 words, crisply previewing (for introduction) or recapping (for conclusion) all of the sections of the report.\n\n" +
    "Use markdown formatting.\n\n" +
    "For your introduction, create a compelling title and use the # header for the title.\n\n" +
    "For your introduction, use ## Introduction as the section header.\n\n" +
    "For your conclusion, use ## Conclusion as the section header.\n\n" +
    "Here are the sections to reflect on for writing: %s";

  private static final HttpClient httpClient = HttpClient.newHttpClient();
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private static final JsonSchema ANALYST_RESPONSE_SCHEMA = JsonSchema.builder()
    .name("analysts")
    .rootElement(JsonObjectSchema.builder()
      .addProperty("analysts", JsonArraySchema.builder()
        .items(JsonObjectSchema.builder()
          .addStringProperty("name", "Name of the analyst")
          .addStringProperty("role", "Role of the analyst in the context of the topic")
          .addStringProperty("affiliation", "Primary affiliation of the analyst")
          .addStringProperty("description", "Description of the analyst focus, concerns, and motives")
          .build())
        .build())
      .build())
    .build();


  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));
    topology.declareAgentObject("tavily-api-key", System.getenv("TAVILY_API_KEY"));

    topology.declareAgentObjectBuilder("openai", setup ->
      OpenAiStreamingChatModel.builder()
        .apiKey((String) setup.getAgentObject("openai-api-key"))
        .modelName("gpt-4o-mini")
        .build()
    );

    topology.declareAgentObjectBuilder("openai-non-streaming", setup ->
      OpenAiChatModel.builder()
        .apiKey((String) setup.getAgentObject("openai-api-key"))
        .modelName("gpt-4o-mini")
        .build()
    );

    topology.declareAgentObjectBuilder("tavily", setup ->
      TavilyWebSearchEngine.builder()
        .apiKey((String) setup.getAgentObject("tavily-api-key"))
        .excludeDomains(Arrays.asList("en.wikipedia.org"))
        .timeout(Duration.ofMinutes(1))
        .build()
    );

    topology.newAgent("researcher")
      .node("create-analysts", "feedback", (AgentNode agentNode, String humanFeedback, Map<String, Object> options) -> {
        Map<String, Object> config = new HashMap<>();
        config.put("max-analysts", 4);
        config.put("max-turns", 2);
        config.putAll(options);

        String topic = (String) config.get("topic");
        Integer maxAnalysts = (Integer) config.get("max-analysts");

        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai-non-streaming");

        String instructions = String.format(ANALYST_INSTRUCTIONS, topic, humanFeedback, maxAnalysts);

        List<ChatMessage> messages = Arrays.asList(new SystemMessage(instructions));

        ResponseFormat responseFormat = ResponseFormat.builder()
          .type(ResponseFormatType.JSON)
          .jsonSchema(ANALYST_RESPONSE_SCHEMA)
          .build();

        ChatRequest chatRequest = ChatRequest.builder()
          .messages(messages)
          .responseFormat(responseFormat)
          .build();

        String response = openai.chat(chatRequest).aiMessage().text();

        List<Map<String, Object>> analysts = parseAnalystsResponse(response);

        agentNode.emit("feedback", analysts, config);
      })
      .node("feedback", Arrays.asList("create-analysts", "questions"), (AgentNode agentNode, List<Map<String, Object>> analysts, Map<String, Object> options) -> {
        String prompt = "Do you have any feedback on this set of analysts? Answer 'yes' or 'no'.\n\n" +
          analysts.stream()
            .map(analyst -> analyst.toString())
            .collect(Collectors.joining("\n"));

        boolean hasFeedback = humanYes(agentNode, prompt);
        if (hasFeedback) {
          String feedback = agentNode.getHumanInput("What is your feedback?");
          agentNode.emit("create-analysts", feedback, options);
        } else {
          agentNode.emit("questions", analysts, options);
        }
      })
      .aggStartNode("questions", "generate-question", (AgentNode agentNode, List<Map<String, Object>> analysts, Map<String, Object> config) -> {
        for (Map<String, Object> analyst : analysts) {
          String persona = formatAnalystPersona(analyst);
          agentNode.emit("generate-question", persona, new ArrayList<ChatMessage>(), (Integer) config.get("max-turns"));
        }
        return config;
      })
      .aggStartNode("generate-question", Arrays.asList("search-web", "search-wikipedia"), (AgentNode agentNode, String persona, List<ChatMessage> messages, Integer maxTurns) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String instructions = String.format(GENERATE_QUESTION_INSTRUCTIONS, persona);

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage(instructions));
        chatMessages.addAll(messages);

        AiMessage question = openai.chat(chatMessages).aiMessage();
        List<ChatMessage> newMessages = new ArrayList<>(messages);
        newMessages.add(question);

        String searchQuery = generateSearchQuery(openai, newMessages);

        agentNode.emit("search-web", searchQuery);
        agentNode.emit("search-wikipedia", searchQuery);

        Map<String, Object> result = new HashMap<>();
        result.put("persona", persona);
        result.put("messages", newMessages);
        result.put("max-turns", maxTurns);
        return result;
      })
      .node("search-web", "agg-research", (AgentNode agentNode, String searchQuery) -> {
        TavilyWebSearchEngine tavily = (TavilyWebSearchEngine) agentNode.getAgentObject("tavily");
        List<Document> docs = tavily.search(WebSearchRequest.from(searchQuery, 3)).toDocuments();

        for (Document doc : docs) {
          String url = doc.metadata().getString("url");
          String content = doc.text();
          String formattedDoc = String.format(WEB_DOCUMENT_TEMPLATE, url, content);
          agentNode.emit("agg-research", formattedDoc);
        }
      })
      .node("search-wikipedia", "agg-research", (AgentNode agentNode, String searchQuery) -> {
        try {
          List<Map<String, Object>> docs = wikipediaLoader(searchQuery.replace("\"", ""), 2);
          for (Map<String, Object> doc : docs) {
            String source = (String) doc.get("source");
            String page = (String) doc.get("page");
            String content = (String) doc.get("content");
            String formattedDoc = String.format(WIKIPEDIA_DOCUMENT_TEMPLATE, source, page, content);
            agentNode.emit("agg-research", formattedDoc);
          }
        } catch (Exception e) {
          throw new RuntimeException("Wikipedia search failed", e);
        }
      })
      .aggNode("agg-research", Arrays.asList("generate-question", "write-section"), BuiltIn.LIST_AGG, (AgentNode agentNode, List<String> searches, Map<String, Object> context) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String persona = (String) context.get("persona");
        List<ChatMessage> messages = (List<ChatMessage>) context.get("messages");
        Integer maxTurns = (Integer) context.get("max-turns");

        String searchContext = String.join("\n---\n", searches);
        String instructions = String.format(ANSWER_INSTRUCTIONS, persona, searchContext);

        List<ChatMessage> chatMessages = new ArrayList<>();
        chatMessages.add(new SystemMessage(instructions));
        chatMessages.addAll(messages);

        String answer = openai.chat(chatMessages).aiMessage().text();
        List<ChatMessage> newMessages = new ArrayList<>(messages);
        newMessages.add(new UserMessage("expert", answer));

        long expertTurns = newMessages.stream()
          .filter(msg -> msg instanceof UserMessage)
          .map(msg -> (UserMessage) msg)
          .filter(msg -> "expert".equals(msg.name()))
          .count();

        if (expertTurns >= maxTurns) {
          agentNode.emit("write-section", persona, newMessages, searchContext);
        } else {
          agentNode.emit("generate-question", persona, newMessages, maxTurns);
        }
      })
      .node("write-section", "agg-sections", (AgentNode agentNode, String persona, List<ChatMessage> messages, String context) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String interview = extractInterview(messages);
        String instructions = String.format(SECTION_WRITER_INSTRUCTIONS, persona);

        List<ChatMessage> chatMessages = Arrays.asList(
          new SystemMessage(instructions),
          new UserMessage("Here is the interview:\n" + interview),
          new UserMessage("Here are the sources:\n" + context)
        );

        String section = openai.chat(chatMessages).aiMessage().text();
        agentNode.emit("agg-sections", section);
      })
      .aggNode("agg-sections", "begin-report", BuiltIn.LIST_AGG, (AgentNode agentNode, List<String> sections, Map<String, Object> context) -> {
        String topic = (String) context.get("topic");
        agentNode.emit("begin-report", sections, topic);
      })
      .aggStartNode("begin-report", Arrays.asList("write-report", "write-intro", "write-conclusion"), (AgentNode agentNode, List<String> sections, String topic) -> {
        String sectionsText = String.join("\n\n", sections);
        agentNode.emit("write-report", sectionsText, topic);
        agentNode.emit("write-intro", sectionsText, topic);
        agentNode.emit("write-conclusion", sectionsText, topic);
        return null;
      })
      .node("write-report", "finish-report", (AgentNode agentNode, String sections, String topic) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String instructions = String.format(REPORT_WRITER_INSTRUCTIONS, topic, sections);

        List<ChatMessage> chatMessages = Arrays.asList(
          new SystemMessage(instructions),
          new UserMessage("Write a report based upon these memos.")
        );

        String report = openai.chat(chatMessages).aiMessage().text();
        agentNode.emit("finish-report", "report", report);
      })
      .node("write-intro", "finish-report", (AgentNode agentNode, String sections, String topic) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String instructions = String.format(INTRO_CONCLUSION_INSTRUCTIONS, topic, sections);

        List<ChatMessage> chatMessages = Arrays.asList(
          new SystemMessage(instructions),
          new UserMessage("Write the report introduction")
        );

        String intro = openai.chat(chatMessages).aiMessage().text();
        agentNode.emit("finish-report", "intro", intro);
      })
      .node("write-conclusion", "finish-report", (AgentNode agentNode, String sections, String topic) -> {
        ChatModel openai = (ChatModel) agentNode.getAgentObject("openai");
        String instructions = String.format(INTRO_CONCLUSION_INSTRUCTIONS, topic, sections);

        List<ChatMessage> chatMessages = Arrays.asList(
          new SystemMessage(instructions),
          new UserMessage("Write the report conclusion")
        );

        String conclusion = openai.chat(chatMessages).aiMessage().text();
        agentNode.emit("finish-report", "conclusion", conclusion);
      })
      .aggNode("finish-report", null, BuiltIn.MAP_AGG, (AgentNode agentNode, Map<String, String> reportParts, Object startNodeResult) -> {
        String report = reportParts.get("report");
        String intro = reportParts.get("intro");
        String conclusion = reportParts.get("conclusion");

        report = report.replaceAll("## Insights", "");
        String[] parts = report.split("## Sources");
        String reportBody = parts[0];
        String sources = parts.length > 1 ? parts[1] : null;

        StringBuilder finalReportBuilder = new StringBuilder();
        if (intro != null) {
          finalReportBuilder.append(intro).append("\n\n---\n");
        }
        finalReportBuilder.append(reportBody);
        if (conclusion != null) {
          finalReportBuilder.append("\n---\n\n").append(conclusion);
        }
        if (sources != null) {
          finalReportBuilder.append("\n\n## Sources").append(sources);
        }

        String finalReport = finalReportBuilder.toString();

        agentNode.result(finalReport);
      });
  }

  private static List<Map<String, Object>> parseAnalystsResponse(String response) {
    try {
      JsonNode root = objectMapper.readTree(response);
      JsonNode analystsArray = root.path("analysts");

      if (!analystsArray.isArray()) {
        throw new RuntimeException("Invalid JSON schema: 'analysts' field must be an array");
      }

      List<Map<String, Object>> analysts = new ArrayList<>();
      for (JsonNode analystNode : analystsArray) {
        if (!analystNode.has("name") || !analystNode.has("role") ||
            !analystNode.has("affiliation") || !analystNode.has("description")) {
          throw new RuntimeException("Invalid JSON schema: analyst missing required fields (name, role, affiliation, description)");
        }

        Map<String, Object> analyst = new HashMap<>();
        analyst.put("name", analystNode.path("name").asText());
        analyst.put("role", analystNode.path("role").asText());
        analyst.put("affiliation", analystNode.path("affiliation").asText());
        analyst.put("description", analystNode.path("description").asText());
        analysts.add(analyst);
      }

      if (analysts.isEmpty()) {
        throw new RuntimeException("No valid analysts found in response");
      }

      return analysts;
    } catch (Exception e) {
      throw new RuntimeException("Failed to parse analysts response: " + e.getMessage(), e);
    }
  }

  private static String formatAnalystPersona(Map<String, Object> analyst) {
    String name = analyst.get("name") != null ? analyst.get("name").toString() : "Unknown";
    String role = analyst.get("role") != null ? analyst.get("role").toString() : "Unknown";
    String affiliation = analyst.get("affiliation") != null ? analyst.get("affiliation").toString() : "Unknown";
    String description = analyst.get("description") != null ? analyst.get("description").toString() : "No description available";

    return String.format("Name: %s\nRole: %s\nAffiliation: %s\nDescription: %s",
      name, role, affiliation, description);
  }

  private static boolean humanYes(AgentNode agentNode, String prompt) {
    String currentPrompt = prompt;
    while (true) {
      String response = agentNode.getHumanInput(currentPrompt);
      if ("yes".equalsIgnoreCase(response.trim())) {
        return true;
      } else if ("no".equalsIgnoreCase(response.trim())) {
        return false;
      } else {
        currentPrompt = "Please answer 'yes' or 'no'.";
      }
    }
  }

  private static String generateSearchQuery(ChatModel openai, List<ChatMessage> messages) {
    List<ChatMessage> chatMessages = new ArrayList<>();
    chatMessages.add(new SystemMessage(SEARCH_INSTRUCTIONS));
    chatMessages.addAll(messages);

    int iters = 0;
    while (iters < 3) {
      String query = openai.chat(chatMessages).aiMessage().text();
      if (query.length() <= 400) return query;
      chatMessages.add(new UserMessage(String.format("You last generated: %s\nTry again and keep the query under 400 chars.", query)));
      iters++;
    }

    throw new RuntimeException("Failed to generate search query <= 400 chars");
  }

  private static String extractInterview(List<ChatMessage> messages) {
    StringBuilder interview = new StringBuilder();
    for (ChatMessage msg : messages) {
      if (msg instanceof UserMessage) {
        UserMessage userMsg = (UserMessage) msg;
        String prefix = "expert".equals(userMsg.name()) ? "Expert: " : "Human: ";
        interview.append(prefix).append(userMsg.singleText()).append("\n\n");
      } else if (msg instanceof AiMessage) {
        AiMessage aiMsg = (AiMessage) msg;
        interview.append("AI: ").append(aiMsg.text()).append("\n\n");
      }
    }
    return interview.toString();
  }

  private static List<Map<String, Object>> wikipediaLoader(String query, int maxDocs) throws IOException, InterruptedException {
    List<String> titles = wikiSearch(query);
    List<Map<String, Object>> docs = new ArrayList<>();

    if (titles == null || titles.isEmpty()) {
      return docs;
    }

    int docCount = Math.min(titles.size(), maxDocs);
    for (int i = 0; i < docCount; i++) {
      String title = titles.get(i);
      Map<String, Object> doc = wikiExtract(title);
      docs.add(doc);
    }

    return docs;
  }

  private static List<String> wikiSearch(String query) throws IOException, InterruptedException {
    String url = "https://en.wikipedia.org/w/api.php" +
      "?action=query&list=search&format=json&srsearch=" +
      URLEncoder.encode(query, StandardCharsets.UTF_8);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(java.net.URI.create(url))
      .header("User-Agent", "Agent-o-rama/1.0 (Research Agent)")
      .header("Accept", "application/json")
      .GET()
      .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode queryNode = root.path("query");
      JsonNode searchResults = queryNode.path("search");

      List<String> titles = new ArrayList<>();
      if (searchResults.isArray()) {
        for (JsonNode result : searchResults) {
          String title = result.path("title").asText();
          if (title != null && !title.isEmpty()) {
            titles.add(title);
          }
        }
      }
      return titles;
    } else {
      throw new RuntimeException("Wikipedia search failed with status: " + response.statusCode());
    }
  }

  private static Map<String, Object> wikiExtract(String title) throws IOException, InterruptedException {
    String url = "https://en.wikipedia.org/w/api.php" +
      "?action=query&prop=extracts&explaintext=true&format=json&titles=" +
      URLEncoder.encode(title, StandardCharsets.UTF_8);

    HttpRequest request = HttpRequest.newBuilder()
      .uri(java.net.URI.create(url))
      .header("User-Agent", "Agent-o-rama/1.0 (Research Agent)")
      .header("Accept", "application/json")
      .GET()
      .build();

    HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

    if (response.statusCode() == 200) {
      JsonNode root = objectMapper.readTree(response.body());
      JsonNode queryNode = root.path("query");
      JsonNode pages = queryNode.path("pages");

      Map<String, Object> doc = new HashMap<>();
      if (pages.isObject() && pages.size() > 0) {
        JsonNode firstPage = pages.iterator().next();
        String extract = firstPage.path("extract").asText();
        doc.put("content", extract != null ? extract : "");
        doc.put("source", "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"));
        doc.put("page", title);
      } else {
        doc.put("content", "");
        doc.put("source", "https://en.wikipedia.org/wiki/" + title.replace(" ", "_"));
        doc.put("page", title);
      }
      return doc;
    } else {
      throw new RuntimeException("Wikipedia extract failed with status: " + response.statusCode());
    }
  }
}
