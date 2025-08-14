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
}
