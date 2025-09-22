# State Management

Your agents need memory: they track conversations, store results, and share data across distributed executions. Agent-O-Rama (AOR) gives you three powerful distributed stores, each designed for specific data patterns.

Here's what makes AOR's state management special: you get distributed, durable, consistent storage without writing a single line of distributed systems code. Rama handles the replication, partitioning, and fault tolerance. You just store and retrieve data.

> **Reference**: See [Key-Value Store](../terms/key-value-store.md) documentation for comprehensive storage details.

## Three Stores, Three Strengths

Choose the right store for your data pattern:

- **[Key-Value Store](../terms/key-value-store.md)**: Your go-to for simple key-value pairs with type safety
- **Document Store**: Perfect for structured records where you update individual fields
- **PState Store**: Ideal for complex, nested data that you navigate by path

All three stores deliver the same distributed foundation:
- **Distributed access**: Automatically partitioned across cluster [task globals](../glossary.md#task-global)
- **Durable state**: Your data survives restarts and failures with automatic backup
- **Strong consistency**: Reads always return the latest writes with atomic operations
- **High performance**: Task-local caching delivers sub-millisecond access

## Key-Value Store: Your Reliable Workhorse

The [key-value store](../terms/key-value-store.md) handles simple key-value relationships with type safety. Think Redis, but distributed and built into your agent runtime.

**Clojure:**
```clojure
(aor/defagentmodule CacheModule
  [topology]

  ;; Declare the store
  (aor/declare-key-value-store topology "cache" String Object)

  (-> topology
      (aor/new-agent "CacheManager")

      ;; Write to cache
      (aor/node "store" nil
                (fn [agent-node key value]
                  (let [cache (aor/get-store agent-node "cache")]
                    (store/put! cache key value)
                    (aor/result! agent-node :stored))))

      ;; Read from cache
      (aor/node "retrieve" nil
                (fn [agent-node key]
                  (let [cache (aor/get-store agent-node "cache")
                        value (store/get cache key)]
                    (aor/result! agent-node value))))))
```

**Java:**
```java
public class CacheModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        // Declare the store
        topology.declareKeyValueStore("cache", String.class, Object.class);

        topology.newAgent("CacheManager")

            // Write to cache
            .node("store", null, (agentNode, key, value) -> {
                KeyValueStore cache = agentNode.getStore("cache");
                cache.put(key, value);
                agentNode.result("stored");
            })

            // Read from cache
            .node("retrieve", null, (agentNode, key) -> {
                KeyValueStore cache = agentNode.getStore("cache");
                Object value = cache.get(key);
                agentNode.result(value);
            });
    }
}
```

## Document Store: Structured Records

Document stores handle entities with multiple fields using field-based storage. Update individual fields atomically without rewriting the entire record, perfect for entities with multiple attributes that change independently.

**Clojure:**
```clojure
(aor/defagentmodule UserModule
  [topology]

  ;; Declare document store with field types
  (aor/declare-document-store topology "users" String
    {:name String
     :email String
     :score Long
     :preferences Object})

  (-> topology
      (aor/new-agent "UserManager")

      ;; Create user
      (aor/node "create-user" nil
                (fn [agent-node user-id name email]
                  (let [users (aor/get-store agent-node "users")]
                    ;; Set multiple fields at once
                    (store/put-multiple! users user-id
                      {:name name
                       :email email
                       :score 0
                       :preferences {}})
                    (aor/result! agent-node :created))))

      ;; Update score
      (aor/node "update-score" nil
                (fn [agent-node user-id points]
                  (let [users (aor/get-store agent-node "users")
                        current-score (store/get-field users user-id :score)]
                    ;; Update single field
                    (store/put-field! users user-id :score (+ current-score points))
                    (aor/result! agent-node :updated))))

      ;; Get user data
      (aor/node "get-user" nil
                (fn [agent-node user-id]
                  (let [users (aor/get-store agent-node "users")]
                    ;; Get all fields as a map
                    (aor/result! agent-node (store/get-multiple users user-id)))))))
```

**Java:**
```java
public class UserModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        // Declare document store with field types
        Map<String, Class<?>> fields = Map.of(
            "name", String.class,
            "email", String.class,
            "score", Long.class,
            "preferences", Object.class
        );
        topology.declareDocumentStore("users", String.class, fields);

        topology.newAgent("UserManager")

            // Create user
            .node("create-user", null, (agentNode, userId, name, email) -> {
                DocumentStore users = agentNode.getStore("users");
                // Set multiple fields at once
                users.putMultiple(userId, Map.of(
                    "name", name,
                    "email", email,
                    "score", 0L,
                    "preferences", Map.of()
                ));
                agentNode.result("created");
            })

            // Update score
            .node("update-score", null, (agentNode, userId, points) -> {
                DocumentStore users = agentNode.getStore("users");
                Long currentScore = (Long) users.getField(userId, "score");
                // Update single field
                users.putField(userId, "score", currentScore + points);
                agentNode.result("updated");
            })

            // Get user data
            .node("get-user", null, (agentNode, userId) -> {
                DocumentStore users = agentNode.getStore("users");
                // Get all fields as a map
                agentNode.result(users.getMultiple(userId));
            });
    }
}
```

## PState Store: Hierarchical Data

PState stores manage complex, nested data structures using path-based navigation. Navigate and update deep structures efficiently with atomic operations on nested values, ideal for tree-like or graph data.

**Clojure:**
```clojure
(aor/defagentmodule ConversationModule
  [topology]

  ;; PState for conversation trees
  (aor/declare-pstate-store topology "conversations" String)

  (-> topology
      (aor/new-agent "ConversationManager")

      ;; Initialize conversation
      (aor/node "start-conversation" nil
                (fn [agent-node conv-id user-id]
                  (let [convs (aor/get-store agent-node "conversations")]
                    (store/put! convs conv-id
                      {:id conv-id
                       :user user-id
                       :messages []
                       :metadata {:created (System/currentTimeMillis)
                                 :status "active"}})
                    (aor/result! agent-node :started))))

      ;; Add message to conversation
      (aor/node "add-message" nil
                (fn [agent-node conv-id message]
                  (let [convs (aor/get-store agent-node "conversations")]
                    ;; Navigate path and append to messages array
                    (store/update-at! convs conv-id [:messages]
                      (fn [messages]
                        (conj messages
                          {:text message
                           :timestamp (System/currentTimeMillis)})))
                    (aor/result! agent-node :added))))

      ;; Update nested metadata
      (aor/node "update-status" nil
                (fn [agent-node conv-id status]
                  (let [convs (aor/get-store agent-node "conversations")]
                    ;; Update deep nested value
                    (store/put-at! convs conv-id [:metadata :status] status)
                    (aor/result! agent-node :updated))))

      ;; Query nested data
      (aor/node "get-messages" nil
                (fn [agent-node conv-id]
                  (let [convs (aor/get-store agent-node "conversations")]
                    ;; Get value at path
                    (aor/result! agent-node
                      (store/get-at convs conv-id [:messages])))))))
```

**Java:**
```java
public class ConversationModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        // PState for conversation trees
        topology.declarePStateStore("conversations", String.class);

        topology.newAgent("ConversationManager")

            // Initialize conversation
            .node("start-conversation", null, (agentNode, convId, userId) -> {
                PStateStore convs = agentNode.getStore("conversations");
                Map<String, Object> conversation = Map.of(
                    "id", convId,
                    "user", userId,
                    "messages", new ArrayList<>(),
                    "metadata", Map.of(
                        "created", System.currentTimeMillis(),
                        "status", "active"
                    )
                );
                convs.put(convId, conversation);
                agentNode.result("started");
            })

            // Add message to conversation
            .node("add-message", null, (agentNode, convId, message) -> {
                PStateStore convs = agentNode.getStore("conversations");
                // Navigate path and append to messages array
                List<Object> messages = convs.getAt(convId, List.of("messages"));
                messages.add(Map.of(
                    "text", message,
                    "timestamp", System.currentTimeMillis()
                ));
                convs.putAt(convId, List.of("messages"), messages);
                agentNode.result("added");
            })

            // Update nested metadata
            .node("update-status", null, (agentNode, convId, status) -> {
                PStateStore convs = agentNode.getStore("conversations");
                // Update deep nested value
                convs.putAt(convId, List.of("metadata", "status"), status);
                agentNode.result("updated");
            })

            // Query nested data
            .node("get-messages", null, (agentNode, convId) -> {
                PStateStore convs = agentNode.getStore("conversations");
                // Get value at path
                List<Object> messages = convs.getAt(convId, List.of("messages"));
                agentNode.result(messages);
            });
    }
}
```

## Choosing the Right Store

Pick your store based on your data pattern:

**Use Key-Value when you:**
- Need simple key-to-value lookups
- Store atomic values or blobs
- Want maximum performance
- Don't need field-level updates

*Example scenario*: Caching AI model responses, storing session tokens, managing simple flags.

**Use Document Store when you:**
- Have entities with multiple attributes
- Need to update individual fields
- Want structured but flat data
- Query by specific fields

*Example scenario*: User profiles, product catalogs, order records - anything with fixed fields that change independently.

**Use PState when you:**
- Have deeply nested structures
- Need path-based navigation
- Store tree-like or graph data
- Update nested values frequently

*Example scenario*: Conversation histories with threaded replies, workflow state machines, hierarchical configurations.

## The Power of Distributed State

Remember: every store operation in AOR is automatically:
- **Replicated**: Your data exists on multiple nodes
- **Consistent**: All agents see the same data
- **Durable**: Survives crashes and restarts
- **Fast**: Local caching means microsecond access

You write `store/put!`. Rama delivers enterprise-grade distributed storage.

## Cross-Agent State Sharing

Stores are shared across all [agents](../terms/agent.md) in an [agent module](../glossary.md#agent-module), enabling distributed coordination and state synchronization between different agent types:

**Clojure:**
```clojure
(aor/defagentmodule SharedStateModule
  [topology]

  ;; Shared task queue
  (aor/declare-key-value-store topology "task-queue" String Object)
  (aor/declare-document-store topology "task-status" String
    {:status String :assigned-to String :completed-at Long})

  ;; Producer agent adds tasks
  (-> topology
      (aor/new-agent "TaskProducer")
      (aor/node "add-task" nil
                (fn [agent-node task-id task-data]
                  (let [queue (aor/get-store agent-node "task-queue")
                        status (aor/get-store agent-node "task-status")]
                    (store/put! queue task-id task-data)
                    (store/put-field! status task-id :status "pending")
                    (aor/result! agent-node :queued)))))

  ;; Consumer agent processes tasks
  (-> topology
      (aor/new-agent "TaskConsumer")
      (aor/node "process-task" nil
                (fn [agent-node task-id worker-id]
                  (let [queue (aor/get-store agent-node "task-queue")
                        status (aor/get-store agent-node "task-status")
                        task (store/get queue task-id)]
                    ;; Mark as processing
                    (store/put-multiple! status task-id
                      {:status "processing" :assigned-to worker-id})
                    ;; Process task...
                    (process-task task)
                    ;; Mark complete
                    (store/put-multiple! status task-id
                      {:status "complete"
                       :completed-at (System/currentTimeMillis)})
                    (aor/result! agent-node :processed))))))
```

**Java:**
```java
public class SharedStateModule extends AgentModule {
    @Override
    public void configure(AgentTopology topology) {
        // Shared task queue
        topology.declareKeyValueStore("task-queue", String.class, Object.class);
        topology.declareDocumentStore("task-status", String.class, Map.of(
            "status", String.class,
            "assigned-to", String.class,
            "completed-at", Long.class
        ));

        // Producer agent adds tasks
        topology.newAgent("TaskProducer")
            .node("add-task", null, (agentNode, taskId, taskData) -> {
                KeyValueStore queue = agentNode.getStore("task-queue");
                DocumentStore status = agentNode.getStore("task-status");
                queue.put(taskId, taskData);
                status.putField(taskId, "status", "pending");
                agentNode.result("queued");
            });

        // Consumer agent processes tasks
        topology.newAgent("TaskConsumer")
            .node("process-task", null, (agentNode, taskId, workerId) -> {
                KeyValueStore queue = agentNode.getStore("task-queue");
                DocumentStore status = agentNode.getStore("task-status");
                Object task = queue.get(taskId);
                // Mark as processing
                status.putMultiple(taskId, Map.of(
                    "status", "processing",
                    "assigned-to", workerId
                ));
                // Process task...
                processTask(task);
                // Mark complete
                status.putMultiple(taskId, Map.of(
                    "status", "complete",
                    "completed-at", System.currentTimeMillis()
                ));
                agentNode.result("processed");
            });
    }
}
```

## What's Next?

You've mastered distributed state management with [key-value stores](../terms/key-value-store.md), document stores, and PState stores. Now learn how agents communicate in real-time with [Communication Patterns](03-communication-patterns.md).
