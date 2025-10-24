# Coding Agents in Agent-o-rama

This page explains how to code agents with Agent-o-rama. All examples are shown in both Java and Clojure.

## Table of Contents

1. [Basic Concepts](#basic-concepts)
2. [Nodes, Emits, and Results](#nodes-emits-and-results)
3. [Routing in Agent Graphs](#routing-in-agent-graphs)
4. [Aggregation Subgraphs](#aggregation-subgraphs)
5. [Metadata](#metadata)
6. [Agent Objects](#agent-objects)
7. [Stores](#stores)
8. [Subagents and Recursion](#subagents-and-recursion)
9. [Human Input](#human-input)
10. [Streaming Data](#streaming-data)
11. [Deploying Modules](#deploying-modules)
12. [Updating Modules](#updating-modules)

## Basic Concepts

Agent-o-rama is a library for building AI agents as directed graphs. Nodes are the fundamental computation units in agent graphs. Each node is a plain Java or Clojure function that receives data, processes it, and either passes it along to other nodes or returns a final result. This is the basic building block that enables all other agent patterns. Agent-o-rama executes all nodes on [virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), which means node functions can be long-running and written in a blocking style without wasting thread resources.

Agent-o-rama captures all inputs, nested operations (e.g. model calls or database operations), and outputs from each node for viewing in the web UI. This information is also used and for to produce and display aggregated analytics about individual agent executions and time-series analytics for all agent executions.

Besides tracing, nodes are also the granularity at which streaming is consumed by agent clients. Things like calls to [Langchain4j](https://docs.langchain4j.dev/) models are automatically streamed for the node, and node functionsd can explicitly stream chunks back as well. This is discussed more on the [agent client](TODO) page.

### Key Components

- **AgentGraph**: The builder interface for defining agent execution graphs
- **AgentNode**: The interface for interacting with the agent execution environment from within nodes
- **AgentTopology**: The interface for defining agents, stores, and objects
- **AgentClient**: The interface for invoking agents and managing executions

### Understanding the Flow

Every agent execution starts with an invocation that provides input data to the first node. From there, data flows through the graph via `emit()` calls, which send data to downstream nodes. The execution continues until a node calls `result()`, which terminates the agent and returns the final output.

The `outputNodesSpec` parameter when defining nodes is crucial - it declares which nodes can receive data from this node. This creates a contract that the runtime enforces, preventing errors from emitting to undeclared nodes.

### Simple Example: Greeting Pipeline

This example shows a basic two-node pipeline where the first node processes the input and the second node creates the final result.

#### Java API

```java
import com.rpl.agentorama.*;

public class BasicAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("BasicAgent")
            .node("start", "process", (AgentNode agentNode, String input) -> {
                agentNode.emit("process", "Hello " + input);
            })
            .node("process", null, (AgentNode agentNode, String data) -> {
                agentNode.result("Processed: " + data);
            });
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor])

(aor/defagentmodule BasicAgentModule
  [topology]
  (-> (aor/new-agent topology "BasicAgent")
      (aor/node
       "start"
       "process"
       (fn [agent-node input]
         (aor/emit! agent-node "process" (str "Hello " input))))
      (aor/node
       "process"
       nil
       (fn [agent-node data]
         (aor/result! agent-node (str "Processed: " data))))))
```

### Key Concepts

- **emit()**: Sends data to another node in the agent graph
- **result()**: Sets the final result of the agent execution (first-one-wins)
- **outputNodesSpec**: Declares which nodes can receive emissions from this node. This is either a single node name string, a list of node names, or null to indicate a terminal node.

## Routing in Agent Graphs

While simple linear pipelines are useful, real-world agents often need complex control flow. Agent graphs support loops, conditional routing, and multiple execution paths that can reconverge. This enables sophisticated decision-making and parallel processing within a single agent.

### Conditional Routing Example

This example demonstrates how an agent can route different types of messages through different processing paths, then reconverge to a single result. In this example each node emits only once, but the first node can emit to one of two nodes. In either cases, they reconverge to the node "finalize" which emits the final result.

#### Java API

```java
public class RouterAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("RouterAgent")
            .node("route", new String[]{"handle-urgent", "handle-default"},
                  (AgentNode agentNode, String message) -> {
              if (message.startsWith("urgent:")) {
                  agentNode.emit("handle-urgent", message);
              } else {
                  agentNode.emit("handle-default", message);
              }
            })
            .node("handle-urgent", "finalize", (AgentNode agentNode, String message) -> {
              String content = message.substring(7);
              agentNode.emit("finalize", Map.of("priority", "HIGH", "message", content));
            })
            .node("handle-default", "finalize", (AgentNode agentNode, String message) -> {
              agentNode.emit("finalize", Map.of("priority", "NORMAL", "message", message));
            })
            .node("finalize", null, (AgentNode agentNode, Map<String, String> data) -> {
              String result = String.format("[%s] %s", data.get("priority"), data.get("message"));
              agentNode.result(result);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule RouterAgentModule
  [topology]
  (-> (aor/new-agent topology "RouterAgent")
      (aor/node
       "route"
       ["handle-urgent" "handle-default"]
       (fn [agent-node message]
         (if (str/starts-with? message "urgent:")
           (aor/emit! agent-node "handle-urgent" message)
           (aor/emit! agent-node "handle-default" message))))
      (aor/node
       "handle-urgent"
       "finalize"
       (fn [agent-node message]
         (aor/emit! agent-node "finalize" {:priority "HIGH" :message (subs message 7)})))
      (aor/node
       "handle-default"
       "finalize"
       (fn [agent-node message]
         (aor/emit! agent-node "finalize" {:priority "NORMAL" :message message})))
      (aor/node
       "finalize"
       nil
       (fn [agent-node {:keys [priority message]}]
         (aor/result! agent-node (format "[%s] %s" priority message))))))
```


### Emitting Multiple Times

When a node emits multiple times, the first emit runs on the same node/thread, but subsequent emits will run in parallel on other threads or even other nodes. This means agent graphs automatically parallelize and distribute execution, which is powerful for performance but requires consideration if nodes might access the same resources (e.g. a database) in parallel. A node can emit any number of times to any number of downstream nodes.

If multiple nodes call `result()`, only the first one wins – subsequent results are ignored. This "first-wins" behavior is useful when you want to try multiple approaches and return the first successful result for expediency. That said, most agents will only call `result()` once and any parallel processing triggered by multiple emits will be combined with [agggregation](#aggregation-subgraphs).

#### Java API

```java
public class MultiEmitAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("MultiEmitAgent")
            .node("start", new String[]{"process-a", "process-b"}, (AgentNode agentNode, String input) -> {
              // Emit to multiple nodes in parallel
              agentNode.emit("process-a", input + "-A1");
              agentNode.emit("process-b", input + "-B");
              agentNode.emit("process-a", input + "-A2");
            })
            .node("process-a", "finalize", (AgentNode agentNode, String data) -> {
              // Simulate some work
              Thread.sleep(100);
              agentNode.emit("finalize", "Result A: " + data);
            })
            .node("process-b", "finalize", (AgentNode agentNode, String data) -> {
              // Simulate some work
              Thread.sleep(50);
              agentNode.emit("finalize", "Result B: " + data);
            })
            .node("finalize", null, (AgentNode agentNode, String result) -> {
              agentNode.result(result);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule MultiEmitAgentModule
  [topology]
  (-> (aor/new-agent topology "MultiEmitAgent")
      (aor/node
       "start"
       ["process-a" "process-b"]
       (fn [agent-node input]
         ;; Emit to both processing nodes in parallel
         (aor/emit! agent-node "process-a" (str input "-A"))
         (aor/emit! agent-node "process-b" (str input "-B"))
         (aor/emit! agent-node "process-a" (str input "-A"))))
      (aor/node
       "process-a"
       "finalize"
       (fn [agent-node data]
         ;; Simulate some work
         (Thread/sleep 100)
         (aor/emit! agent-node "finalize" (str "Result A: " data))))
      (aor/node
       "process-b"
       "finalize"
       (fn [agent-node data]
         ;; Simulate some work
         (Thread/sleep 50)
         (aor/emit! agent-node "finalize" (str "Result B: " data))))
      (aor/node
       "finalize"
       nil
       (fn [agent-node result]
         (aor/result! agent-node result)))))
```

## Aggregation Subgraphs

Aggregation subgraphs enable fan-out/fan-in patterns where work is distributed to multiple parallel nodes and results are collected and combined. This is essential for handling multiple concurrent operations, like making multiple LLM calls in parallel (since they're slow) and then combining the results.

### Basic Aggregation Example

This example shows how to distribute work across multiple parallel processors and then collect the results. The agg node runs once the subgraph preceding it has finished running/emitting.

#### Java API

```java
public class AggregationAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("AggregationAgent")
            .aggStartNode("distribute-work", "process-item", (AgentNode agentNode, List<String> items) -> {
              // Emit each item for parallel processing
              for (String item: items) {
                  agentNode.emit("process-item", item);
              }
              return null;
            })
            .node("process-item", "collect-results", (AgentNode agentNode, String item) -> {
              // Simulate processing each item
              String processed = "Processed: " + item.toUpperCase();
              agentNode.emit("collect-results", processed);
            })
            .aggNode("collect-results", null, BuiltIn.LIST_AGG,
                     (AgentNode agentNode, List<String> results, Object nodeStartRes) -> {
              agentNode.result(results);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule AggregationAgentModule
  [topology]
  (-> (aor/new-agent topology "AggregationAgent")
      (aor/agg-start-node
       "distribute-work"
       "process-item"
       (fn [agent-node items]
         ;; Emit each item for parallel processing
         (doseq [item items]
           (aor/emit! agent-node "process-item" item))))
      (aor/node
       "process-item"
       "collect-results"
       (fn [agent-node item]
         ;; Simulate processing each item
         (let [processed (str "Processed: " (str/upper-case item))]
           (aor/emit! agent-node "collect-results" processed))))
      (aor/agg-node
       "collect-results"
       nil
       aggs/+vec-agg
       (fn [agent-node results _]
         (aor/result! agent-node results)))))
```

### Aggregation Scope

Aggregation subgraphs can be nested, where each invocation of an agg start node creates a new aggregation context. This means you can have a first agg start node that emits multiple times to another agg start node, and the nested aggregation results get collected into the outer aggregation context.

For example, imagine processing multiple documents where each document needs to be analyzed by multiple experts in parallel, then the expert results for each document need to be combined, and finally all document results need to be aggregated together.

Agg start nodes are the only nodes that have return values. The return value is passed as the last argument to the corresponding agg node, allowing you to pass non-aggregated information through the aggregation.

#### Java API

```java
public class NestedAggregationModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("NestedAggregationAgent")
            // Outer aggregation: process multiple documents
            .aggStartNode("distribute-docs", "analyze-doc", (AgentNode agentNode, List<String> docs) -> {
              for (String doc : docs) {
                agentNode.emit("analyze-doc", doc);
              }
              return docs.size(); // Return value passed to outer agg node
            })
            // Inner aggregation: analyze each document with multiple methods
            .aggStartNode("analyze-doc", "analyze-method", (AgentNode agentNode, String doc) -> {
              agentNode.emit("analyze-method", doc, "sentiment");
              agentNode.emit("analyze-method", doc, "keywords");
              agentNode.emit("analyze-method", doc, "summary");
              return doc; // Return value passed to inner agg node
            })
            .node("analyze-method", "combine-analysis", (AgentNode agentNode, String doc, String method) -> {
              String result = method + " analysis of: " + doc;
              agentNode.emit("combine-analysis", Map.of("method", method, "result", result));
            })
            // Inner agg node: combine analyses for one document
            .aggNode("combine-analysis", "collect-docs", BuiltIn.LIST_AGG,
                     (AgentNode agentNode, List<Map<String, String>> analyses, String originalDoc) -> {
              agentNode.emit("collect-docs", Map.of("doc", originalDoc, "analyses", analyses));
            })
            // Outer agg node: collect all document results
            .aggNode("collect-docs", null, BuiltIn.LIST_AGG,
                     (AgentNode agentNode, List<Map<String, Object>> allResults, Integer totalDocs) -> {
              agentNode.result(Map.of("total-docs", totalDocs, "results", allResults));
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule NestedAggregationModule
  [topology]
  (-> (aor/new-agent topology "NestedAggregationAgent")
      ;; Outer aggregation: process multiple documents
      (aor/agg-start-node
       "distribute-docs"
       "analyze-doc"
       (fn [agent-node docs]
         (doseq [doc docs]
           (aor/emit! agent-node "analyze-doc" doc))
         (count docs))) ; Return value passed to outer agg node
      ;; Inner aggregation: analyze each document with multiple methods
      (aor/agg-start-node
       "analyze-doc"
       "analyze-method"
       (fn [agent-node doc]
         (aor/emit! agent-node "analyze-method" doc "sentiment")
         (aor/emit! agent-node "analyze-method" doc "keywords")
         (aor/emit! agent-node "analyze-method" doc "summary")
         doc)) ; Return value passed to inner agg node
      (aor/node
       "analyze-method"
       "combine-analysis"
       (fn [agent-node doc method]
         (let [result (str method " analysis of: " doc)]
           (aor/emit! agent-node "combine-analysis" {:method method :result result}))))
      ;; Inner agg node: combine analyses for one document
      (aor/agg-node
       "combine-analysis"
       "collect-docs"
       aggs/+vec-agg
       (fn [agent-node analyses original-doc]
         (aor/emit! agent-node "collect-docs" {:doc original-doc :analyses analyses})))
      ;; Outer agg node: collect all document results
      (aor/agg-node
       "collect-docs"
       nil
       aggs/+vec-agg
       (fn [agent-node all-results total-docs]
         (aor/result! agent-node {:total-docs total-docs :results all-results})))))
```

### Custom Aggregators

Built-in aggregators handle most use cases, but sometimes you need custom logic on how to aggregate inputs. You can do that by defining custom Rama aggregators, which is explained [here for Java](https://redplanetlabs.com/docs/~/aggregators.html#_defining_aggregators) and [here for Clojure](https://redplanetlabs.com/docs/~/clj-dataflow-lang.html#_aggregators).

Agent-o-rama also has a special aggregator type called "multi aggregator" which can process different kinds of inputs. When using multi-aggregators, aggregation inputs specify which "target" to run by including a tag as the first argument to `emit()`. The multi-agg then routes each input to the appropriate handler based on this tag.

This example shows how to process different types of data (numbers and text) with different logic, then combine the results.

#### Java API

```java
public class MultiAggAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("MultiAggAgent")
            .aggStartNode("distribute-data", Arrays.asList("process-numbers", "process-text"),
                          (AgentNode agentNode, Map<String, Object> data) -> {
              List<Integer> numbers = (List<Integer>) data.get("numbers");
              List<String> text = (List<String>) data.get("text");

              for (Integer num : numbers) {
                  agentNode.emit("process-numbers", num);
              }
              for (String txt : text) {
                  agentNode.emit("process-text", txt);
              }
              return null;
            })
            .node("process-numbers", "combine-results", (AgentNode agentNode, Integer number) -> {
              agentNode.emit("combine-results", "number", number);
            })
            .node("process-text", "combine-results", (AgentNode agentNode, String text) -> {
              agentNode.emit("combine-results", "text", text);
            })
            .aggNode("combine-results", null,
                     MultiAgg.init(() -> {
                         Map<String, Object> state = new HashMap<>();
                         state.put("number-sum", 0);
                         state.put("text", "");
                         return state;
                     })
                     .on("number", (Map<String, Object> state, Integer num) -> {
                         state.put("number-sum", (Integer) state.get("number-sum") + num);
                         return state;
                     })
                     .on("text", (Map<String, Object> state, String txt) -> {
                         state.put("text", state.get("text") + txt + " ");
                         return state;
                     }),
                     (AgentNode agentNode, Map<String, Object> state, Object _) -> {
              agentNode.result(state);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule MultiAggAgentModule
  [topology]
  (-> (aor/new-agent topology "MultiAggAgent")
      (aor/agg-start-node
       "distribute-data"
       ["process-numbers" "process-text"]
       (fn [agent-node {:keys [numbers text]}]
         (doseq [num numbers] (aor/emit! agent-node "process-numbers" num))
         (doseq [txt text] (aor/emit! agent-node "process-text" txt))))
      (aor/node
       "process-numbers"
       "combine-results"
       (fn [agent-node number]
         (aor/emit! agent-node "combine-results" "number" number)))
      (aor/node
       "process-text"
       "combine-results"
       (fn [agent-node text]
         (aor/emit! agent-node "combine-results" "text" text)))
      (aor/agg-node
       "combine-results"
       nil
       (aor/multi-agg
        (init [] {:number-sum 0 :text ""})
        (on "number" [state num] (update state :number-sum + num))
        (on "text" [state txt] (update state :text str txt " ")))
       (fn [agent-node state _]
         (aor/result! agent-node state)))))
```

### Early Aggregation Return

Aggregators can be written to return early, which causes aggregation to immediately finish (before all incoming data has been processed) and run the agg node. In Clojure, this is done by returning a value wrapped in `reduced`, and in Java with `FinishedAgg`. This is useful when you want to stop processing as soon as you have enough data or when a certain condition is met.

#### Java API

```java
import com.rpl.agentorama.FinishedAgg;
import com.rpl.rama.ops.RamaAccumulatorAgg1;

// Custom aggregator that stops when sum exceeds 100
public class SumUntil100 implements RamaAccumulatorAgg1<Integer, Integer> {
  @Override
  public Integer initVal() {
    return 0;
  }

  @Override
  public Integer accumulate(Integer curr, Integer value) {
    Integer newSum = curr + value;
    if (newSum > 100) {
      return new FinishedAgg(newSum); // Stop aggregating early
    }
    return newSum;
  }
}
```

#### Clojure API

```clojure
;; Custom aggregator that stops when sum exceeds 100
(def +sum-until-100
  (accumulator
   (fn [v]
     (term (fn [curr]
             (let [ret (+ curr v)]
               (if (> ret 100)
                 (reduced ret) ; Stop aggregating early
                 ret
               ))
           )))
   :init-fn
   (constantly 0)))
```


## Metadata

Metadata allows you to attach custom key-value data to agent executions. Metadata is set when invoking an agent and can be accessed from any node within the agent execution.

Common use cases for metadata include:

- **Tracking**: User IDs, session IDs, request IDs for correlating agent executions with application events
- **A/B Testing**: Feature flags, model versions, or experimental configurations
- **Configuration**: Runtime parameters that affect agent behavior without changing code, like model names to use
- **Debugging**: Additional context for troubleshooting specific executions

Metadata is automatically included in traces and analytics, making it easy to filter and analyze agent performance by any metadata dimension.

### Setting Metadata

Metadata is set when invoking an agent using the "with context" methods. Metadata keys must be strings, and values must be strings, numbers (int, long, float, double), or booleans.

Here's a quick example of invoking an agent with metadata:

#### Java API

```java
import com.rpl.agentorama.AgentContext;

// Create context with metadata
AgentContext context = AgentContext.metadata("user-id", "user-123")
                                   .metadata("model", "gpt-4");

// Invoke with context
String result = agent.invokeWithContext(context, "Hello, world!");
```

#### Clojure API

```clojure
;; Create context with metadata
(let [context {:metadata {"user-id" "user-123"
                          "model" "gpt-4"}}]

  ;; Invoke with context
  (let [result (aor/agent-invoke-with-context agent context "Hello, world!")]
    (println "Result:" result)))
```

### Accessing Metadata in Agents

Inside agent nodes, you can access the metadata using `getMetadata` / `get-metadata`. The metadata is immutable during execution – it reflects what was set at invocation time even if its modified after initiation through the UI or API.

#### Java API

```java
public class MetadataAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("MetadataAgent")
            .node("process", null, (AgentNode agentNode) -> {
              // Get metadata
              Map<String, Object> metadata = agentNode.getMetadata();

              String userId = (String) metadata.get("user-id");
              String model = (String) metadata.get("model");

              System.out.println("Processing for user: " + userId);
              System.out.println("Using model: " + model);

              agentNode.result("Processed for " + userId);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule MetadataAgentModule
  [topology]
  (-> (aor/new-agent topology "MetadataAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node]
         ;; Get metadata
         (let [metadata (aor/get-metadata agent-node)
               user-id (get metadata "user-id")
               model (get metadata "model")]

           (println "Processing for user:" user-id)
           (println "Using model:" model)

           (aor/result! agent-node (str "Processed for " user-id)))))))
```

## Agent Objects

Agent objects are shared resources like AI models, database connections, or API clients that agents can access during execution. They enable agents to interact with external systems and maintain expensive resources efficiently. Many resources like AI models and database connections are expensive to create and maintain persistent connections. Agent object builders allow you to create these resources once and reuse them across multiple agent invocations, rather than recreating them for every agent execution.

### Thread Safety and Pooling

Agent objects are all about thread safety. There are two modes:

1. **Thread-safe objects**: When declared with `threadSafe()`, one object is built for the entire process and reused across all node invokes on all threads. Use this if you know the object you're creating (like a database client) is thread-safe.

2. **Pooled objects**: By default, a pool of objects is maintained, and nodes get exclusive access to an instance during execution. When the node finishes, the object goes back into the pool. The pool size can be configured with the `workerObjectLimit(amt)` option (defaults to 100).

This ensures that your agents can safely use shared resources without worrying about concurrency issues.

### Static and Dynamic Objects

This example shows both static objects (like API keys) and dynamic objects (like AI models that need to be built with configuration). Static objects are created once and shared, while dynamic objects are built on-demand with proper pooling and thread safety.

#### Java API

```java
public class AgentObjectsModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    // Declare static agent object
    topology.declareAgentObject("openai-api-key", System.getenv("OPENAI_API_KEY"));

    // Declare agent object builder
    topology.declareAgentObjectBuilder("openai-model", setup -> {
      String apiKey = setup.getAgentObject("openai-api-key");
      return OpenAiStreamingChatModel.builder()
                                    .apiKey(apiKey)
                                    .modelName("gpt-4o-mini")
                                    .build();
      },
      AgentObjectOptions.workerObjectLimit(200));

    topology.newAgent("AgentWithObjects")
            .node("process", null, (AgentNode agentNode, String input) -> {
              ChatModel model = agentNode.getAgentObject("openai-model");
              String response = model.chat(input);
              agentNode.result(response);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule AgentObjectsModule
  [topology]
  ;; Declare static agent object
  (aor/declare-agent-object topology "openai-api-key" (System/getenv "OPENAI_API_KEY"))

  ;; Declare agent object builder
  (aor/declare-agent-object-builder
   topology
   "openai-model"
   (fn [setup]
     (-> (OpenAiStreamingChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build))
    {:worker-object-limit 200})

  (-> (aor/new-agent topology "AgentWithObjects")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [model (aor/get-agent-object agent-node "openai-model")]
           (aor/result! agent-node (aor/chat model input)))))))
```


### Advanced Object Configuration

Agent objects support several configuration options:

- **Pool size**: Control the maximum number of objects in the pool with `workerObjectLimit`
- **Thread safety**: Mark objects as thread-safe with `threadSafe` to share a single instance
- **Auto-tracing**: LangChain4j chat models and embedding stores are automatically wrapped and traced, but this can be turned off with the `autoTracing` option

### Streaming Chat Models

When you declare a `StreamingChatModel` as an agent object, Agent-o-rama automatically captures the stream and forwards chunks to the node. However, when you fetch the object in a node, you always get a `ChatModel` interface (not `StreamingChatModel`). This means you can use streaming models in a blocking style within agent nodes, while agent clients can stream the node to get the stream of all model calls. If you don't want streaming behavior, declare the object as a non-streaming `ChatModel`.

#### Java API

```java
// Declare streaming model
topology.declareAgentObjectBuilder("streaming-model", setup -> {
  return OpenAiStreamingChatModel.builder()
                                 .apiKey(apiKey)
                                 .modelName("gpt-4")
                                 .build();
});

// In node: fetch as ChatModel (not StreamingChatModel)
topology.newAgent("MyAgent")
        .node("process", null, (AgentNode agentNode, String input) -> {
          ChatModel model = agentNode.getAgentObject("streaming-model"); // Always ChatModel
          String response = model.chat(input); // Blocking call, but streaming happens automatically
          agentNode.result(response);
        });

// Non-streaming model - no streaming behavior
topology.declareAgentObjectBuilder("blocking-model", setup -> {
  return OpenAiChatModel.builder()
                        .apiKey(apiKey)
                        .modelName("gpt-4")
                        .build();
});
```

#### Clojure API

```clojure
;; Declare streaming model
(aor/declare-agent-object-builder
 topology
 "streaming-model"
 (fn [setup]
   (-> (OpenAiStreamingChatModel/builder)
       (.apiKey api-key)
       (.modelName "gpt-4")
       .build)))

;; In node: fetch as ChatModel (not StreamingChatModel)
(-> (aor/new-agent topology "MyAgent")
    (aor/node
     "process"
     nil
     (fn [agent-node input]
       (let [model (aor/get-agent-object agent-node "streaming-model")] ; Always ChatModel
         (aor/result! agent-node (aor/chat model input)))))) ; Blocking call, but streaming happens automatically

;; Non-streaming model - no streaming behavior
(aor/declare-agent-object-builder
 topology
 "blocking-model"
 (fn [setup]
   (-> (OpenAiChatModel/builder)
       (.apiKey api-key)
       (.modelName "gpt-4")
       .build)))
```

## Stores

Real agents need to remember information, maintain user sessions, cache results, and share data between executions. Agent-o-rama stores provide persistent data access for agents, enabling them to maintain state across invocations and share data between different agent executions. Stores are built-in and are high-performance, durable, scalable, and replicated. Because they're built-in, they require no additional work for deployment or configuration. While it's easy to use databases from Agent-o-rama, it's usually much more convenient and higher performance to just use a store.

There are three types of stores available in Agent-o-rama: key-value store, document store, and [PState](https://redplanetlabs.com/docs/~/pstates.html) store. Stores are declared as part of the agent module definition, and store names always begin with `$$`. Stores are fetched within agent nodes by calling `getStore`.

### Key-Value Store Example

Key-value stores are perfect for simple data like counters, flags, or cached values.

#### Java API

```java
public class KeyValueStoreModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareKeyValueStore("$$counters", String.class, Integer.class);

    topology.newAgent("KeyValueStoreAgent")
            .node("manage-counter", null, (AgentNode agentNode, String counterName, String operation) -> {
              KeyValueStore<String, Integer> store = agentNode.getStore("$$counters");
              switch (operation) {
                  case "get":
                      Integer value = store.get(counterName);
                      agentNode.result(Map.of("counter", counterName, "value", value));
                      break;
                  case "increment":
                      Integer currentValue = store.get(counterName);
                      if (currentValue == null) currentValue = 0;
                      store.put(counterName, currentValue + 1);
                      agentNode.result(Map.of("counter", counterName, "new-value", currentValue + 1));
                      break;
              }
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule KeyValueStoreModule
  [topology]
  (aor/declare-key-value-store topology "$$counters" String Long)

  (-> (aor/new-agent topology "KeyValueStoreAgent")
      (aor/node
       "manage-counter"
       nil
       (fn [agent-node counter-name operation]
         (let [store (aor/get-store agent-node "$$counters")]
           (case operation
             "get"
             (aor/result! agent-node {:counter counter-name :value (store/get store counter-name)})
             "increment"
             (let [current-value (or (store/get store counter-name) 0)
                   new-value (inc current-value)]
               (store/put! store counter-name new-value)
               (aor/result! agent-node {:counter counter-name :new-value new-value}))))))))
```

### Document Store Example

Document stores are essentially key-value stores where the values are maps with their own schema for each field. You can perform operations on individual nested values without reading or writing the entire document. This is ideal for structured data with multiple fields, like user profiles.

#### Java API

```java
public class DocumentStoreModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareDocumentStore("$$user-profiles", String.class,
                                   "name", String.class,
                                   "age", Long.class);

    topology.newAgent("DocumentStoreAgent")
            .node("update-profile", "read-profile", (AgentNode agentNode, Map<String, Object> data) -> {
              DocumentStore store = agentNode.getStore("$$user-profiles");
              String userId = (String) data.get("user-id");
              Map<String, Object> updates = (Map<String, Object>) data.get("updates");

              if (updates.containsKey("name")) {
                store.putDocumentField(userId, "name", updates.get("name"));
              }
              if (updates.containsKey("age")) {
                store.putDocumentField(userId, "age", updates.get("age"));
              }

              agentNode.emit("read-profile", userId);
            })
            .node("read-profile", null, (AgentNode agentNode, String userId) -> {
              DocumentStore store = agentNode.getStore("$$user-profiles");
              String name = store.getDocumentField(userId, "name");
              Long age = store.getDocumentField(userId, "age");
              agentNode.result(Map.of("user-id", userId, "name", name, "age", age));
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule DocumentStoreModule
  [topology]
  (aor/declare-document-store topology "$$user-profiles" String
                              "name" String
                              "age" Long)

  (-> (aor/new-agent topology "DocumentStoreAgent")
      (aor/node
       "update-profile"
       "read-profile"
       (fn [agent-node {:keys [user-id updates]}]
         (let [store (aor/get-store agent-node "$$user-profiles")]
           (when (:name updates) (store/put-document-field! store user-id "name" (:name updates)))
           (when (:age updates) (store/put-document-field! store user-id "age" (:age updates)))
           (aor/emit! agent-node "read-profile" user-id))))
      (aor/node
       "read-profile"
       nil
       (fn [agent-node user-id]
         (let [store (aor/get-store agent-node "$$user-profiles")
               name (store/get-document-field store user-id "name")
               age (store/get-document-field store user-id "age")]
           (aor/result! agent-node {:user-id user-id :name name :age age}))))))
```

### PState Store Example

PState stores provide direct access to Rama's [PStates](https://redplanetlabs.com/docs/~/pstates.html). PStates are declared as any combination of data structures of any size with any amount of nesting. PState stores are extremely flexible and are used when you need more sophisticated structures than key-value or document stores provide, such as nested maps, lists with subindexing, or complex hierarchical data.

#### Java API

```java
import com.rpl.rama.Path;
import com.rpl.rama.PState;

public class PStateStoreModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    // Declare PState store with nested schema
    topology.declarePStateStore(
      "$$user-data",
      PState.mapSchema(
        String.class,  // user-id
        PState.fixedKeysSchema(
          "age", Integer.class,
          "memories", PState.listSchema(String.class).subindexed())));

    topology.newAgent("PStateStoreAgent")
            .node("update-user", "read-user", (AgentNode agentNode, Map<String, Object> data) -> {
              PStateStore store = agentNode.getStore("$$user-data");
              String userId = (String) data.get("user-id");
              Integer age = (Integer) data.get("age");
              String memory = (String) data.get("memory");

              // Update age if provided
              if (age != null) {
                store.transform(userId, Path.key(userId, "age").termVal(age));
              }

              // Append memory if provided
              if (memory != null) {
                store.transform(userId, Path.key(userId, "memories").afterElem().termVal(memory));
              }

              agentNode.emit("read-user", userId);
            })
            .node("read-user", null, (AgentNode agentNode, String userId) -> {
              PStateStore store = agentNode.getStore("$$user-data");

              // Read age
              Integer age = (Integer) store.selectOne(Path.key(userId, "age"));

              // Read all memories
              List<String> memories = store.select(Path.key(userId, "memories").all());

              agentNode.result(Map.of("user-id", userId, "age", age, "memories", memories));
            });
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.rama.path :as path])

(aor/defagentmodule PStateStoreModule
  [topology]
  ;; Declare PState store with nested schema
  (aor/declare-pstate-store
   topology
   "$$user-data"
   {String (fixed-keys-schema
            {:age Long
             :memories (vector-schema String {:subindex? true})})})

  (-> topology
      (aor/new-agent "PStateStoreAgent")
      (aor/node
       "update-user"
       "read-user"
       (fn [agent-node {:keys [user-id age memory]}]
         (let [store (aor/get-store agent-node "$$user-data")]
           ;; Update age if provided
           (when age
             (store/pstate-transform!
              [(path/keypath user-id :age) (path/termval age)]
              store
              user-id))

           ;; Append memory if provided
           (when memory
             (store/pstate-transform!
              [(path/keypath user-id :memories) AFTER-ELEM (path/termval memory)]
              store
              user-id))

           (aor/emit! agent-node "read-user" user-id))))
      (aor/node
       "read-user"
       nil
       (fn [agent-node user-id]
         (let [store (aor/get-store agent-node "$$user-data")
               ;; Read age using path
               age (store/pstate-select-one (path/keypath user-id :age) store user-id)
               ;; Read all memories using path
               memories (store/pstate-select [(path/keypath user-id :memories) ALL] store user-id)]
           (aor/result! agent-node {:user-id user-id :age age :memories memories}))))))
```

## Subagents and Recursion

Agents can call other agents within the same module or across modules, including recursively and mutually recursively. This makes it trivial to orchestrate complex applications consisting of many agents working together.

Real-world systems often need to break down complex tasks into smaller, manageable pieces. Subagents enable this decomposition while maintaining the benefits of the agent execution model. They also enable recursive patterns for algorithms that naturally decompose into smaller instances of the same problem.

### Calling Agents in the Same Module

The simplest form of subagent invocation is calling another agent within the same module. You get an agent client within node functions and invoke them directly. Subagent calls are also tracked in traces and incorporated into agent analytics.

#### Java API

```java
public class SubagentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    // Helper agent that processes text
    topology.newAgent("TextProcessor")
            .node("process", null, (AgentNode agentNode, String text) -> {
              String processed = text.toUpperCase();
              agentNode.result(processed);
            });

    // Main agent that uses the helper
    topology.newAgent("MainAgent")
            .node("orchestrate", null, (AgentNode agentNode, String input) -> {
              AgentClient processor = agentNode.getAgentClient("TextProcessor");
              String result = processor.invoke(input);
              agentNode.result("Processed: " + result);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule SubagentModule
  [topology]
  ;; Helper agent that processes text
  (-> topology
      (aor/new-agent "TextProcessor")
      (aor/node
       "process"
       nil
       (fn [agent-node text]
         (aor/result! agent-node (str/upper-case text)))))

  ;; Main agent that uses the helper
  (-> topology
      (aor/new-agent "MainAgent")
      (aor/node
       "orchestrate"
       nil
       (fn [agent-node input]
         (let [processor (aor/agent-client agent-node "TextProcessor")
               result (aor/agent-invoke processor input)]
           (aor/result! agent-node (str "Processed: " result)))))))
```

### Recursive Agent Invocation

Agents can call themselves recursively, enabling elegant implementations of recursive algorithms.

#### Java API

```java
public class RecursiveModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("Factorial")
            .node("compute", null, (AgentNode agentNode, Integer n) -> {
              if (n <= 1) {
                agentNode.result(1);
              } else {
                AgentClient self = agentNode.getAgentClient("Factorial");
                Integer subResult = (Integer) self.invoke(n - 1);
                agentNode.result(n * subResult);
              }
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule RecursiveModule
  [topology]
  (-> topology
      (aor/new-agent "Factorial")
      (aor/node
       "compute"
       nil
       (fn [agent-node n]
         (if (<= n 1)
           (aor/result! agent-node 1)
           (let [self (aor/agent-client agent-node "Factorial")
                 sub-result (aor/agent-invoke self (dec n))]
             (aor/result! agent-node (* n sub-result))))))))
```

### Cross-Module Agent Calls

This example shows how one agent can call another agent in a different module. The agent from the other module is declared and given a local name with `declareClusterAgent`.

#### Java API

```java
// Module 1: Greeter agent
public class GreeterModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("Greeter")
            .node("greet", null, (AgentNode agentNode, String name) -> {
              agentNode.result("Hello, " + name + "!");
            });
  }
}

// Module 2: Mirror agent that calls Greeter
public class MirrorModule extends AgentModule {
  private final String greeterModuleName;

  public MirrorModule(String greeterModuleName) {
    this.greeterModuleName = greeterModuleName;
  }

  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.declareClusterAgent("GreeterMirror", new GreeterModule().getModuleName(), "Greeter");

    topology.newAgent("MirrorAgent")
            .node("process", null, (AgentNode agentNode, String name) -> {
                AgentClient greeterClient = agentNode.getAgentClient("GreeterMirror");
                String greeting = (String) greeterClient.invoke(name);
                agentNode.result("Mirror says: " + greeting);
            });
  }
}
```

#### Clojure API

```clojure
;; Module 1: Greeter agent
(aor/defagentmodule GreeterModule
  [topology]
  (-> topology
      (aor/new-agent "Greeter")
      (aor/node
       "greet"
       nil
       (fn [agent-node name]
         (aor/result! agent-node (str "Hello, " name "!"))))))

;; Module 2: Mirror agent that calls Greeter
(aor/defagentmodule MirrorModule
 [topology]
 (aor/declare-cluster-agent topology "GreeterMirror" (get-module-name GreeterModule) "Greeter")

 (-> topology
     (aor/new-agent "MirrorAgent")
     (aor/node
      "process"
      nil
      (fn [agent-node name]
        (let [greeter-client (aor/agent-client agent-node "GreeterMirror")
              greeting (aor/agent-invoke greeter-client name)]
          (aor/result! agent-node (str "Mirror says: " greeting)))))))
```

## Deploying Modules

Once you've defined your agents, you need to deploy them to a Rama cluster. Agent-o-rama modules are Rama modules, so they follow the standard Rama deployment process. For deploying a Rama cluster, consult the Rama docs on [setting up a cluster](https://redplanetlabs.com/docs/~/operating-rama.html#_setting_up_a_rama_cluster). There are also one-click deploys available [for AWS](https://github.com/redplanetlabs/rama-aws-deploy) and [for Azure](https://github.com/redplanetlabs/rama-azure-deploy).

### Local Development

For local development and testing, you can use `InProcessCluster` which runs everything in a single JVM process. This is perfect for development and doesn't require any cluster setup.

#### Java API

```java
import com.rpl.rama.test.*;

public class Main {
  public static void main(String[] args) throws Exception {
    try (InProcessCluster ipc = InProcessCluster.create()) {
      // Launch your module
      MyAgentModule module = new MyAgentModule();
      ipc.launchModule(module, new LaunchConfig(4, 2));

      // Get agent manager and interact with agents
      String moduleName = module.getModuleName();
      AgentManager manager = AgentManager.create(ipc, moduleName);
      AgentClient agent = manager.getAgentClient("MyAgent");

      // Invoke the agent
      Object result = agent.invoke("input data");
      System.out.println("Result: " + result);
    }
  }
}
```

#### Clojure API

```clojure
(require '[com.rpl.rama.test :as rtest])

(with-open [ipc (rtest/create-ipc)]
  ;; Launch your module
  (rtest/launch-module! ipc MyAgentModule {:tasks 4 :threads 2})

  ;; Get agent manager and interact with agents
  (let [module-name (rama/get-module-name MyAgentModule)
        manager (aor/agent-manager ipc module-name)
        agent (aor/agent-client manager "MyAgent")]

    ;; Invoke the agent
    (let [result (aor/agent-invoke agent "input data")]
      (println "Result:" result))))
```

### Deploying to a Cluster

To deploy to a production Rama cluster, you use the Rama CLI. First, package your module as a JAR file with all dependencies included.

#### Building the JAR

For Maven projects, use the Maven Assembly or Shade plugin to create an uber-jar:

```bash
mvn clean package
```

For Leiningen projects:

```bash
lein uberjar
```

#### Deploying with Rama CLI

Once you have your JAR, deploy it using the `rama deploy` command:

```bash
rama deploy \
  --action launch
  --jar target/my-agents.jar \
  --module com.mycompany.MyAgentModule \
  --tasks 32 \
  --threads 8 \
  --workers 4
```

See the [Rama documentation on launching modules](https://redplanetlabs.com/docs/~/operating-rama.html#_launching_modules) for a full explanation of these parameters.

## Updating Modules

Modules aren't static – their code evolves over time as you add features, fix bugs, or optimize performance. To update a module, you use Rama's one-line [update command](https://redplanetlabs.com/docs/~/operating-rama.html#_updating_modules), like so:


```bash
rama deploy \
  --action update \
  --jar target/my-agents-v2.jar \
  --module com.mycompany.MyAgentModule
```

It's possible or even likely there are some agent invocations mid-execution when you perform an update, especially if you have long-running agents. Agent-o-rama lets you decide what to do with these in-flight agent invocations on update by setting an "update mode" on the agent definition.


Update mode is set on the agent graph when defining the agent. Here's how to set it:

#### Java API

```java
public class MyAgentModule extends AgentModule {
  @Override
  protected void defineAgents(AgentTopology topology) {
    topology.newAgent("MyAgent")
            .setUpdateMode(UpdateMode.CONTINUE)  // or RESTART or DROP
            .node("process", null, (AgentNode agentNode, String input) -> {
              // Agent logic here
              agentNode.result("processed: " + input);
            });
  }
}
```

#### Clojure API

```clojure
(aor/defagentmodule MyAgentModule
  [topology]
  (-> (aor/new-agent topology "MyAgent")
      (aor/set-update-mode :continue)  ; or :restart or :drop
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (aor/result! agent-node (str "processed: " input))))))
```

### Update Modes

You can choose from three update modes:

#### 1. **CONTINUE Mode** (Default)

In-flight executions continue where they left off with the new agent definition. The agent's execution state is preserved and it resumes on the new code version.

Use this mode when:
- You want agents to complete their work without interruption
- The new code is compatible with in-flight execution state
- You're making incremental changes that don't fundamentally alter the agent's logic

#### 2. **RESTART Mode**

In-flight executions restart from the beginning with the new agent definition. The agent is invoked again with its original input arguments.

Use this mode when:
- The new code has significant changes that make continuing problematic
- You want all executions to use the new logic from start to finish

#### 3. **DROP Mode**

In-flight executions are terminated and not restarted. The agent invocation is simply dropped.

Use this mode when:
- The agent's work is no longer needed
- You're deprecating functionality
- Completing in-flight work would be problematic or wasteful

### Scaling Modules

You can also scale a module to change its resource allocation without changing code:

```bash
rama scaleExecutors \
  --module com.mycompany.MyAgentModule \
  --threads 32
  --workers 16
```

The docs on scaling are [here](https://redplanetlabs.com/docs/~/operating-rama.html#_scaling_modules).

## Learn next

- [Agent clients](TODO)
- [Human-in-the-loop](TODO)
- [Streaming](TODO)
- [Tools agent](TODO)
