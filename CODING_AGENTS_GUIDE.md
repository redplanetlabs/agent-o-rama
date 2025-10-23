# Coding Agents in Agent-O-Rama

This page explains how to code agents with Agent-o-rama. All examples are shown in both Java and Clojure.

## Table of Contents

1. [Basic Concepts](#basic-concepts)
2. [Nodes, Emits, and Results](#nodes-emits-and-results)
3. [Routing in Agent Graphs](#routing-in-agent-graphs)
4. [Aggregation Subgraphs](#aggregation-subgraphs)
5. [Agent Objects](#agent-objects)
6. [Stores](#stores)
7. [Subagents and Recursion](#subagents-and-recursion)
8. [Advanced Patterns](#advanced-patterns)

## Basic Concepts

Agent-o-rama is a library for building AI agents as directed graphs. Nodes are the fundamental computation units in agent graphs. Each node is a plain Java or Clojure function that receives data, processes it, and either passes it along to other nodes or returns a final result. This is the basic building block that enables all other agent patterns. Agent-o-rama executes all nodes on [virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), which means node functions can be long-running and written in a blocking style without wasting thread resources.

Agent-o-rama captures all inputs, nested operations (e.g. model calls or database operations), and outputs from each node for viewing in the web UI. This information is also used and for to produce and display aggregated analytics about individual agent executions and time-series analytics for all agent executions.

Besides tracing, nodes are also the granularity at which streaming is consumed by agent clients. Things like calls to Langchain4j models are automatically streamed for the node, and node functionsd can explicitly stream chunks back as well. This is discussed more on the [agent client](TODO) page.

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

Agg start nodes are the only nodes that have return values. The return value is passed as the last argument to the corresponding agg node, allowing you to pass non-aggregated information (like metadata or configuration) through the aggregation.

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

TODO: this java example should be written as a class that implements RamaAccumulatorAgg. just base it on EarlySumAccum.java in the tests
```java
// Custom aggregator that stops when sum exceeds 100
RamaAccumulatorAgg<Integer, Integer> sumUntil100 = new RamaAccumulatorAgg<Integer, Integer>() {
  @Override
  public Integer init() {
    return 0;
  }

  @Override
  public Object update(Integer state, Integer value) {
    int newSum = state + value;
    if (newSum > 100) {
      return new FinishedAgg(newSum); // Stop aggregating early
    }
    return newSum;
  }
};
```

#### Clojure API

TODO: this should be a def that makes an accumulator... look in the tests for basically this exact example
```clojure
;; Custom aggregator that stops when sum exceeds 100
(defn sum-until-100-agg []
  (aggs/accumulator-agg
   (init [] 0)
   (update [state value]
     (let [new-sum (+ state value)]
       (if (> new-sum 100)
         (reduced new-sum) ; Stop aggregating early
         new-sum)))))
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

Stores provide persistent data access for agents, enabling them to maintain state across invocations and share data between different agent executions. There are three types of stores optimized for different use cases.

### Why Stores Matter

Real agents need to remember information, maintain user sessions, cache results, and share data between executions. Stores provide efficient, persistent storage that integrates seamlessly with the agent execution model.

### Key-Value Store Example

Key-value stores are perfect for simple data like counters, flags, or cached values.

#### Java API

```java
public class KeyValueStoreModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.declareKeyValueStore("$$counters", String.class, Long.class);

        topology.newAgent("KeyValueStoreAgent")
            .node("manage-counter", null, (AgentNode agentNode, Map<String, Object> data) -> {
                KeyValueStore<String, Long> store = agentNode.getStore("$$counters");
                String counterName = (String) data.get("counter-name");
                String operation = (String) data.get("operation");

                switch (operation) {
                    case "get":
                        Long value = store.get(counterName);
                        agentNode.result(Map.of("counter", counterName, "value", value));
                        break;
                    case "increment":
                        Long currentValue = store.get(counterName);
                        if (currentValue == null) currentValue = 0L;
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
       (fn [agent-node {:keys [counter-name operation]}]
         (let [store (aor/get-store agent-node "$$counters")]
           (case operation
             "get"
             (aor/result! agent-node {:counter counter-name :value (store/get store counter-name)})
             "increment"
             (let [current-value (or (store/get store counter-name) 0)
                   new-value (inc current-value)]
               (store/put! store counter-name new-value)
               (aor/result! agent-node {:counter counter-name :new-value new-value})))))))
```

### Document Store Example

Document stores are ideal for structured data with multiple fields, like user profiles or configuration objects.

#### Java API

```java
public class DocumentStoreModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.declareDocumentStore("$$user-profiles", String.class,
                                     "name", String.class,
                                     "age", Long.class,
                                     "preferences", Object.class);

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
                              "age" Long
                              "preferences" Object)

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
           (aor/result! agent-node {:user-id user-id :name name :age age})))))
```

## Subagents and Recursion

Agents can call other agents (subagents) within the same module or across modules, enabling recursive and mutually recursive patterns. This is essential for building complex agent hierarchies and implementing recursive algorithms.

### Why Subagents Matter

Real-world systems often need to break down complex tasks into smaller, manageable pieces. Subagents enable this decomposition while maintaining the benefits of the agent execution model. They also enable recursive patterns for algorithms that naturally decompose into smaller instances of the same problem.

### Cross-Module Agent Calls

This example shows how one agent can call another agent in a different module.

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
        topology.declareClusterAgent("GreeterMirror", greeterModuleName, "Greeter");

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
(defn create-mirror-module [greeter-module-name]
  (aor/agentmodule
   [topology]
   (aor/declare-cluster-agent topology "GreeterMirror" greeter-module-name "Greeter")

   (-> topology
       (aor/new-agent "MirrorAgent")
       (aor/node
        "process"
        nil
        (fn [agent-node name]
          (let [greeter-client (aor/agent-client agent-node "GreeterMirror")
                greeting (aor/agent-invoke greeter-client name)]
            (aor/result! agent-node (str "Mirror says: " greeting))))))))
```

### Recursive Agent Patterns

This example shows how agents can implement recursive algorithms.

#### Java API

```java
public class RecursiveAgentModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.newAgent("RecursiveAgent")
            .node("process", null, (AgentNode agentNode, Map<String, Object> data) -> {
                Integer n = (Integer) data.get("n");
                String operation = (String) data.get("operation");

                if ("factorial".equals(operation)) {
                    long result = factorial(n);
                    agentNode.result(Map.of("operation", "factorial", "n", n, "result", result));
                } else {
                    agentNode.result(Map.of("error", "Unknown operation: " + operation));
                }
            });
    }

    private long factorial(int n) {
        if (n <= 1) return 1;
        return n * factorial(n - 1);
    }
}
```

#### Clojure API

```clojure
(aor/defagentmodule RecursiveAgentModule
  [topology]
  (-> (aor/new-agent topology "RecursiveAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node {:keys [n operation]}]
         (case operation
           "factorial"
           (aor/result! agent-node {:operation "factorial" :n n :result (factorial n)})
           (aor/result! agent-node {:error (str "Unknown operation: " operation)})))))

(defn factorial [n]
  (if (<= n 1) 1 (* n (factorial (dec n)))))
```

## Advanced Patterns

### Human Input Integration

Agents can request human input during execution, enabling human-in-the-loop patterns for tasks that require human judgment or approval.

#### Java API

```java
public class HumanInputAgentModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.newAgent("HumanInputAgent")
            .node("process", null, (AgentNode agentNode, String input) -> {
                String humanResponse = agentNode.getHumanInput("Please review: " + input + "\nIs this correct? (y/n): ");

                if ("y".equals(humanResponse)) {
                    agentNode.result("Human approved: " + input);
                } else {
                    agentNode.result("Human rejected: " + input);
                }
            });
    }
}
```

#### Clojure API

```clojure
(aor/defagentmodule HumanInputAgentModule
  [topology]
  (-> (aor/new-agent topology "HumanInputAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [human-response (aor/get-human-input agent-node
                                                   (str "Please review: " input "\nIs this correct? (y/n): "))]
           (if (= "y" human-response)
             (aor/result! agent-node (str "Human approved: " input))
             (aor/result! agent-node (str "Human rejected: " input)))))))
```

### Streaming Data

Agents can stream data to clients in real-time, enabling progressive results and better user experience for long-running operations.

#### Java API

```java
public class StreamingAgentModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.newAgent("StreamingAgent")
            .node("process", null, (AgentNode agentNode, String input) -> {
                String[] words = input.split("\\s+");
                for (int i = 0; i < words.length; i++) {
                    agentNode.streamChunk(Map.of("word", words[i], "index", i, "total", words.length));
                }
                agentNode.result(Map.of("total-words", words.length, "status", "complete"));
            });
    }
}
```

#### Clojure API

```clojure
(aor/defagentmodule StreamingAgentModule
  [topology]
  (-> (aor/new-agent topology "StreamingAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [words (str/split input #"\\s+")]
           (doseq [[word index] (map-indexed vector words)]
             (aor/stream-chunk! agent-node {:word word :index index :total (count words)}))
           (aor/result! agent-node {:total-words (count words) :status "complete"})))))
```

## Conclusion

This guide covers the essential concepts for coding agents in agent-o-rama:

1. **Basic Concepts**: Nodes, emits, and results form the foundation
2. **Graph Patterns**: Loops, conditional routing, and complex control flow
3. **Aggregation**: Fan-out/fan-in patterns with custom aggregators
4. **Persistence**: Stores for maintaining state across invocations
5. **Integration**: Agent objects for external resources
6. **Composition**: Subagents and recursive patterns
7. **Advanced Features**: Human input, streaming, and error handling

The framework provides a powerful and flexible foundation for building complex AI agents with sophisticated control flow, data processing, and integration capabilities.
