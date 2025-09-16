# Core Concepts

You build Agent-O-Rama systems from three fundamental pieces: [agents](../terms/agent.md), [nodes](../glossary.md#agent-node), and [graphs](../glossary.md#agent-graph). Master these, and everything else follows naturally.

> **Reference**: See the [Agent](../terms/agent.md) and [Agent Node](../terms/agent-node.md) documentation for comprehensive details.

## Agents: Your Distributed Workers

An [agent](../terms/agent.md) is a distributed, stateful computational unit that executes a directed graph of [nodes](../glossary.md#agent-node). Think of it as a smart worker that:
- Follows a defined workflow (its [agent graph](../glossary.md#agent-graph))
- Maintains its own state through [stores](../glossary.md#key-value-store)
- Runs distributed across your cluster with automatic partitioning
- Handles failures automatically with [retry mechanisms](../glossary.md#retry-mechanism)
- Communicates via [node emissions](../glossary.md#node-emit) and [agent results](../glossary.md#agent-result)

Every agent starts with an [agent module](../glossary.md#agent-module) definition that packages agents, stores, and [agent objects](../terms/agent-objects.md) into a deployable unit:

**Clojure:**
```clojure
(aor/defagentmodule MyModule
  [topology]
  ;; Your agent definitions go here
  )
```

**Java:**
```java
public class MyModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Your agent definitions go here
    }
}
```

## Nodes: The Building Blocks

An [agent node](../glossary.md#agent-node) is an individual execution unit within an [agent graph](../glossary.md#agent-graph) that performs specific computation. Each node:
- Performs a specific computation with access to [stores](../glossary.md#key-value-store) and [agent objects](../terms/agent-objects.md)
- Receives input from previous nodes or the initial [agent invoke](../glossary.md#agent-invoke)
- Can [emit](../glossary.md#node-emit) data to other nodes or return a final [agent result](../glossary.md#agent-result)
- Executes within distributed [task globals](../glossary.md#task-global) for state management

A node is just a function that receives an `agent-node` context and your data:

**Clojure:**
```clojure
(aor/node "process" "next-step"
          (fn [agent-node data]
            ;; Do something with data
            (let [result (process data)]
              ;; Send result to next-step node
              (aor/emit! agent-node "next-step" result))))
```

**Java:**
```java
.node("process", "next-step", (agentNode, data) -> {
    // Do something with data
    Object result = process(data);
    // Send result to next-step node
    agentNode.emit("next-step", result);
})
```

Nodes have three key operations:
- **`emit!`**: Send data via [node emit](../glossary.md#node-emit) to trigger execution of downstream nodes
- **`result!`**: Return the final [agent result](../glossary.md#agent-result) and complete the agent execution
- **`stream-chunk!`**: Send [streaming chunks](../glossary.md#streaming-chunk) to active [streaming subscriptions](../glossary.md#streaming-subscription)

## Graphs: Connecting the Dots

An [agent graph](../glossary.md#agent-graph) is a directed graph structure that defines the execution flow of an agent, consisting of interconnected [nodes](../glossary.md#agent-node) with specified output relationships. You build graphs by chaining nodes together:

**Clojure:**
```clojure
(-> topology
    (aor/new-agent "DataProcessor")
    (aor/node "validate" "transform"
              (fn [agent-node input]
                (if (valid? input)
                  (aor/emit! agent-node "transform" input)
                  (aor/result! agent-node {:error "Invalid input"}))))
    (aor/node "transform" "save"
              (fn [agent-node data]
                (let [transformed (transform data)]
                  (aor/emit! agent-node "save" transformed))))
    (aor/node "save" nil
              (fn [agent-node data]
                (save-to-db data)
                (aor/result! agent-node {:status "success"}))))
```

**Java:**
```java
topology.newAgent("DataProcessor")
    .node("validate", "transform", (agentNode, input) -> {
        if (isValid(input)) {
            agentNode.emit("transform", input);
        } else {
            agentNode.result(Map.of("error", "Invalid input"));
        }
    })
    .node("transform", "save", (agentNode, data) -> {
        Object transformed = transform(data);
        agentNode.emit("save", transformed);
    })
    .node("save", null, (agentNode, data) -> {
        saveToDb(data);
        agentNode.result(Map.of("status", "success"));
    });
```

## Routing: Dynamic Flow Control

Sometimes you need dynamic control flow based on runtime conditions. Router nodes provide conditional [node emissions](../glossary.md#node-emit) to choose the next node at runtime:

**Clojure:**
```clojure
(aor/router-node "decide"
                 (fn [agent-node data]
                   (cond
                     (urgent? data) (aor/emit! agent-node "fast-track" data)
                     (complex? data) (aor/emit! agent-node "deep-analysis" data)
                     :else (aor/emit! agent-node "standard" data))))
```

**Java:**
```java
.routerNode("decide", (agentNode, data) -> {
    if (isUrgent(data)) {
        agentNode.emit("fast-track", data);
    } else if (isComplex(data)) {
        agentNode.emit("deep-analysis", data);
    } else {
        agentNode.emit("standard", data);
    }
})
```

## Invoking Agents

Once deployed, you interact with agents through [agent clients](../glossary.md#agent-client). An [agent invoke](../glossary.md#agent-invoke) represents a specific execution instance:

**Clojure:**
```clojure
;; Get a client for your agent
(def client (aor/agent-client manager "DataProcessor"))

;; Synchronous invocation
(def result (aor/agent-invoke client {:data "process me"}))

;; Asynchronous invocation
(def invoke-handle (aor/agent-initiate client {:data "process me"}))
(def result (aor/agent-result invoke-handle))
```

**Java:**
```java
// Get a client for your agent
AgentClient client = manager.agentClient("DataProcessor");

// Synchronous invocation
Map<String, Object> result = client.invoke(Map.of("data", "process me"));

// Asynchronous invocation
AgentInvoke invokeHandle = client.initiate(Map.of("data", "process me"));
Map<String, Object> result = invokeHandle.result();
```

## Shared Resources: Agent Objects

[Agent objects](../terms/agent-objects.md) are shared resources (like AI models, databases, APIs) that agents can access during execution. They provide distributed access to expensive or stateful resources:

**Clojure:**
```clojure
(aor/defagentmodule ModuleWithResources
  [topology]
  
  ;; Declare a static object
  (aor/declare-agent-object topology "config" {:api-key "secret"})
  
  ;; Declare a built object (created per worker)
  (aor/declare-agent-object-builder 
    topology "ai-model"
    (fn [setup]
      (create-model {:api-key (get-api-key)})))
  
  ;; Use in nodes
  (-> topology
      (aor/new-agent "SmartAgent")
      (aor/node "process" nil
                (fn [agent-node input]
                  (let [config (aor/get-agent-object agent-node "config")
                        model (aor/get-agent-object agent-node "ai-model")
                        response (query-model model input (:api-key config))]
                    (aor/result! agent-node response))))))
```

**Java:**
```java
public class ModuleWithResources extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Declare a static object
        topology.declareAgentObject("config", Map.of("apiKey", "secret"));
        
        // Declare a built object (created per worker)
        topology.declareAgentObjectBuilder("ai-model", setup -> 
            createModel(getApiKey())
        );
        
        // Use in nodes
        topology.newAgent("SmartAgent")
            .node("process", null, (agentNode, input) -> {
                Map<String, Object> config = agentNode.getAgentObject("config");
                Object model = agentNode.getAgentObject("ai-model");
                Object response = queryModel(model, input, config.get("apiKey"));
                agentNode.result(response);
            });
    }
}
```

## Putting It Together

Here's a complete example that shows all the concepts working together - an [agent module](../glossary.md#agent-module) with [agent objects](../terms/agent-objects.md), [stores](../glossary.md#key-value-store), and a multi-node [agent graph](../glossary.md#agent-graph):

**Clojure:**
```clojure
(aor/defagentmodule OrderProcessingModule
  [topology]
  
  ;; Shared database connection
  (aor/declare-agent-object-builder topology "db"
    (fn [setup] (create-db-connection)))
  
  ;; Order processing agent
  (-> topology
      (aor/new-agent "OrderProcessor")
      
      ;; Validate the order
      (aor/router-node "validate"
                       (fn [agent-node order]
                         (if (valid-order? order)
                           (aor/emit! agent-node "check-inventory" order)
                           (aor/result! agent-node {:status "rejected"
                                                   :reason "Invalid order"}))))
      
      ;; Check inventory
      (aor/node "check-inventory" "process-payment"
                (fn [agent-node order]
                  (let [db (aor/get-agent-object agent-node "db")
                        available? (check-stock db (:items order))]
                    (if available?
                      (aor/emit! agent-node "process-payment" order)
                      (aor/result! agent-node {:status "out-of-stock"})))))
      
      ;; Process payment
      (aor/node "process-payment" "ship"
                (fn [agent-node order]
                  (let [payment-result (charge-card (:payment order))]
                    (if (:success payment-result)
                      (aor/emit! agent-node "ship" order)
                      (aor/result! agent-node {:status "payment-failed"})))))
      
      ;; Ship order
      (aor/node "ship" nil
                (fn [agent-node order]
                  (let [tracking (create-shipment order)]
                    (aor/result! agent-node {:status "shipped"
                                            :tracking tracking}))))))
```

**Java:**
```java
public class OrderProcessingModule extends AgentModule {
    @Override
    public void configure(AgentsTopology topology) {
        // Shared database connection
        topology.declareAgentObjectBuilder("db", setup -> 
            createDbConnection()
        );
        
        // Order processing agent
        topology.newAgent("OrderProcessor")
            
            // Validate the order
            .routerNode("validate", (agentNode, order) -> {
                if (isValidOrder(order)) {
                    agentNode.emit("check-inventory", order);
                } else {
                    agentNode.result(Map.of(
                        "status", "rejected",
                        "reason", "Invalid order"
                    ));
                }
            })
            
            // Check inventory
            .node("check-inventory", "process-payment", (agentNode, order) -> {
                Database db = agentNode.getAgentObject("db");
                boolean available = checkStock(db, order.getItems());
                if (available) {
                    agentNode.emit("process-payment", order);
                } else {
                    agentNode.result(Map.of("status", "out-of-stock"));
                }
            })
            
            // Process payment
            .node("process-payment", "ship", (agentNode, order) -> {
                PaymentResult result = chargeCard(order.getPayment());
                if (result.isSuccess()) {
                    agentNode.emit("ship", order);
                } else {
                    agentNode.result(Map.of("status", "payment-failed"));
                }
            })
            
            // Ship order
            .node("ship", null, (agentNode, order) -> {
                String tracking = createShipment(order);
                agentNode.result(Map.of(
                    "status", "shipped",
                    "tracking", tracking
                ));
            });
    }
}
```

## What's Next?

You now understand the core building blocks: [agents](../terms/agent.md), [agent nodes](../glossary.md#agent-node), [agent graphs](../glossary.md#agent-graph), and [agent objects](../terms/agent-objects.md). Next, learn how agents maintain and share distributed state in [State Management](02-state-management.md).