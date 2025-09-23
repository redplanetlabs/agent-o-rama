# Storage and Objects

Your agents need memory and resources: persistent [stores](../terms/store.md) for state and [agent objects](../terms/agent-objects.md) for shared resources like AI models. This chapter covers the three store types and resource management.

> **Reference**: See [Store](../terms/store.md) and [Agent Objects](../terms/agent-objects.md) documentation for comprehensive details.

## Three Store Types

Agent-O-Rama provides three distributed storage patterns:

1. **[Key-Value Store](../terms/key-value-store.md)**: Simple typed key-value pairs
2. **[Document Store](../terms/document-store.md)**: Structured records with named fields
3. **[PState Store](../terms/pstate-store.md)**: Nested data with path-based access

All stores are distributed, durable, and consistent across your cluster.

## Key-Value Store

Perfect for simple mappings with type safety:

**Declaration:**
```clojure
(aor/declare-key-value-store topology "cache" String Object)
```

**Usage:**
```clojure
(aor/node "cache-data" nil
  (fn [agent-node key value]
    (let [cache (aor/get-store agent-node "cache")]
      ;; Store data
      (store/put! cache key value)
      ;; Retrieve data
      (let [retrieved (store/get cache key)]
        (aor/result! agent-node retrieved)))))
```

Operations:
- `(store/get store key)` - Get value
- `(store/get store key default)` - Get with default
- `(store/put! store key value)` - Store value
- `(store/delete! store key)` - Remove key
- `(store/contains? store key)` - Check existence

## Document Store

For structured records where you update individual fields:

**Declaration:**
```clojure
(aor/declare-document-store topology "users"
  "user-id" String       ; primary key
  "profile" Object       ; field definitions
  "preferences" Object
  "history" Object)
```

**Usage:**
```clojure
(aor/node "update-user" nil
  (fn [agent-node user-id profile-data]
    (let [users (aor/get-store agent-node "users")]
      ;; Update specific field
      (store/put! users user-id "profile" profile-data)
      ;; Get specific field
      (let [profile (store/get users user-id "profile")]
        (aor/result! agent-node profile)))))
```

Operations:
- `(store/get store key field)` - Get field value
- `(store/put! store key field value)` - Update field
- `(store/select store key)` - Get all fields
- `(store/update! store key field update-fn)` - Transform field

## PState Store

For complex nested data with path-based operations:

**Declaration:**
```clojure
(aor/declare-pstate-store topology "analytics")
```

**Usage:**
```clojure
(aor/node "update-metrics" nil
  (fn [agent-node user-id metric-value]
    (let [analytics (aor/get-store agent-node "analytics")]
      ;; Update nested paths
      (store/pstate-transform!
        ["users" user-id "metrics" "total"]
        analytics
        (fnil + 0) metric-value)
      ;; Query nested data
      (let [user-metrics (store/pstate-select ["users" user-id] analytics)]
        (aor/result! agent-node user-metrics)))))
```

Operations:
- `(store/pstate-select path store)` - Query by path
- `(store/pstate-transform! path store fn & args)` - Update by path
- Supports nested maps, vectors, and complex structures

## Agent Objects: Shared Resources

[Agent objects](../terms/agent-objects.md) provide shared resources with managed lifecycles. Two declaration types:

### Static Objects

For simple values and configuration:

```clojure
(aor/declare-agent-object topology "api-key" (System/getenv "API_KEY"))
```

### Object Builders

For complex resources that need initialization:

```clojure
(aor/declare-agent-object-builder topology "openai-model"
  (fn [setup]
    (-> (OpenAiChatModel/builder)
        (.apiKey (aor/get-agent-object setup "api-key"))
        (.modelName "gpt-4o-mini")
        .build)))
```

### Accessing Objects

In agent nodes:

```clojure
(aor/node "use-ai" nil
  (fn [agent-node prompt]
    (let [model (aor/get-agent-object agent-node "openai-model")
          api-key (aor/get-agent-object agent-node "api-key")]
      ;; Use the resources
      (let [response (generate-response model prompt)]
        (aor/result! agent-node response)))))
```

## Complete Storage Example

Here's an agent using all three store types:

```clojure
(aor/defagentmodule StorageModule
  [topology]

  ;; Declare all store types
  (aor/declare-key-value-store topology "sessions" String Object)
  (aor/declare-document-store topology "users"
    "user-id" String
    "profile" Object
    "settings" Object)
  (aor/declare-pstate-store topology "analytics")

  ;; Declare resources
  (aor/declare-agent-object topology "app-config"
    {:max-sessions 1000
     :session-timeout 3600})

  (-> topology
      (aor/new-agent "StorageAgent")
      (aor/node "process-user" nil
        (fn [agent-node user-data]
          (let [sessions (aor/get-store agent-node "sessions")
                users (aor/get-store agent-node "users")
                analytics (aor/get-store agent-node "analytics")
                config (aor/get-agent-object agent-node "app-config")
                user-id (:user-id user-data)
                session-id (str "session-" (random-uuid))]

            ;; Store session in key-value store
            (store/put! sessions session-id
                       {:user-id user-id
                        :created-at (System/currentTimeMillis)})

            ;; Update user profile in document store
            (store/put! users user-id "profile" (:profile user-data))

            ;; Update analytics in PState store
            (store/pstate-transform!
              ["daily-logins" (today-date)]
              analytics
              (fnil inc 0))

            (aor/result! agent-node
              {:session-id session-id
               :user-id user-id
               :config config}))))))
```

## Store vs Objects

**Use stores for:**
- Agent state that persists across executions
- Data that needs distributed access
- Information that changes during agent execution

**Use agent objects for:**
- Shared resources like AI models, databases, APIs
- Configuration values
- Resources with complex initialization
- Expensive objects you want to reuse

## Transaction Patterns

Stores support atomic operations:

```clojure
;; Atomic update with transformation
(store/update! store key field
  (fn [current-value]
    (if (> current-value threshold)
      (reset-value)
      (inc current-value))))
```

For cross-store transactions, use agent node logic to coordinate:

```clojure
(aor/node "transfer" nil
  (fn [agent-node from-user to-user amount]
    (let [accounts (aor/get-store agent-node "accounts")]
      ;; Check balance
      (when (>= (store/get accounts from-user "balance") amount)
        ;; Atomic transfer
        (store/update! accounts from-user "balance" #(- % amount))
        (store/update! accounts to-user "balance" #(+ % amount))
        (aor/result! agent-node :success)))))
```

## Key Concepts

You've learned storage and resource management:

1. **[Key-Value Store](../terms/key-value-store.md)**: Simple typed pairs
2. **[Document Store](../terms/document-store.md)**: Structured field access
3. **[PState Store](../terms/pstate-store.md)**: Nested path operations
4. **[Agent Objects](../terms/agent-objects.md)**: Shared resources
5. **[Agent Object Builder](../terms/agent-object-builder.md)**: Complex resource initialization

These patterns provide persistent state and resource sharing across your distributed agents.

## What's Next

You have persistent storage and shared resources. Next, learn [Streaming](06-streaming.md) to handle real-time data flows and live updates.