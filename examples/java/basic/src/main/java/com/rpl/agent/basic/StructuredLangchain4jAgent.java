package com.rpl.agent.basic;

import com.rpl.agentorama.AgentClient;
import com.rpl.agentorama.AgentManager;
import com.rpl.agentorama.AgentNode;
import com.rpl.agentorama.AgentTopology;
import com.rpl.agentorama.AgentsModule;
import com.rpl.agentorama.ops.RamaVoidFunction2;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.output.structured.Description;
import java.util.List;

/**
 * Java example demonstrating LangChain4j structured output with JSON response format.
 *
 * <p>Features demonstrated:
 *
 * <ul>
 *   <li>Structured output with Java records
 *   <li>OpenAI chat model integration with structured responses
 *   <li>Single-node agent returning structured data
 * </ul>
 *
 * <p>This example requires OPENAI_API_KEY environment variable to be set.
 */
public class StructuredLangchain4jAgent {

  /** Structured output class for question analysis. */
  public record QuestionAnalysis(
      @Description("Type of question being asked") String questionType,
      @Description("Complexity level of the question") String complexity,
      @Description("Key topics covered in the question") List<String> mainTopics,
      @Description("Direct answer to the user's question") String answer,
      @Description("Confidence level in the response") String confidence) {}

  /** Agent Module demonstrating structured LangChain4j output. */
  public static class StructuredLangChain4jModule extends AgentsModule {

    @Override
    protected void defineAgents(AgentTopology topology) {
      // Declare OpenAI API key as agent object
      topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

      // Build OpenAI chat model with configuration
      topology.declareAgentObjectBuilder(
          "openai-model",
          setup -> {
            String apiKey = (String) setup.getAgentObject("openai-api-key");
            return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .temperature(0.3)
                .maxTokens(300)
                .responseFormat("json_object")
                .build();
          });

      topology
          .newAgent("StructuredLangChain4jAgent")
          .node("analyze-question", null, new AnalyzeQuestionFunction());
    }
  }

  /** Node function that analyzes user question and returns structured response. */
  public static class AnalyzeQuestionFunction implements RamaVoidFunction2<AgentNode, String> {

    @Override
    public void invoke(AgentNode agentNode, String userQuestion) {
      ChatModel model = (ChatModel) agentNode.getAgentObject("openai-model");

      // Request structured output from OpenAI
      String systemPrompt =
          "You are an intelligent question analyzer. Analyze the user's question and provide a JSON"
              + " response with these fields: questionType (one of: factual, analytical, creative,"
              + " technical, personal), complexity (one of: simple, moderate, complex), mainTopics"
              + " (array of key topics), answer (direct answer to the question), confidence (one"
              + " of: high, medium, low).";

      String fullPrompt = systemPrompt + "\n\nUser question: " + userQuestion;

      String response = model.chat(fullPrompt);

      agentNode.result(response);
    }
  }

  public static void main(String[] args) throws Exception {
    String apiKey = System.getenv("OPENAI_API_KEY");
    if (apiKey == null || apiKey.trim().isEmpty()) {
      System.out.println("Structured LangChain4j Agent Example:");
      System.out.println("OPENAI_API_KEY environment variable not set.");
      System.out.println("Please set your OpenAI API key to run this example:");
      System.out.println("  export OPENAI_API_KEY=your-api-key-here");
      return;
    }

    try (InProcessCluster ipc = InProcessCluster.create()) {
      StructuredLangChain4jModule module = new StructuredLangChain4jModule();
      ipc.launchModule(module, new LaunchConfig(1, 1));

      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("StructuredLangChain4jAgent");

      System.out.println("Structured LangChain4j Agent Example:");
      System.out.println("Analyzing questions with structured output...\n");

      // Test with different types of questions
      String[] questions = {
        "What is artificial intelligence?",
        "How can I improve my programming skills?",
        "Write a creative story about a robot"
      };

      for (String question : questions) {
        System.out.println("Question: " + question);
        String result = (String) agent.invoke(question);
        System.out.println("Analysis: " + result);
        System.out.println();
      }

      System.out.println("Notice how:");
      System.out.println("- JSON response format ensures structured output");
      System.out.println("- Different question types are automatically categorized");
      System.out.println("- Response includes metadata about the analysis");
    }
  }
}
