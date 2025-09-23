# Rama Infrastructure

Your agents need a home: the Rama distributed computing platform. This chapter covers [cluster management](../terms/cluster-manager.md), [IPC](../terms/ipc.md) for development, and module lifecycle.

> **Reference**: See [Rama](../terms/rama.md) and [Cluster Manager](../terms/cluster-manager.md) documentation for comprehensive details.

## Rama: The Foundation

[Rama](../terms/rama.md) is the distributed computing platform that powers Agent-O-Rama. It provides:

- **Distributed runtime**: Executes your agents across multiple machines
- **Persistent state**: Durable storage with automatic replication
- **Stream processing**: Real-time data flow through your system
- **Fault tolerance**: Automatic recovery from failures
- **Partitioning**: Scales horizontally across nodes

You don't manage Rama directly - you deploy [agent modules](../terms/agent-module.md) to it.

## IPC: Local Development

[IPC (In-Process Cluster)](../terms/ipc.md) runs a complete Rama cluster in a single JVM process. Perfect for development and testing:

**Clojure:**
```clojure
(with-open [ipc (rtest/create-ipc)]
  ;; Your development code here
  )
```

**Java:**
```java
try (InProcessCluster ipc = InProcessCluster.create()) {
    // Your development code here
}
```

IPC gives you:
- Full Rama functionality locally
- Fast startup for testing
- No external dependencies
- Automatic cleanup on close

## Cluster Manager

The [cluster manager](../terms/cluster-manager.md) is your interface to Rama clusters. It handles:
- Connection management
- Module deployment
- Resource access

For IPC, the cluster itself acts as the manager:

```clojure
;; IPC is both cluster and manager
(with-open [ipc (rtest/create-ipc)]
  ;; Use ipc directly as cluster manager
  (rtest/launch-module! ipc MyModule {:tasks 1 :threads 1}))
```

For production clusters, you create a separate client:

```clojure
(let [cluster-manager (rama/client cluster-config)]
  (rama/launch-module! cluster-manager MyModule {:tasks 8 :threads 4}))
```

## Module Lifecycle

### Launching Modules

Deploy your [agent module](../terms/agent-module.md) to the cluster:

**Clojure:**
```clojure
;; Launch with configuration
(rtest/launch-module! ipc BasicAgentModule
  {:tasks 1      ; number of partitions
   :threads 1})  ; threads per task
```

**Java:**
```java
// Launch with configuration
LaunchConfig config = new LaunchConfig(1, 1);
ipc.launchModule(new BasicAgentModule(), config);
```

Parameters:
- **tasks**: Number of partitions for distributed processing
- **threads**: Concurrent threads per task

### Module Names

Rama identifies modules by name:

```clojure
(rama/get-module-name BasicAgentModule)
;; => "BasicAgentModule"
```

Use this name to access the deployed module.

## Complete Infrastructure Example

Here's how the pieces fit together from basic_agent.clj:

```clojure
(defn -main [& _args]
  ;; 1. Create IPC cluster
  (with-open [ipc (rtest/create-ipc)]

    ;; 2. Launch module to cluster
    (rtest/launch-module! ipc BasicAgentModule
      {:tasks 1 :threads 1})

    ;; 3. Get module name for access
    (let [module-name (rama/get-module-name BasicAgentModule)

          ;; 4. Create agent manager (Chapter 3)
          manager (aor/agent-manager ipc module-name)]

      ;; Your agent operations here
      )))
```

## Development vs Production

### Development (IPC)
```clojure
;; Simple, self-contained
(with-open [ipc (rtest/create-ipc)]
  (rtest/launch-module! ipc MyModule {:tasks 1 :threads 1})
  ;; Test your agents
  )
```

### Production (Distributed Cluster)
```clojure
;; Connect to real cluster
(let [cluster (rama/client production-config)]
  (rama/launch-module! cluster MyModule {:tasks 8 :threads 4})
  ;; Agents run distributed
  )
```

The same agent code runs in both environments. Rama handles the distribution complexity.

## Module Updates

Rama modules support updates without downtime:

```clojure
;; Update existing module
(rama/update-module! cluster MyModule)
```

Your agents continue running during updates. Rama manages the transition.

## Resource Management

Modules can reference resources from other modules:

```clojure
(aor/defagentmodule ConsumerModule
  [topology]
  ;; Reference another module's resources
  (let [other-module (rama/get-module topology "ProducerModule")]
    ;; Use cross-module resources
    ))
```

## Key Concepts

You've learned the Rama infrastructure:

1. **[Rama](../terms/rama.md)**: The distributed platform running everything
2. **[Cluster Manager](../terms/cluster-manager.md)**: Your interface to clusters
3. **[Rama Module](../terms/module.md)**: Deployable units in Rama
4. **[IPC](../terms/ipc.md)**: Local development cluster
5. **Module Lifecycle**: Launch, update, and manage modules

These concepts form the runtime foundation for your agents.

## What's Next

You've deployed modules to Rama. Next, learn [Client Interaction](03-client-interaction.md) to invoke your agents and handle responses.