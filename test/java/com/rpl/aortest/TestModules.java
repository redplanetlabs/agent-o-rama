package com.rpl.aortest;

import java.util.Arrays;

import com.rpl.agentorama.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.openai.*;

import java.util.*;


public class TestModules {
  public static class BasicToolsOpenAIAgent extends AgentsModule {
    public static List<ToolInfo> TOOLS = Arrays.asList(
      ToolInfo.create(
        ToolSpecification.builder()
                         .name("add")
                         .parameters(JsonObjectSchema.builder()
                                                     .addIntegerProperty("a")
                                                     .addIntegerProperty("b")
                                                     .build())
                         .build(),
        (Map<String, Integer> args) -> {
          return "" + (args.get("a") + args.get("b"));
        }),
      ToolInfo.createWithContext(
        ToolSpecification.builder()
                         .name("multiply")
                         .parameters(JsonObjectSchema.builder()
                                                     .addIntegerProperty("a")
                                                     .addIntegerProperty("b")
                                                     .build())
                         .build(),
        (AgentNode node, Integer context, Map<String, Integer> args) -> {
          return "" + (args.get("a") * args.get("b") + context);
        })
      );

    @Override
    protected void defineAgents(AgentsTopology topology) {
      topology.declareAgentObject("openai-key", System.getenv("OPENAI_API_KEY"));
      topology.declareAgentObjectBuilder("openai", (AgentObjectSetup setup) -> {
        return OpenAiChatModel.builder()
                              .apiKey(setup.getAgentObject("openai-key"))
                              .build();
      });
      topology.newToolsAgent("tools", TOOLS);
      topology.newAgent("foo");
      // TODO: <<<<>>>>
    }
  }

}
