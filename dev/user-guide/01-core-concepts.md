# Core Concepts

You build Agent-O-Rama systems from three fundamental pieces: agents, nodes, and graphs. Master these, and everything else follows naturally.

## Agents: Your Distributed Workers

An agent is a computational unit that executes tasks. Think of it as a smart worker that:
- Follows a defined workflow (its graph)
- Maintains its own state
- Runs distributed across your cluster
- Handles failures automatically

Every agent starts with a module definition:

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

Nodes are the individual steps in your agent's workflow. Each node:
- Performs a specific computation
- Receives input from previous nodes or the initial invocation
- Can emit data to other nodes or return a final result

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
- **`emit!`**: Send data to another node
- **`result!`**: Return the final result and end execution
- **`stream-chunk!`**: Send streaming data to subscribers

## Graphs: Connecting the Dots

An agent graph defines how nodes connect and data flows. You build graphs by chaining nodes together:

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

Sometimes you need dynamic control flow. Router nodes let you choose the next node at runtime:

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

Once deployed, you interact with agents through clients:

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

Agents often need shared resources like database connections or AI models. Agent objects provide these:

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

Here's a complete example that shows all the concepts working together:

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

You now understand the core building blocks. Next, learn how agents maintain and share state in [State Management](02-state-management.md).