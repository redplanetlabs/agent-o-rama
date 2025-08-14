package com.rpl.agentorama;

import java.util.Map;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.ops.*;

import dev.langchain4j.agent.tool.ToolSpecification;

public interface ToolInfo {
  static <T1> ToolInfo create(ToolSpecification spec, RamaFunction1<Map<String, T1>, String> toolFn) {
    return (ToolInfo) AORHelpers.CREATE_TOOL_INFO.invoke(spec, toolFn);
  }

  static <T1, T2> ToolInfo createWithContext(ToolSpecification spec, RamaFunction3<AgentNode, T1, Map<String, T2>, String> toolFn) {
    return (ToolInfo) AORHelpers.CREATE_TOOL_INFO_WITH_CONTEXT.invoke(spec, toolFn);
  }

  ToolSpecification getToolSpecification();
}
