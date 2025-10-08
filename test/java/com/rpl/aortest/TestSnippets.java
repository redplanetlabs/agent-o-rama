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

  public static void declareEvaluatorBuilders(AgentTopology topology) {
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

    topology.declareComparativeEvaluatorBuilder("jcompare1", "java compare 1", (Map<String, String> buildParams) -> {
      return (AgentObjectFetcher fetcher, Long input, Long refOutput, List<clojure.lang.Keyword> outputs) -> {
        clojure.lang.Keyword s;
        if(input < refOutput) s = outputs.get(0);
        else if(input > refOutput) s = outputs.get(2);
        else s = outputs.get(1);
        Map ret = new HashMap();
        ret.put("res", s);
        return ret;
      };
    });
    topology.declareComparativeEvaluatorBuilder("jcompare2", "java compare 2", (Map<String, String> buildParams) -> {
      return (AgentObjectFetcher fetcher, Long input, Long refOutput, List<clojure.lang.Keyword> outputs) -> {
        clojure.lang.Keyword s;
        if(input < refOutput) s = outputs.get(0);
        else if(input > refOutput) s = outputs.get(2);
        else s = outputs.get(1);
        Map ret = new HashMap();
        ret.put("res", s);
        ret.put("extra", buildParams.get("extra"));
        return ret;
      };
    },
     EvaluatorBuilderOptions.param("extra", "more input"));


    topology.declareSummaryEvaluatorBuilder("jsum1", "java sum 1", (Map<String, String> builderParams) -> {
      return (AgentObjectFetcher fetcher, List<ExampleRun> runs) -> {
        Long sum = 0L;
        for(ExampleRun run: runs) {
          sum+=(Long)run.getInput();
          sum+=(Long)run.getReferenceOutput();
          sum+=(Long)run.getOutput();
        }
        Map ret = new HashMap();
        ret.put("res", sum);
        return ret;
      };
    });
    topology.declareSummaryEvaluatorBuilder("jsum2", "java sum 1", (Map<String, String> builderParams) -> {
      return (AgentObjectFetcher fetcher, List<ExampleRun> runs) -> {
        Long sum = Long.parseLong(builderParams.get("extra"));
        for(ExampleRun run: runs) {
          sum+=(Long)run.getInput();
          sum+=(Long)run.getReferenceOutput();
          sum+=(Long)run.getOutput();
        }
        Map ret = new HashMap();
        ret.put("res", sum);
        return ret;
      };
    },
    EvaluatorBuilderOptions.param("extra", "more input"));
  }

  public static void declareActionBuilders(AgentTopology topology) {
    topology.declareActionBuilder("action3", "a 3rd one", (Map<String, String> params) -> {
      return (AgentObjectFetcher fetcher, List<Object> input, Object output, RunInfo info) -> {
        Map ret = new HashMap();
        ret.put("input", input);
        ret.put("output", output);
        return ret;
      };
    });
    topology.declareActionBuilder("action4", "a 4th one", (Map<String, String> params) -> {
      return (AgentObjectFetcher fetcher, List<String> input, String output, RunInfo info) -> {
        Map ret = new HashMap();
        ret.put("input", input);
        ret.put("output", output);
        ret.put("params", params);
        return ret;
      };
    },
    ActionBuilderOptions.param("jparam1", "jp jp", "aaa").param("jparam2", "jp2"));
  }

  public static AgentInvoke initiateWithContext(AgentClient client) {
    return client.initiateWithContext(
      AgentContext.metadata("l", 1L)
                  .metadata("i", 1)
                  .metadata("f", 0.5)
                  .metadata("d", 0.5d)
                  .metadata("s", "abc")
                  .metadata("b", true));
  }

  public static void setMetadata(AgentClient client, AgentInvoke inv) {
    client.setMetadata(inv, "l", 1L);
    client.setMetadata(inv, "i", 1);
    client.setMetadata(inv, "f", 0.5);
    client.setMetadata(inv, "d", 0.5d);
    client.setMetadata(inv, "s", "abc");
    client.setMetadata(inv, "b", true);
  }
}
