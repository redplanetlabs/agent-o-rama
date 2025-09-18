package com.rpl.aortest;

import java.util.Arrays;

import com.rpl.agentorama.*;
import com.rpl.rama.*;
import com.rpl.rama.test.*;

import dev.langchain4j.agent.tool.ToolSpecification;
import dev.langchain4j.data.message.ToolExecutionResultMessage;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.request.json.JsonObjectSchema;
import dev.langchain4j.model.chat.response.ChatResponse;
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
        (AgentNode node, Integer callerData, Map<String, Integer> args) -> {
          return "" + (args.get("a") * args.get("b") + callerData);
        })
      );

    public static void doModelCall(AgentNode node, String k, String prompt) {
      ChatModel model = node.getAgentObject("openai");
      AgentClient tools = node.getAgentClient("tools");
      List<ToolSpecification> t = new ArrayList();
      for(ToolInfo info: TOOLS) {
        t.add(info.getToolSpecification());
      }
      ChatResponse response = model.chat(ChatRequest.builder()
                                   .toolSpecifications(t)
                                   .messages(Arrays.asList(new UserMessage(prompt)))
                                   .build());
      List requests = response.aiMessage().toolExecutionRequests();
      if(requests.size() != 1) throw new RuntimeException("failed");
      List<ToolExecutionResultMessage> results = tools.invoke(requests, 6);
      if(results.size() != 1) throw new RuntimeException("failed");
      node.emit("agg", k, results.get(0).text());
    }

    @Override
    protected void defineAgents(AgentTopology topology) {
      topology.declareAgentObject("openai-key", System.getenv("OPENAI_API_KEY"));
      topology.declareAgentObjectBuilder("openai", (AgentObjectSetup setup) -> {
        return OpenAiChatModel.builder()
                              .apiKey(setup.getAgentObject("openai-key"))
                              .modelName("gpt-4o-mini")
                              .build();
      });
      topology.newToolsAgent("tools", TOOLS);
      topology.newToolsAgent("tools2", TOOLS, ToolsAgentOptions.errorHandlerStaticString("edcba"));
      topology.newAgent("foo")
              .node(
                "start",
                "a",
                (AgentNode node) -> {
                  node.emit("a");
                })
              .aggStartNode(
                "a",
                "model",
                (AgentNode node) -> {
                  node.emit("model", "a", "What is five added to three? Use a tool call to answer the question.");
                  node.emit("model", "m", "What is six multiplied by eight? Use a tool call to answer the question.");
                  return null;
                })
              .node(
                "model",
                List.of("agg"),
                BasicToolsOpenAIAgent::doModelCall)
              .aggNode(
                "agg",
                null,
                BuiltIn.MAP_AGG,
                (AgentNode node, Map ret, Object aggStartRes) -> {
                  node.result(ret);
                });
    }
  }

  public static Map runBasicToolsOpenAIAgent() throws Exception {
    try(InProcessCluster ipc = InProcessCluster.create()) {
      RamaModule module = new BasicToolsOpenAIAgent();
      ipc.launchModule(module, new LaunchConfig(4, 2));
      AgentManager manager = AgentManager.create(ipc, module.getModuleName());
      AgentClient foo = manager.getAgentClient("foo");
      return foo.invoke();
    }
  }
}
