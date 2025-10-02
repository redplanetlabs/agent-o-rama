package com.rpl.agentorama;

import com.rpl.agentorama.impl.AORHelpers;
import com.rpl.rama.PState;
import com.rpl.rama.RamaModule.*;
import com.rpl.rama.module.*;
import com.rpl.rama.ops.*;
import java.util.*;

public interface AgentTopology {

  public static AgentTopology create(Setup setup, Topologies topologies) {
    return (AgentTopology) AORHelpers.CREATE_AGENTS_TOPOLOGY.invoke(setup, topologies);
  }

  AgentGraph newAgent(String name);
  AgentGraph newToolsAgent(String name, List<ToolInfo> tools);
  AgentGraph newToolsAgent(String name, List<ToolInfo> tools, ToolsAgentOptions options);

  void declareKeyValueStore(String name, Class keyClass, Class valClass);
  void declareDocumentStore(String name, Class keyClass, Object... keyAndValClasses);
  PState.Declaration declarePStateStore(String name, Class schema);
  PState.Declaration declarePStateStore(String name,  PState.Schema schema);

  void declareAgentObject(String name, Object o);
  void declareAgentObjectBuilder(String name, RamaFunction1<AgentObjectSetup, Object> builder);
  void declareAgentObjectBuilder(String name, RamaFunction1<AgentObjectSetup, Object> builder, AgentObjectOptions options);

  <Input, RefOutput, Output> void declareEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, Output, Map>> builder);
  <Input, RefOutput, Output> void declareEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, Output, Map>> builder,
      EvaluatorBuilderOptions options);

  <Input, RefOutput, Output> void declareComparativeEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, List<Output>, Map>> builder);
  <Input, RefOutput, Output> void declareComparativeEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, Input, RefOutput, List<Output>, Map>> builder,
      EvaluatorBuilderOptions options);

  void declareSummaryEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction2<AgentObjectFetcher, List<ExampleRun>, Map>> builder);
  void declareSummaryEvaluatorBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction2<AgentObjectFetcher, List<ExampleRun>, Map>> builder,
      EvaluatorBuilderOptions options);

   <Input, Output> void declareActionBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, List<Input>, Output, RunInfo, Map>> builder);

   <Input, Output> void declareActionBuilder(
      String name,
      String description,
      RamaFunction1<Map<String, String>,
                    RamaFunction4<AgentObjectFetcher, List<Input>, Output, RunInfo, Map>> builder,
      ActionBuilderOptions options);


  void declareClusterAgent(String localName, String moduleName, String agentName);

  StreamTopology getStreamTopology();

  void define();
}
