# Advanced Patterns

Scale your agents with parallel processing. Integrate AI models seamlessly. These patterns transform simple agents into powerful, intelligent systems.

## Aggregation: Parallel Processing at Scale

Aggregation patterns let you split work across multiple executions, then combine results. Think MapReduce, but simpler and more flexible.

### Basic Aggregation

The foundation: fan out work, then collect results.

**Clojure:**
```clojure
(aor/defagentmodule ParallelSearchModule
  [topology]
  
  (-> topology
      (aor/new-agent "SearchEngine")
      
      ;; Start aggregation - returns correlation ID
      (aor/agg-start-node "search" "search-source"
                          (fn [agent-node query]
                            ;; Define sources to search
                            (let [sources ["database" "cache" "external-api"]]
                              (doseq [source sources]
                                ;; Emit to search-source for each source
                                (aor/emit! agent-node "search-source" source query)))))
      
      ;; Process each source in parallel
      (aor/agg-node "search-source" "combine-results"
                    (fn [agent-node source query correlation-id]
                      ;; Search this source
                      (let [results (search-in source query)]
                        ;; Emit results with correlation ID
                        (aor/emit! agent-node "combine-results" 
                                  correlation-id results))))
      
      ;; Combine all results
      (aor/node "combine-results" nil
                (fn [agent-node correlation-id results]
                  ;; All results arrive here together
                  (let [combined (merge-search-results results)]
                    (aor/result! agent-node combined))))))
```

**Java:**
```java
public class ParallelSearchModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("SearchEngine")
            
            // Start aggregation - returns correlation ID
            .aggStartNode("search", "search-source", (agentNode, query) -> {
                // Define sources to search
                List<String> sources = List.of("database", "cache", "external-api");
                for (String source : sources) {
                    // Emit to search-source for each source
                    agentNode.emit("search-source", source, query);
                }
            })
            
            // Process each source in parallel
            .aggNode("search-source", "combine-results", 
                     (agentNode, source, query, correlationId) -> {
                // Search this source
                List<Object> results = searchIn(source, query);
                // Emit results with correlation ID
                agentNode.emit("combine-results", correlationId, results);
            })
            
            // Combine all results
            .node("combine-results", null, (agentNode, correlationId, results) -> {
                // All results arrive here together
                Map<String, Object> combined = mergeSearchResults(results);
                agentNode.result(combined);
            });
    }
}
```

### Custom Aggregation with Multi-Agg

For complex aggregation logic, use `multi-agg` for full control:

**Clojure:**
```clojure
(aor/defagentmodule StatisticsModule
  [topology]
  
  (-> topology
      (aor/new-agent "StatsCalculator")
      
      ;; Start parallel calculations
      (aor/agg-start-node "calculate" "process-batch"
                          (fn [agent-node dataset]
                            ;; Split dataset into batches
                            (let [batches (partition-all 1000 dataset)]
                              (doseq [batch batches]
                                (aor/emit! agent-node "process-batch" batch)))))
      
      ;; Process each batch
      (aor/agg-node "process-batch" "aggregate-stats"
                    (fn [agent-node batch correlation-id]
                      (let [stats {:count (count batch)
                                  :sum (reduce + batch)
                                  :min (apply min batch)
                                  :max (apply max batch)}]
                        (aor/emit! agent-node "aggregate-stats" 
                                  correlation-id stats))))
      
      ;; Custom aggregation logic
      (aor/multi-agg "aggregate-stats" nil
        ;; Initialize accumulator
        :init (fn [] {:count 0 :sum 0 :min nil :max nil})
        
        ;; Process each incoming result
        :on (fn [accumulator correlation-id stats]
              {:count (+ (:count accumulator) (:count stats))
               :sum (+ (:sum accumulator) (:sum stats))
               :min (min-safe (:min accumulator) (:min stats))
               :max (max-safe (:max accumulator) (:max stats))})
        
        ;; Finalize and emit result
        :complete (fn [agent-node accumulator]
                    (let [mean (/ (:sum accumulator) (:count accumulator))]
                      (aor/result! agent-node 
                        (assoc accumulator :mean mean)))))))
```

**Java:**
```java
public class StatisticsModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        topology.newAgent("StatsCalculator")
            
            // Start parallel calculations
            .aggStartNode("calculate", "process-batch", (agentNode, dataset) -> {
                // Split dataset into batches
                List<List<Double>> batches = partition(dataset, 1000);
                for (List<Double> batch : batches) {
                    agentNode.emit("process-batch", batch);
                }
            })
            
            // Process each batch
            .aggNode("process-batch", "aggregate-stats",
                     (agentNode, batch, correlationId) -> {
                List<Double> numbers = (List<Double>) batch;
                Map<String, Object> stats = Map.of(
                    "count", numbers.size(),
                    "sum", numbers.stream().mapToDouble(Double::doubleValue).sum(),
                    "min", Collections.min(numbers),
                    "max", Collections.max(numbers)
                );
                agentNode.emit("aggregate-stats", correlationId, stats);
            })
            
            // Custom aggregation logic
            .multiAgg("aggregate-stats", null)
                // Initialize accumulator
                .init(() -> new HashMap<String, Object>() {{
                    put("count", 0);
                    put("sum", 0.0);
                    put("min", null);
                    put("max", null);
                }})
                
                // Process each incoming result
                .on((accumulator, correlationId, stats) -> {
                    Map<String, Object> acc = (Map<String, Object>) accumulator;
                    Map<String, Object> st = (Map<String, Object>) stats;
                    
                    acc.put("count", (int)acc.get("count") + (int)st.get("count"));
                    acc.put("sum", (double)acc.get("sum") + (double)st.get("sum"));
                    acc.put("min", minSafe(acc.get("min"), st.get("min")));
                    acc.put("max", maxSafe(acc.get("max"), st.get("max")));
                    return acc;
                })
                
                // Finalize and emit result
                .complete((agentNode, accumulator) -> {
                    Map<String, Object> acc = (Map<String, Object>) accumulator;
                    double mean = (double)acc.get("sum") / (int)acc.get("count");
                    acc.put("mean", mean);
                    agentNode.result(acc);
                });
    }
}
```

## LangChain4j Integration: AI-Powered Agents

Integrate large language models seamlessly. Agent-O-Rama provides first-class support for LangChain4j.

### Basic Chat Integration

Connect to any LangChain4j-supported model:

**Clojure:**
```clojure
(aor/defagentmodule ChatModule
  [topology]
  
  ;; Declare the AI model
  (aor/declare-agent-object-builder topology "chat-model"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (System/getenv "OPENAI_API_KEY"))
          (.modelName "gpt-4")
          (.temperature 0.7)
          .build)))
  
  (-> topology
      (aor/new-agent "ChatBot")
      
      ;; Chat with context
      (aor/node "chat" nil
                (fn [agent-node user-message context]
                  (let [model (aor/get-agent-object agent-node "chat-model")
                        ;; Build conversation history
                        messages [(lc4j/system-message 
                                   "You are a helpful assistant.")
                                 (lc4j/user-message 
                                   (str "Context: " context))
                                 (lc4j/user-message user-message)]
                        ;; Get AI response
                        response (lc4j/chat model 
                                   (lc4j/chat-request messages))]
                    (aor/result! agent-node 
                      {:response (lc4j/get-content response)
                       :tokens (:tokenUsage response)}))))))
```

**Java:**
```java
public class ChatModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Declare the AI model
        topology.declareAgentObjectBuilder("chat-model", setup ->
            OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .temperature(0.7)
                .build()
        );
        
        topology.newAgent("ChatBot")
            
            // Chat with context
            .node("chat", null, (agentNode, userMessage, context) -> {
                ChatLanguageModel model = agentNode.getAgentObject("chat-model");
                
                // Build conversation history
                List<ChatMessage> messages = List.of(
                    SystemMessage.from("You are a helpful assistant."),
                    UserMessage.from("Context: " + context),
                    UserMessage.from((String) userMessage)
                );
                
                // Get AI response
                Response<AiMessage> response = model.generate(messages);
                
                agentNode.result(Map.of(
                    "response", response.content().text(),
                    "tokens", response.tokenUsage()
                ));
            });
    }
}
```

### Structured Output with JSON

Get structured data from LLMs:

**Clojure:**
```clojure
(aor/defagentmodule StructuredExtractionModule
  [topology]
  
  ;; Model configured for JSON output
  (aor/declare-agent-object-builder topology "json-model"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (System/getenv "OPENAI_API_KEY"))
          (.modelName "gpt-4")
          (.responseFormat "json_object")
          .build)))
  
  (-> topology
      (aor/new-agent "DataExtractor")
      
      ;; Extract structured data from text
      (aor/node "extract" nil
                (fn [agent-node text schema]
                  (let [model (aor/get-agent-object agent-node "json-model")
                        prompt (str "Extract data from this text according to schema:\n"
                                   "Schema: " (json/write-value-as-string schema) "\n"
                                   "Text: " text "\n"
                                   "Return valid JSON matching the schema.")
                        response (lc4j/chat model 
                                   (lc4j/chat-request 
                                     [(lc4j/user-message prompt)]))]
                    ;; Parse JSON response
                    (let [extracted (json/read-value 
                                     (lc4j/get-content response))]
                      (aor/result! agent-node extracted)))))))
```

**Java:**
```java
public class StructuredExtractionModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Model configured for JSON output
        topology.declareAgentObjectBuilder("json-model", setup ->
            OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .responseFormat("json_object")
                .build()
        );
        
        topology.newAgent("DataExtractor")
            
            // Extract structured data from text
            .node("extract", null, (agentNode, text, schema) -> {
                ChatLanguageModel model = agentNode.getAgentObject("json-model");
                
                String prompt = String.format(
                    "Extract data from this text according to schema:\n" +
                    "Schema: %s\n" +
                    "Text: %s\n" +
                    "Return valid JSON matching the schema.",
                    toJson(schema), text
                );
                
                Response<AiMessage> response = model.generate(
                    UserMessage.from(prompt)
                );
                
                // Parse JSON response
                Map<String, Object> extracted = parseJson(
                    response.content().text()
                );
                agentNode.result(extracted);
            });
    }
}
```

### Streaming AI Responses

Stream tokens as they're generated:

**Clojure:**
```clojure
(aor/defagentmodule StreamingAIModule
  [topology]
  
  ;; Streaming-capable model
  (aor/declare-agent-object-builder topology "streaming-model"
    (fn [setup]
      (-> (OpenAiStreamingChatModel/builder)
          (.apiKey (System/getenv "OPENAI_API_KEY"))
          (.modelName "gpt-4")
          .build)))
  
  (-> topology
      (aor/new-agent "StreamingChat")
      
      ;; Stream AI response
      (aor/node "generate" nil
                (fn [agent-node prompt]
                  (let [model (aor/get-agent-object agent-node "streaming-model")
                        ;; Token handler streams each chunk
                        handler (reify TokenStreamHandler
                                  (onNext [_ token]
                                    (aor/stream-chunk! agent-node 
                                      {:type "token" :content token}))
                                  (onComplete [_ response]
                                    (aor/stream-chunk! agent-node
                                      {:type "complete" 
                                       :stats (:tokenUsage response)}))
                                  (onError [_ error]
                                    (aor/stream-chunk! agent-node
                                      {:type "error" :message (.getMessage error)})))]
                    ;; Start streaming
                    (.generate model 
                      [(lc4j/user-message prompt)]
                      handler)
                    ;; Return when streaming completes
                    (aor/result! agent-node {:status "complete"}))))))
```

**Java:**
```java
public class StreamingAIModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Streaming-capable model
        topology.declareAgentObjectBuilder("streaming-model", setup ->
            OpenAiStreamingChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .build()
        );
        
        topology.newAgent("StreamingChat")
            
            // Stream AI response
            .node("generate", null, (agentNode, prompt) -> {
                StreamingChatLanguageModel model = 
                    agentNode.getAgentObject("streaming-model");
                
                // Token handler streams each chunk
                model.generate(
                    UserMessage.from((String) prompt),
                    new StreamingResponseHandler<AiMessage>() {
                        @Override
                        public void onNext(String token) {
                            agentNode.streamChunk(Map.of(
                                "type", "token",
                                "content", token
                            ));
                        }
                        
                        @Override
                        public void onComplete(Response<AiMessage> response) {
                            agentNode.streamChunk(Map.of(
                                "type", "complete",
                                "stats", response.tokenUsage()
                            ));
                        }
                        
                        @Override
                        public void onError(Throwable error) {
                            agentNode.streamChunk(Map.of(
                                "type", "error",
                                "message", error.getMessage()
                            ));
                        }
                    }
                );
                
                // Return when streaming completes
                agentNode.result(Map.of("status", "complete"));
            });
    }
}
```

### Tool Calling

Let AI agents use tools to interact with the world:

**Clojure:**
```clojure
(aor/defagentmodule ToolCallingModule
  [topology]
  
  ;; Define tools the AI can use
  (def weather-tool
    (lc4j/tool "get_weather"
               "Get current weather for a location"
               [{:name "location" :type "string" :required true}]
               (fn [params]
                 {:temperature 72 
                  :condition "sunny"
                  :location (:location params)})))
  
  (def calculator-tool
    (lc4j/tool "calculate"
               "Perform mathematical calculations"
               [{:name "expression" :type "string" :required true}]
               (fn [params]
                 {:result (eval-math (:expression params))})))
  
  ;; Model with tools
  (aor/declare-agent-object-builder topology "tools-model"
    (fn [setup]
      (-> (OpenAiChatModel/builder)
          (.apiKey (System/getenv "OPENAI_API_KEY"))
          (.modelName "gpt-4")
          (.tools [weather-tool calculator-tool])
          .build)))
  
  (-> topology
      (aor/new-agent "ToolAgent")
      
      ;; Process with tools
      (aor/node "assist" nil
                (fn [agent-node query]
                  (let [model (aor/get-agent-object agent-node "tools-model")
                        ;; AI decides which tools to use
                        response (lc4j/chat model
                                   (lc4j/chat-request
                                     [(lc4j/user-message query)]))]
                    ;; Check if tools were called
                    (if-let [tool-calls (:toolCalls response)]
                      (let [results (execute-tool-calls tool-calls)]
                        (aor/result! agent-node 
                          {:answer (lc4j/get-content response)
                           :tools-used (map :name tool-calls)
                           :tool-results results}))
                      (aor/result! agent-node
                        {:answer (lc4j/get-content response)})))))))
```

**Java:**
```java
public class ToolCallingModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Define tools the AI can use
        Tool weatherTool = Tool.builder()
            .name("get_weather")
            .description("Get current weather for a location")
            .addParameter("location", JsonSchemaProperty.STRING)
            .executor(params -> Map.of(
                "temperature", 72,
                "condition", "sunny",
                "location", params.get("location")
            ))
            .build();
        
        Tool calculatorTool = Tool.builder()
            .name("calculate")
            .description("Perform mathematical calculations")
            .addParameter("expression", JsonSchemaProperty.STRING)
            .executor(params -> Map.of(
                "result", evalMath(params.get("expression"))
            ))
            .build();
        
        // Model with tools
        topology.declareAgentObjectBuilder("tools-model", setup ->
            OpenAiChatModel.builder()
                .apiKey(System.getenv("OPENAI_API_KEY"))
                .modelName("gpt-4")
                .tools(List.of(weatherTool, calculatorTool))
                .build()
        );
        
        topology.newAgent("ToolAgent")
            
            // Process with tools
            .node("assist", null, (agentNode, query) -> {
                ChatLanguageModel model = agentNode.getAgentObject("tools-model");
                
                // AI decides which tools to use
                Response<AiMessage> response = model.generate(
                    UserMessage.from((String) query)
                );
                
                // Check if tools were called
                if (response.content().hasToolExecutionRequests()) {
                    List<ToolExecutionRequest> toolCalls = 
                        response.content().toolExecutionRequests();
                    Map<String, Object> results = executeToolCalls(toolCalls);
                    
                    agentNode.result(Map.of(
                        "answer", response.content().text(),
                        "tools-used", toolCalls.stream()
                            .map(ToolExecutionRequest::name)
                            .collect(Collectors.toList()),
                        "tool-results", results
                    ));
                } else {
                    agentNode.result(Map.of(
                        "answer", response.content().text()
                    ));
                }
            });
    }
}
```

## Combining Patterns: Parallel AI Processing

Combine aggregation with AI for powerful patterns:

**Clojure:**
```clojure
(aor/defagentmodule ParallelAIAnalysisModule
  [topology]
  
  ;; Multiple specialized models
  (aor/declare-agent-object-builder topology "sentiment-model"
    (fn [setup] (create-sentiment-model)))
  
  (aor/declare-agent-object-builder topology "summary-model"
    (fn [setup] (create-summary-model)))
  
  (aor/declare-agent-object-builder topology "entity-model"
    (fn [setup] (create-entity-extraction-model)))
  
  (-> topology
      (aor/new-agent "DocumentAnalyzer")
      
      ;; Start parallel analysis
      (aor/agg-start-node "analyze" "run-analysis"
                          (fn [agent-node document]
                            ;; Run multiple analyses in parallel
                            (aor/emit! agent-node "run-analysis" 
                                      "sentiment" document)
                            (aor/emit! agent-node "run-analysis"
                                      "summary" document)
                            (aor/emit! agent-node "run-analysis"
                                      "entities" document)))
      
      ;; Run each analysis type
      (aor/agg-node "run-analysis" "combine"
                    (fn [agent-node analysis-type document correlation-id]
                      (let [model-key (str analysis-type "-model")
                            model (aor/get-agent-object agent-node model-key)
                            result (analyze-with model document)]
                        (aor/emit! agent-node "combine"
                                  correlation-id
                                  {analysis-type result}))))
      
      ;; Combine all results
      (aor/multi-agg "combine" nil
        :init (fn [] {})
        :on (fn [acc correlation-id result]
              (merge acc result))
        :complete (fn [agent-node combined]
                    ;; All analyses complete
                    (aor/result! agent-node combined)))))
```

## What's Next?

You've mastered advanced patterns. Finally, explore system-level features in [System Features](05-system-features.md).