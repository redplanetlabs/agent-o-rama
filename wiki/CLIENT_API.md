# Agent Client API

The Agent Client API is how you interact with deployed agents from your application code. It provides methods for invoking agents, tracking executions, and retrieving results.

## Table of Contents

1. [Getting an Agent Client](#getting-an-agent-client)
2. [Invoking Agents](#invoking-agents)
3. [Initiating Agent Executions](#initiating-agent-executions)
4. [Invoking with Metadata](#invoking-with-metadata)
5. [Getting Agent Results](#getting-agent-results)
6. [Other Features](#other-features)

## Getting an Agent Client

Before you can invoke an agent, you need to get an `AgentClient` instance. This requires an `AgentManager`, which you create from a Rama cluster and module name.

### Cluster Managers

The `AgentManager` is created from a cluster manager, which is the interface to a Rama cluster. There are two types of cluster managers:

1. **InProcessCluster (IPC)**: For local development and testing. Runs a complete Rama cluster in a single JVM process.
2. **RamaClusterManager**: For production. Connects to a deployed Rama cluster.

Both implement the same `ClusterManagerBase` interface, so your agent code works the same way in development and production.

### Local Development with InProcessCluster

For development and testing, use `InProcessCluster` which runs everything locally:

#### Java API

```java
import com.rpl.agentorama.*;
import com.rpl.rama.test.InProcessCluster;
import com.rpl.rama.test.LaunchConfig;

try (InProcessCluster ipc = InProcessCluster.create()) {
  // Launch your agent module
  MyAgentModule module = new MyAgentModule();
  ipc.launchModule(module, new LaunchConfig(4, 2));

  // Create agent manager from IPC
  String moduleName = module.getModuleName();
  AgentManager manager = AgentManager.create(ipc, moduleName);

  // Get client for a specific agent
  AgentClient agent = manager.getAgentClient("MyAgent");

  // List all available agents in the module
  Set<String> agentNames = manager.getAgentNames();
  System.out.println("Available agents: " + agentNames);
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor]
         '[com.rpl.rama :as rama]
         '[com.rpl.rama.test :as rtest])

(with-open [ipc (rtest/create-ipc)]
  ;; Launch your agent module
  (rtest/launch-module! ipc MyAgentModule {:tasks 4 :threads 2})

  ;; Create agent manager from IPC
  (let [module-name (rama/get-module-name MyAgentModule)
        manager (aor/agent-manager ipc module-name)]

    ;; Get client for a specific agent
    (let [agent (aor/agent-client manager "MyAgent")]

      ;; List all available agents in the module
      (println "Available agents:" (aor/agent-names manager)))))
```

### Production with RamaClusterManager

For production, connect to a deployed Rama cluster using `RamaClusterManager`:

#### Java API

```java
import com.rpl.agentorama.*;
import com.rpl.rama.RamaClusterManager;

// Connect to production cluster
try (RamaClusterManager cluster = RamaClusterManager.open(Map.of("conductor.host", "1.2.3.4"))) {
  // Create agent manager for deployed module
  AgentManager manager = AgentManager.create(cluster, "MyModule");

  // Get client for a specific agent
  AgentClient agent = manager.getAgentClient("MyAgent");

  // Use the agent
  String result = agent.invoke("input data");
  System.out.println("Result: " + result);
}
```

#### Clojure API

```clojure
(require '[com.rpl.agent-o-rama :as aor]
         '[com.rpl.rama :as rama])

;; Connect to production cluster
(with-open [cluster (rama/open-cluster {"conductor.host" "1.2.3.4"})]
  ;; Create agent manager for deployed module
  (let [manager (aor/agent-manager cluster "MyModule")]

    ;; Get client for a specific agent
    (let [agent (aor/agent-client manager "MyAgent")]

      ;; Use the agent
      (let [result (aor/agent-invoke agent "input data")]
        (println "Result:" result)))))
```

## Invoking Agents

The simplest way to use an agent is to invoke it synchronously. This blocks until the agent completes and returns the final result.

### Synchronous Invocation

Use `invoke` when you want to wait for the agent to complete before continuing.

#### Java API

```java
// Single argument
String result = agent.invoke("Hello, world!");

// Multiple arguments
Map<String, Object> result = agent.invoke("query", "context", 42);
```

#### Clojure API

```clojure
;; Single argument
(def result (aor/agent-invoke agent "Hello, world!"))

;; Multiple arguments
(def result (aor/agent-invoke agent "query" "context" 42))
```

### Asynchronous Invocation

Use `invokeAsync` / `agent-invoke-async` when you want non-blocking execution. This returns a `CompletableFuture` that completes with the result.

#### Java API

```java
import java.util.concurrent.CompletableFuture;

// Start async invocation
CompletableFuture<String> future = agent.invokeAsync("Hello, world!");

// Do other work while agent executes
System.out.println("Agent is running...");

// Wait for result when needed
String result = future.get();
System.out.println("Result: " + result);

// Or use callbacks
future.thenAccept(result -> {
  System.out.println("Agent completed with: " + result);
});
```

#### Clojure API

```clojure
;; Start async invocation
(let [future (aor/agent-invoke-async agent "Hello, world!")]

  ;; Do other work while agent executes
  (println "Agent is running...")

  ;; Wait for result when needed
  (let [result (.get future)]
    (println "Result:" result))

  ;; Or use callbacks
  (.thenAccept future
    (reify java.util.function.Consumer
      (accept [_ result]
        (println "Agent completed with:" result)))))
```

## Initiating Agent Executions

For more control over agent execution (e.g., for streaming or human input), use `initiate` to start an execution and get a handle for tracking it.

The `initiate` method returns an `AgentInvoke` handle that you can use with other methods like `result`, `nextStep`, `stream`, etc.

### Java API

```java
// Initiate agent execution
AgentInvoke invoke = agent.initiate("Hello, world!");

// The agent is now running asynchronously
// You can use the invoke handle to:
// - Get the result: agent.result(invoke)
// - Stream data: agent.stream(invoke, "node-name", callback)
// - Handle human input: agent.nextStep(invoke)

// Get the final result (blocks until complete)
String result = agent.result(invoke);
```

### Clojure API

```clojure
;; Initiate agent execution
(let [invoke (aor/agent-initiate agent "Hello, world!")]

  ;; The agent is now running asynchronously
  ;; You can use the invoke handle to:
  ;; - Get the result: (aor/agent-result agent invoke)
  ;; - Stream data: (aor/agent-stream agent invoke "node-name" callback)
  ;; - Handle human input: (aor/agent-next-step agent invoke)

  ;; Get the final result (blocks until complete)
  (let [result (aor/agent-result agent invoke)]
    (println "Result:" result)))
```

### Async Initiation

You can also initiate asynchronously to get a future that completes with the invoke handle. This usually complete within a few milliseconds:

#### Java API

```java
CompletableFuture<AgentInvoke> future = agent.initiateAsync("Hello, world!");

future.thenAccept(invoke -> {
  // Use the invoke handle
  String result = agent.result(invoke);
  System.out.println("Result: " + result);
});
```

#### Clojure API

```clojure
(let [future (aor/agent-initiate-async agent "Hello, world!")]
  (.thenAccept future
    (reify java.util.function.Consumer
      (accept [_ invoke]
        ;; Use the invoke handle
        (let [result (aor/agent-result agent invoke)]
          (println "Result:" result))))))
```

## Invoking with Metadata

Metadata allows you to attach custom key-value data to agent executions. This is useful for:
- **Tracking**: User IDs, session IDs, request IDs for correlating agent executions
- **A/B Testing**: Model versions, feature flags, experimental configurations
- **Configuration**: Runtime parameters like model names that agents can access
- **Debugging**: Additional context for troubleshooting specific executions

Metadata is automatically included in traces and analytics, making it easy to filter and analyze agent performance by any metadata dimension. For example, you can set a `"model"` metadata field and then view separate analytics for each model version to compare performance.

### Creating Metadata Context

Metadata is passed via an `AgentContext` object in Java or a map with a `:metadata` key in Clojure. Metadata keys must be strings, and values must be strings, numbers (int, long, float, double), or booleans.

#### Java API

```java
import com.rpl.agentorama.AgentContext;

AgentContext context = AgentContext.metadata("user-id", "user-123")
                                   .metadata("model", "gpt-4");
```

#### Clojure API

```clojure
;; Create context with metadata
(def context {:metadata {"user-id" "user-123"
                         "model" "gpt-4"}})
```

### Invoking with Metadata

Use `invokeWithContext` / `agent-invoke-with-context` to invoke an agent synchronously with metadata.

#### Java API

```java
// Invoke with metadata (blocks until complete)
AgentContext context = AgentContext.metadata("user-id", "user-123")
                                   .metadata("model", "gpt-4");

String result = agent.invokeWithContext(context, "Hello, world!");
System.out.println("Result: " + result);

// Invoke asynchronously with metadata
CompletableFuture<String> future = agent.invokeWithContextAsync(context, "Hello, world!");
String result2 = future.get();
```

#### Clojure API

```clojure
;; Invoke with metadata (blocks until complete)
(let [context {:metadata {"user-id" "user-123"
                          "model" "gpt-4"}}
      result (aor/agent-invoke-with-context agent context "Hello, world!")]
  (println "Result:" result))

;; Invoke asynchronously with metadata
(let [context {:metadata {"user-id" "user-123"
                          "model" "gpt-4"}}
      future (aor/agent-invoke-with-context-async agent context "Hello, world!")
      result (.get future)]
  (println "Result:" result))
```

### Initiating with Metadata

Use `initiateWithContext` / `agent-initiate-with-context` to start an agent execution with metadata and get a handle for tracking.

#### Java API

```java
// Initiate with metadata
AgentContext context = AgentContext.metadata("user-id", "user-123")
                                   .metadata("session-id", "session-456");

AgentInvoke invoke = agent.initiateWithContext(context, "Hello, world!");

// Use the invoke handle for streaming, human input, etc.
agent.stream(invoke, "process", (allChunks, newChunks, reset, complete) -> {
  // Handle streaming...
});

// Get the final result
String result = agent.result(invoke);

// Initiate asynchronously with metadata
CompletableFuture<AgentInvoke> futureInvoke =
  agent.initiateWithContextAsync(context, "Hello, world!");
```

#### Clojure API

```clojure
;; Initiate with metadata
(let [context {:metadata {"user-id" "user-123"
                          "session-id" "session-456"}}
      invoke (aor/agent-initiate-with-context agent context "Hello, world!")]

  ;; Use the invoke handle for streaming, human input, etc.
  (aor/agent-stream agent invoke "process"
    (fn [all-chunks new-chunks reset? complete?]
      ;; Handle streaming...
      ))

  ;; Get the final result
  (let [result (aor/agent-result agent invoke)]
    (println "Result:" result)))

;; Initiate asynchronously with metadata
(let [context {:metadata {"user-id" "user-123"
                          "session-id" "session-456"}}
      future-invoke (aor/agent-initiate-with-context-async agent context "Hello, world!")]
  ;; Use the future...
  )
```

## Getting Agent Results

There are several ways to get results from agent executions, depending on whether you used `invoke` or `initiate`.

### With invoke/invokeAsync

When using `invoke`, the result is returned directly:

```java
// Java - result is returned
String result = agent.invoke("input");
```

```clojure
;; Clojure - result is returned
(def result (aor/agent-invoke agent "input"))
```

### With initiate

When using `initiate`, use the `result` method with the invoke handle:

#### Java API

```java
// Initiate execution
AgentInvoke invoke = agent.initiate("input");

// Get result (blocks until complete)
String result = agent.result(invoke);

// Or get result asynchronously
CompletableFuture<String> futureResult = agent.resultAsync(invoke);
```

#### Clojure API

```clojure
;; Initiate execution
(let [invoke (aor/agent-initiate agent "input")]

  ;; Get result (blocks until complete)
  (let [result (aor/agent-result agent invoke)]
    (println "Result:" result))

  ;; Or get result asynchronously
  (let [future-result (aor/agent-result-async agent invoke)]
    (.thenAccept future-result
      (reify java.util.function.Consumer
        (accept [_ result]
          (println "Result:" result))))))
```

### Checking Completion Status

You can check if an agent execution is complete:

#### Java API

```java
AgentInvoke invoke = agent.initiate("input");

if (agent.isAgentInvokeComplete(invoke)) {
  String result = agent.result(invoke);
  System.out.println("Already complete: " + result);
} else {
  System.out.println("Still running...");
}
```

#### Clojure API

```clojure
(let [invoke (aor/agent-initiate agent "input")]
  (if (aor/agent-invoke-complete? agent invoke)
    (let [result (aor/agent-result agent invoke)]
      (println "Already complete:" result))
    (println "Still running...")))
```

## Other Features

The Agent Client API provides additional features:

### Streaming

For real-time feedback as agents process data, use streaming. See the [Streaming documentation](TODO) for details.

### Human Input

For human-in-the-loop patterns, agents can request human input during execution. See the [Human Input documentation](TODO) for details.
