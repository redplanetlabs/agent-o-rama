# Coding Agents in Agent-O-Rama

This page explains how to code agents with Agent-o-rama. All examples are shown in both Java and Clojure.

## Table of Contents

TODO: update this TOC after doing rest of updates

1. [Basic Concepts](#basic-concepts)
2. [Nodes, Emits, and Results](#nodes-emits-and-results)
3. [Agent Graphs with Loops](#agent-graphs-with-loops)
4. [Aggregation Subgraphs](#aggregation-subgraphs)
5. [Custom Aggregators](#custom-aggregators)
6. [Agent Objects](#agent-objects)
7. [Stores](#stores)
8. [Subagents and Recursion](#subagents-and-recursion)
9. [Advanced Patterns](#advanced-patterns)

## Basic Concepts

Agent-o-rama is a library for building AI agents as directed graphs. Nodes are the fundamental computation units in agent graphs. Each node is a plain Java or Clojure function that receives data, processes it, and either passes it along to other nodes or return a final result. This is the basic building block that enables all other agent patterns. Agent-o-rama executes all nodes on [virtual threads](https://docs.oracle.com/en/java/javase/21/core/virtual-threads.html), which means node functions can be long-running and written in a blocking style without wasting thread resources.

Agent-o-rama captures all inputs, nested operations (e.g. model calls or database operations), and outputs from each node for viewing in the web UI. This information is also used and for to produce and display aggregated analytics about individual agent executions and time-series analytics for all agent executions.

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


### Emitting multiple times example

TODO:
- explain how when a node emits multiple times:
  - the first emit runs on the same node/thread
  - subsequent emits will run in parallel on other threads or even other nodes
    - so agent graphs automatically parallelize/distribute execution
    - only something you need to think about if you're nodes could be doing database calls to the same entities in parallel
- show example of this with just regular nodes, where the example does do multiple result! calls due to multiple emits
  - explain first-write-wins behavior, and why that's allowed
    - you might want to try multiple things and have the first result go back to user for expediency

## Aggregation Subgraphs

Aggregation subgraphs enable fan-out/fan-in patterns where work is distributed to multiple parallel nodes and results are collected and combined. This is essential for handling multiple concurrent operations, TODO: <example here about doing multiple LLM calls in parallel (since they're slow), and then combining results>

### Basic Aggregation Example

This example shows how to distribute work across multiple parallel processors and then collect the results.

#### Java API

TODO: this example is way too complicated. just have the agg start node explicitly emit multiple times to the intermediate node rather than partition data like this

```java
public class AggregationAgentModule extends AgentModule {
    @Override
    protected void defineAgents(AgentTopology topology) {
        topology.newAgent("AggregationAgent")
            .aggStartNode("distribute-work", "process-chunk",
                          (AgentNode agentNode, Map<String, Object> data) -> {
                List<Integer> numbers = (List<Integer>) data.get("data");
                Integer chunkSize = (Integer) data.get("chunk-size");

                // Partition data into chunks
                for (int i = 0; i < numbers.size(); i += chunkSize) {
                    int end = Math.min(i + chunkSize, numbers.size());
                    List<Integer> chunk = numbers.subList(i, end);
                    agentNode.emit("process-chunk", chunk);
                }
            })
            .node("process-chunk", "collect-results", (AgentNode agentNode, List<Integer> chunk) -> {
                int sum = chunk.stream().mapToInt(x -> x * x).sum();
                agentNode.emit("collect-results", Map.of("chunk", chunk, "sum", sum));
            })
            .aggNode("collect-results", null, BuiltInAgg.vector(),
                     (AgentNode agentNode, List<Map<String, Object>> results, Object _) -> {
                int totalSum = results.stream().mapToInt(r -> (Integer) r.get("sum")).sum();
                agentNode.result(Map.of("total-sum", totalSum, "chunks-processed", results.size()));
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
       "process-chunk"
       (fn [agent-node {:keys [data chunk-size]}]
         (doseq [chunk (partition-all chunk-size data)]
           (aor/emit! agent-node "process-chunk" chunk))))
      (aor/node
       "process-chunk"
       "collect-results"
       (fn [agent-node chunk]
         (let [sum (reduce + (map #(* % %) chunk))]
           (aor/emit! agent-node "collect-results" {:chunk chunk :sum sum}))))
      (aor/agg-node
       "collect-results"
       nil
       aggs/+vec-agg
       (fn [agent-node results _]
         (let [total-sum (reduce + (map :sum results))]
           (aor/result! agent-node {:total-sum total-sum :chunks-processed (count results)}))))))
```

### Aggregation scope

TODO: explain here how agg subgraphs can be nested and how each invocation of an agg start node is a new context for aggregation that will lead to the corresponding aggNode being run once. so you could have a first agg start node that emits multiple times to another agg start node, and then that nested agg gets agged into the first agg context. look at research-agent.clj in examples/ for a real example of this, and then make a simplified but relatable example here

### Custom Aggregators

Built-in aggregators handle most use cases, but sometimes you need custom logic on how to aggregate inputs. You can do that by defining custom Rama aggregators, which is explained [here for Java](https://redplanetlabs.com/docs/~/aggregators.html#_defining_aggregators) and [here for Clojure](https://redplanetlabs.com/docs/~/clj-dataflow-lang.html#_aggregators).

Agent-o-rama also has a special aggregator type called "multi aggregator" which can process different kinds of inputs.

TODO: needs a more gentle intro to multi-aggs. explain how aggregation inputs specify which "target" to run, and then show the multi-agg definition, and then show using it in an agent module


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
            })
            .node("process-numbers", "combine-results", (AgentNode agentNode, Integer number) -> {
                Map<String, Object> analysis = Map.of("value", number, "square", number * number);
                agentNode.emit("combine-results", "number", analysis);
            })
            .node("process-text", "combine-results", (AgentNode agentNode, String text) -> {
                Map<String, Object> analysis = Map.of("value", text, "length", text.length());
                agentNode.emit("combine-results", "text", analysis);
            })
            .aggNode("combine-results", null,
                     MultiAgg.init(() -> new AggregationState())
                         .on("number", (AggregationState state, Map<String, Object> analysis) -> {
                             state.numbers.add(analysis);
                             return state;
                         })
                         .on("text", (AggregationState state, Map<String, Object> analysis) -> {
                             state.text.add(analysis);
                             return state;
                         }),
                     (AgentNode agentNode, AggregationState state, Object _) -> {
                int numberSum = state.numbers.stream().mapToInt(n -> (Integer) n.get("value")).sum();
                int totalChars = state.text.stream().mapToInt(t -> (Integer) t.get("length")).sum();
                agentNode.result(Map.of("number-sum", numberSum, "total-chars", totalChars));
            });
    }

    public static class AggregationState implements RamaSerializable {
        public List<Map<String, Object>> numbers = new ArrayList<>();
        public List<Map<String, Object>> text = new ArrayList<>();
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
         (aor/emit! agent-node "combine-results" "number" {:value number :square (* number number)})))
      (aor/node
       "process-text"
       "combine-results"
       (fn [agent-node text]
         (aor/emit! agent-node "combine-results" "text" {:value text :length (count text)})))
      (aor/agg-node
       "combine-results"
       nil
       (aor/multi-agg
        (init [] {:numbers [] :text []})
        (on "number" [state analysis] (update state :numbers conj analysis))
        (on "text" [state analysis] (update state :text conj analysis)))
       (fn [agent-node state _]
         (let [number-sum (reduce + (map :value (:numbers state)))
               total-chars (reduce + (map :length (:text state)))]
           (aor/result! agent-node {:number-sum number-sum :total-chars total-chars}))))))
```

### Early aggregation return

TODO: explain how aggregators can be written to return early, in clojure by returning a value wrapped in reduce, and in java with FinishedAgg. this causes aggregation to immediately finish (before all incoming data has been processed), and run the agg node. look at tests for examples of both of these

## Agent Objects

Agent objects are shared resources like AI models, database connections, or API clients that agents can access during execution. They enable agents to interact with external systems and maintain expensive resources efficiently. Agent objects handle connection pooling if necessary and ensure thread safety. They're essential for building real-world agents that integrate with other systems.

### Static Objects Example

TODO: this example is showing static example, which is good, and ALSO a dynamic example. so this section should really be not just about "static objects". I think showing both is fine and not too much at once. just explain it better here

This example shows how to declare and use static objects like API keys or other static info.

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
            return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .modelName("gpt-4o-mini")
                .build();
        });

        topology.newAgent("AgentWithObjects")
            .node("process", null, (AgentNode agentNode, String input) -> {
                OpenAiChatModel model = agentNode.getAgentObject("openai-model");
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
     (-> (OpenAiChatModel/builder)
         (.apiKey (aor/get-agent-object setup "openai-api-key"))
         (.modelName "gpt-4o-mini")
         .build)))

  (-> (aor/new-agent topology "AgentWithObjects")
      (aor/node
       "process"
       nil
       (fn [agent-node input]
         (let [model (aor/get-agent-object agent-node "openai-model")]
           (aor/result! agent-node (aor/chat model input)))))))
```


TODO: need to also explain in agent objects section:
  - worker object pool size options
  - thread safety option
  - show example of using an embedding store (from langchain4j)
  - explain how lc4j chat models and embedding stores are automatically wrapped and traced, but this can be turned off with auto tracing option
  - explain how streaming chat model gets wrapped in chat model when you get it in an agent node
    - so you always use it in a blocking style in agent nodes, but by declaring it as streaming chat model AOR automatically captures the stream and forwards its chunks to the node. so agent clients can stream the node and get stream of all calls done of streaming chat models. if you don't want streaming, declare the object as non-streaming model


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
