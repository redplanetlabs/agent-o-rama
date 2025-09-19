# Communication Patterns

Agents don't just compute: they communicate. Stream real-time updates through [streaming chunks](../glossary.md#streaming-chunk). Request [human input](../glossary.md#human-input-request). Build interactive systems that respond as things happen.

> **Reference**: See comprehensive communication pattern documentation in the [Glossary](../glossary.md) for streaming and human input concepts.

## Streaming: Real-Time Data Flow

[Streaming chunks](../glossary.md#streaming-chunk) let your [agents](../terms/agent.md) send data as it's generated from any [agent node](../glossary.md#agent-node). Perfect for progress updates, live monitoring, or AI responses with real-time feedback.

### Emitting Stream Chunks

Any [agent node](../glossary.md#agent-node) can emit [streaming chunks](../glossary.md#streaming-chunk) using `stream-chunk!` to send real-time data to active [streaming subscriptions](../glossary.md#streaming-subscription):

**Clojure:**
```clojure
(aor/defagentmodule StreamingModule
  [topology]

  (-> topology
      (aor/new-agent "DataStreamer")

      ;; Stream processing progress
      (aor/node "process-batch" nil
                (fn [agent-node items]
                  (doseq [[idx item] (map-indexed vector items)]
                    ;; Process item
                    (let [result (process-item item)]
                      ;; Stream progress update
                      (aor/stream-chunk! agent-node
                        {:progress (inc idx)
                         :total (count items)
                         :current item
                         :result result}))
                    ;; Simulate work
                    (Thread/sleep 100))
                  ;; Return final result
                  (aor/result! agent-node {:processed (count items)})))))
```

**Java:**
```java
public class StreamingModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        topology.newAgent("DataStreamer")

            // Stream processing progress
            .node("process-batch", null, (agentNode, items) -> {
                List<Object> itemList = (List<Object>) items;
                for (int i = 0; i < itemList.size(); i++) {
                    // Process item
                    Object result = processItem(itemList.get(i));
                    // Stream progress update
                    agentNode.streamChunk(Map.of(
                        "progress", i + 1,
                        "total", itemList.size(),
                        "current", itemList.get(i),
                        "result", result
                    ));
                    // Simulate work
                    Thread.sleep(100);
                }
                // Return final result
                agentNode.result(Map.of("processed", itemList.size()));
            });
    }
}
```

### Subscribing to Streams

[Agent clients](../glossary.md#agent-client) create [streaming subscriptions](../glossary.md#streaming-subscription) to receive [streaming chunks](../glossary.md#streaming-chunk) in real-time from specific nodes or all nodes:

**Clojure:**
```clojure
;; Subscribe to all stream chunks
(def subscription
  (aor/agent-stream-all client
    (fn [chunk]
      (println "Progress:" (:progress chunk) "/" (:total chunk)))))

;; Invoke the agent
(def result (aor/agent-invoke client items))

;; Clean up subscription
(aor/close-subscription subscription)

;; Or subscribe to specific nodes only
(def targeted-sub
  (aor/agent-stream-specific client ["process-batch"]
    (fn [chunk]
      (println "Batch progress:" chunk))))
```

**Java:**
```java
// Subscribe to all stream chunks
StreamSubscription subscription = client.streamAll(chunk -> {
    Map<String, Object> data = (Map<String, Object>) chunk;
    System.out.println("Progress: " + data.get("progress") + "/" + data.get("total"));
});

// Invoke the agent
Map<String, Object> result = client.invoke(items);

// Clean up subscription
subscription.close();

// Or subscribe to specific nodes only
StreamSubscription targetedSub = client.streamSpecific(
    List.of("process-batch"),
    chunk -> System.out.println("Batch progress: " + chunk)
);
```

## Human Input: Interactive Agents

Sometimes [agents](../terms/agent.md) need human decisions. The [human input request](../glossary.md#human-input-request) pattern pauses [agent node](../glossary.md#agent-node) execution and waits for user response, enabling human-in-the-loop workflows.

### Requesting Input

Use `get-human-input` within [agent nodes](../glossary.md#agent-node) to create [human input requests](../glossary.md#human-input-request) that pause execution and wait for user response:

**Clojure:**
```clojure
(aor/defagentmodule ApprovalModule
  [topology]

  (-> topology
      (aor/new-agent "ApprovalFlow")

      ;; Check if approval needed
      (aor/node "check-amount" "process"
                (fn [agent-node amount]
                  (if (> amount 1000)
                    (aor/emit! agent-node "request-approval" amount)
                    (aor/emit! agent-node "process" amount))))

      ;; Request human approval
      (aor/node "request-approval" "process"
                (fn [agent-node amount]
                  ;; Request includes prompt and options
                  (let [response (aor/get-human-input agent-node
                                   {:prompt (str "Approve $" amount " transaction?")
                                    :options ["approve" "reject" "escalate"]})]
                    (case response
                      "approve" (aor/emit! agent-node "process" amount)
                      "reject" (aor/result! agent-node {:status "rejected"})
                      "escalate" (aor/emit! agent-node "escalate" amount)))))

      ;; Process transaction
      (aor/node "process" nil
                (fn [agent-node amount]
                  (process-transaction amount)
                  (aor/result! agent-node {:status "processed" :amount amount})))))
```

**Java:**
```java
public class ApprovalModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        topology.newAgent("ApprovalFlow")

            // Check if approval needed
            .node("check-amount", "process", (agentNode, amount) -> {
                Double value = (Double) amount;
                if (value > 1000) {
                    agentNode.emit("request-approval", amount);
                } else {
                    agentNode.emit("process", amount);
                }
            })

            // Request human approval
            .node("request-approval", "process", (agentNode, amount) -> {
                // Request includes prompt and options
                Map<String, Object> request = Map.of(
                    "prompt", "Approve $" + amount + " transaction?",
                    "options", List.of("approve", "reject", "escalate")
                );
                String response = (String) agentNode.getHumanInput(request);

                switch (response) {
                    case "approve":
                        agentNode.emit("process", amount);
                        break;
                    case "reject":
                        agentNode.result(Map.of("status", "rejected"));
                        break;
                    case "escalate":
                        agentNode.emit("escalate", amount);
                        break;
                }
            })

            // Process transaction
            .node("process", null, (agentNode, amount) -> {
                processTransaction(amount);
                agentNode.result(Map.of("status", "processed", "amount", amount));
            });
    }
}
```

### Handling Human Input Requests

The [agent client](../glossary.md#agent-client) must handle [human input requests](../glossary.md#human-input-request) when they occur during [agent invoke](../glossary.md#agent-invoke) execution:

**Clojure:**
```clojure
;; Async invocation to handle input requests
(def invoke-handle (aor/agent-initiate client 5000.0))

;; Check for human input requests
(when-let [request (aor/agent-next-human-input-request invoke-handle)]
  (println "Agent asks:" (:prompt request))
  (println "Options:" (:options request))

  ;; Get user's choice (in real app, from UI)
  (let [user-choice (get-user-input)]
    ;; Send response back to agent
    (aor/agent-submit-human-input invoke-handle user-choice)))

;; Get final result
(def result (aor/agent-result invoke-handle))
```

**Java:**
```java
// Async invocation to handle input requests
AgentInvoke invokeHandle = client.initiate(5000.0);

// Check for human input requests
HumanInputRequest request = invokeHandle.nextHumanInputRequest();
if (request != null) {
    System.out.println("Agent asks: " + request.getPrompt());
    System.out.println("Options: " + request.getOptions());

    // Get user's choice (in real app, from UI)
    String userChoice = getUserInput();
    // Send response back to agent
    invokeHandle.submitHumanInput(userChoice);
}

// Get final result
Map<String, Object> result = invokeHandle.result();
```

## Combining Patterns: Interactive Streaming

Combine [streaming chunks](../glossary.md#streaming-chunk) with [human input requests](../glossary.md#human-input-request) for truly interactive experiences that provide real-time progress while accepting user decisions:

**Clojure:**
```clojure
(aor/defagentmodule InteractiveAnalysisModule
  [topology]

  (-> topology
      (aor/new-agent "DataAnalyzer")

      ;; Analyze with streaming updates
      (aor/node "analyze" "review"
                (fn [agent-node data]
                  (let [steps ["Loading" "Cleaning" "Processing" "Analyzing"]]
                    (doseq [step steps]
                      (aor/stream-chunk! agent-node {:status step})
                      (Thread/sleep 500))

                    ;; Generate initial results
                    (let [results (analyze-data data)]
                      (aor/stream-chunk! agent-node {:status "Complete" :results results})
                      (aor/emit! agent-node "review" results)))))

      ;; Request review
      (aor/node "review" nil
                (fn [agent-node results]
                  (let [response (aor/get-human-input agent-node
                                   {:prompt "Review analysis results"
                                    :data results
                                    :options ["accept" "refine" "restart"]})]
                    (case response
                      "accept" (aor/result! agent-node results)
                      "refine" (aor/emit! agent-node "refine" results)
                      "restart" (aor/emit! agent-node "analyze" (:original-data results))))))))

;; Client side: handle both streaming and input
(def subscription
  (aor/agent-stream-all client
    (fn [chunk]
      (update-ui-progress chunk))))

(def invoke-handle (aor/agent-initiate client data))

;; Handle any human input requests
(future
  (when-let [request (aor/agent-next-human-input-request invoke-handle)]
    (show-review-dialog (:data request))
    (let [choice (wait-for-user-choice)]
      (aor/agent-submit-human-input invoke-handle choice))))

(def final-result (aor/agent-result invoke-handle))
```

**Java:**
```java
public class InteractiveAnalysisModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        topology.newAgent("DataAnalyzer")

            // Analyze with streaming updates
            .node("analyze", "review", (agentNode, data) -> {
                String[] steps = {"Loading", "Cleaning", "Processing", "Analyzing"};
                for (String step : steps) {
                    agentNode.streamChunk(Map.of("status", step));
                    Thread.sleep(500);
                }

                // Generate initial results
                Map<String, Object> results = analyzeData(data);
                agentNode.streamChunk(Map.of("status", "Complete", "results", results));
                agentNode.emit("review", results);
            })

            // Request review
            .node("review", null, (agentNode, results) -> {
                Map<String, Object> request = Map.of(
                    "prompt", "Review analysis results",
                    "data", results,
                    "options", List.of("accept", "refine", "restart")
                );
                String response = (String) agentNode.getHumanInput(request);

                switch (response) {
                    case "accept":
                        agentNode.result(results);
                        break;
                    case "refine":
                        agentNode.emit("refine", results);
                        break;
                    case "restart":
                        Map<String, Object> resultMap = (Map<String, Object>) results;
                        agentNode.emit("analyze", resultMap.get("original-data"));
                        break;
                }
            });
    }
}

// Client side: handle both streaming and input
StreamSubscription subscription = client.streamAll(chunk -> {
    updateUIProgress(chunk);
});

AgentInvoke invokeHandle = client.initiate(data);

// Handle any human input requests
CompletableFuture.runAsync(() -> {
    HumanInputRequest request = invokeHandle.nextHumanInputRequest();
    if (request != null) {
        showReviewDialog(request.getData());
        String choice = waitForUserChoice();
        invokeHandle.submitHumanInput(choice);
    }
});

Map<String, Object> finalResult = invokeHandle.result();
```

## Pattern Selection Guide

Choose your communication pattern based on your [agent](../terms/agent.md) interaction needs:

**Use Streaming when you:**
- Generate data incrementally
- Want real-time progress updates
- Process large datasets in chunks
- Build responsive UIs

**Use Human Input when you:**
- Need user decisions
- Require approval workflows
- Build interactive assistants
- Implement quality reviews

**Combine both when you:**
- Build conversational AI
- Create monitoring dashboards
- Implement guided workflows
- Need maximum interactivity

## What's Next?

You've learned how [agents](../terms/agent.md) communicate through [streaming chunks](../glossary.md#streaming-chunk) and [human input requests](../glossary.md#human-input-request). Now discover advanced patterns for parallel processing and AI integration in [Advanced Patterns](04-advanced-patterns.md).
