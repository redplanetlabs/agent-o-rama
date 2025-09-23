# Client Interaction

Your agents are deployed - now you need to talk to them. This chapter covers [agent managers](../terms/agent-manager.md), [clients](../terms/agent-client.md), and [invocation](../terms/agent-invoke.md) patterns.

> **Reference**: See [Agent Manager](../terms/agent-manager.md) and [Agent Client](../terms/agent-client.md) documentation for comprehensive details.

## Agent Manager: Your Gateway

The [agent manager](../terms/agent-manager.md) is your client-side interface to deployed agents. It handles:
- Connection to the cluster
- Agent discovery
- Client creation

Create an agent manager after deploying your module:

**Clojure:**
```clojure
;; Connect to deployed module
(let [manager (aor/agent-manager cluster-manager module-name)]
  ;; Use manager to get agent clients
  )
```

**Java:**
```java
// Connect to deployed module
AgentManager manager = cluster.agentManager(moduleName);
// Use manager to get agent clients
```

The manager connects to the specific module instance running in your cluster.

## Agent Client: Your Interface

An [agent client](../terms/agent-client.md) provides the interface to a specific agent type. Get one from your manager:

**Clojure:**
```clojure
;; Get client for specific agent
(let [agent (aor/agent-client manager "AsyncAgent")]
  ;; Use client to invoke the agent
  )
```

**Java:**
```java
// Get client for specific agent
AgentClient agent = manager.agentClient("AsyncAgent");
// Use client to invoke the agent
```

Each client is typed to a specific agent. Use the same name you gave the agent in its declaration.

## Synchronous Invocation

The simplest way to call an agent - block until completion:

**Clojure:**
```clojure
;; Simple synchronous call
(let [result (aor/agent-invoke agent "input-data")]
  (println "Result:" result))
;; => Result: Task 'input-data' completed successfully
```

**Java:**
```java
// Simple synchronous call
String result = agent.invoke("input-data");
System.out.println("Result: " + result);
// => Result: Task 'input-data' completed successfully
```

The calling thread blocks until the agent returns an [agent result](../terms/agent-result.md).

## Asynchronous Invocation

For concurrent execution, use asynchronous patterns. Here's the complete async_agent.clj example:

```clojure
(ns com.rpl.agent.basic.async-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

;; Agent that simulates processing time
(aor/defagentmodule AsyncAgentModule
  [topology]
  (-> (aor/new-agent topology "AsyncAgent")
      (aor/node
       "process"
       nil
       (fn [agent-node task-name]
         (println (format "Starting task '%s'" task-name))
         (Thread/sleep 500) ; Simulate work
         (println (format "Completed task '%s'" task-name))
         (aor/result! agent-node
                     (str "Task '" task-name "' completed successfully"))))))

(defn -main [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc AsyncAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc
                                    (rama/get-module-name AsyncAgentModule))
          agent   (aor/agent-client manager "AsyncAgent")]

      ;; Start multiple async executions
      (let [task1-invoke (aor/agent-initiate agent "Data Processing")
            task2-invoke (aor/agent-initiate agent "Report Generation")
            task3-invoke (aor/agent-initiate agent "Email Sending")]

        (println "All tasks initiated, waiting for completion...")

        ;; Get results in any order
        (println "Task 3 result:" (aor/agent-result agent task3-invoke))
        (println "Task 2 result:" (aor/agent-result agent task2-invoke))
        (println "Task 1 result:" (aor/agent-result agent task1-invoke))))))
```

### Agent Initiate

[Agent initiate](../terms/agent-invoke.md) starts execution without blocking:

```clojure
;; Start execution, get handle
(let [invoke-handle (aor/agent-initiate agent "input-data")]
  ;; Do other work while agent runs
  ;; Get result when ready
  )
```

This returns an [agent invoke](../terms/agent-invoke.md) handle for tracking the execution.

### Agent Result

Get the result when you're ready:

```clojure
;; Blocks until this specific execution completes
(let [result (aor/agent-result agent invoke-handle)]
  (println "Execution completed:" result))
```

## Concurrent Execution

The async pattern enables true concurrency:

```clojure
;; Start three agents simultaneously
(let [task1 (aor/agent-initiate agent "Processing")
      task2 (aor/agent-initiate agent "Analysis")
      task3 (aor/agent-initiate agent "Reporting")]

  ;; All three run concurrently
  ;; Get results as they complete
  (println "First done:" (aor/agent-result agent task1))
  (println "Second done:" (aor/agent-result agent task2))
  (println "Third done:" (aor/agent-result agent task3)))
```

Each `agent-initiate` call starts a separate execution instance.

## Agent Complete

When an execution finishes, it reaches [agent complete](../terms/agent-complete.md) state. This happens when:
- A node calls `result!`
- The agent graph terminates naturally
- An error occurs (with automatic [retry mechanism](../terms/retry-mechanism.md))

## Error Handling

Agent invocations handle errors automatically:

```clojure
(try
  (let [result (aor/agent-invoke agent "bad-input")]
    (println "Success:" result))
  (catch Exception e
    (println "Agent failed:" (.getMessage e))))
```

Failed executions trigger the [retry mechanism](../terms/retry-mechanism.md) before propagating errors.

## Update Mode

When you update agent definitions, the [update mode](../terms/update-mode.md) controls how running executions behave:
- **Continue**: Finish with old definition
- **Restart**: Restart with new definition
- **Drop**: Cancel execution

Set this in your agent graph configuration.

## Complete Client Example

Here's the full client interaction pattern:

```clojure
(defn run-agents []
  ;; 1. Create cluster and deploy module
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc MyModule {:tasks 1 :threads 1})

    ;; 2. Create manager and client
    (let [manager (aor/agent-manager ipc (rama/get-module-name MyModule))
          agent   (aor/agent-client manager "MyAgent")]

      ;; 3. Synchronous execution
      (println "Sync result:" (aor/agent-invoke agent "input"))

      ;; 4. Asynchronous execution
      (let [handle (aor/agent-initiate agent "async-input")]
        ;; Do other work
        (Thread/sleep 100)
        ;; Get result when ready
        (println "Async result:" (aor/agent-result agent handle))))))
```

## Key Concepts

You've learned client interaction:

1. **[Agent Manager](../terms/agent-manager.md)**: Gateway to deployed modules
2. **[Agent Client](../terms/agent-client.md)**: Interface to specific agents
3. **[Agent Invoke](../terms/agent-invoke.md)**: Handle for tracking executions
4. **[Agent Complete](../terms/agent-complete.md)**: Completion state
5. **[Retry Mechanism](../terms/retry-mechanism.md)**: Automatic failure recovery
6. **[Update Mode](../terms/update-mode.md)**: Behavior during updates

These concepts enable you to interact with your distributed agents.

## What's Next

You can invoke agents and get results. Next, learn [Agent Execution](04-agent-execution.md) to build complex workflows with multiple nodes and emissions.