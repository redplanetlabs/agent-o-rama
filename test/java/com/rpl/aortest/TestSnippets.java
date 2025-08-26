package com.rpl.aortest;

import com.rpl.agentorama.*;
import com.rpl.agentorama.ToolsAgentOptions.*;

import clojure.lang.ExceptionInfo;

import java.util.*;

public class TestSnippets {
  public static List<ToolsAgentOptions> toolsAgentOptionsCases() {
    return Arrays.asList(
      ToolsAgentOptions.errorHandlerDefault(),
      ToolsAgentOptions.errorHandlerRethrow(),
      ToolsAgentOptions.errorHandlerStaticStringByType(
        StaticStringHandler.create(ClassCastException.class, "cc"),
        StaticStringHandler.create(ExceptionInfo.class, "ei"),
        StaticStringHandler.create(ArithmeticException.class, "ae")),
      ToolsAgentOptions.errorHandlerByType(
        FunctionHandler.create(ClassCastException.class, (Throwable t) -> t.getClass().getName()),
        FunctionHandler.create(ExceptionInfo.class, (ExceptionInfo t) -> (String) t.getData().valAt("a"))),
      ToolsAgentOptions.create(),
      ToolsAgentOptions.errorHandlerStaticString("abcde")
      );
  }

  public static void declareEvaluatorBuilders(AgentsTopology topology) {
    topology.declareEvaluatorBuilder("jeb1", "java builder 1", (Map<String, String> buildParams) -> {
      return (AgentObjectFetcher fetcher, String input, Long refOutput, String output) -> {
        Map ret = new HashMap();
        ret.put("score", input.length() + refOutput + output.length());
        return ret;
      };
    });
    topology.declareEvaluatorBuilder(
      "jeb2",
      "java builder 2",
      (Map<String, String> buildParams) -> {
        int foo1 = Integer.parseInt(buildParams.get("foo1"));
        int foo2 = Integer.parseInt(buildParams.get("foo2"));
        return (AgentObjectFetcher fetcher, String input, Long refOutput, String output) -> {
          Map ret = new HashMap();
          ret.put("score", input.length() + foo1 + foo2 + refOutput + output.length());
          return ret;
        };
      },
      EvaluatorBuilderOptions.param("foo1", "a number")
                             .param("foo2", "another number")
                             .withoutOutputPath()
    );
  }
}
