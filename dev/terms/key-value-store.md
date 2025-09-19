# Key-Value Store

Typed persistent storage for simple key-value pairs with specified key and value classes, providing distributed state management for agents.

## Purpose

Key-value stores address fundamental state management needs:

- **Simple State Storage**: Efficient storage for basic key-value relationships
- **Type Safety**: Compile-time type checking for keys and values
- **Distributed Access**: Consistent state across cluster nodes
- **Performance**: Optimized for high-frequency read/write operations

## Declaration

```clojure
(aor/declare-key-value-store topology "user-profiles" String UserProfile)
(aor/declare-key-value-store topology "session-data" UUID SessionInfo)
(aor/declare-key-value-store topology "counters" String Long)
```

## Access Patterns

### Basic Operations
```clojure
(aor/node "update-profile" "next"
  (fn [agent-node user-id profile-data]
    (let [store (aor/get-store agent-node "user-profiles")]
      (store/put! store user-id profile-data)
      (aor/emit! agent-node "next" user-id))))
```

### Conditional Updates
```clojure
(let [store (aor/get-store agent-node "counters")
      current (store/get store "page-views" 0)]
  (store/put! store "page-views" (inc current)))
```

### Batch Operations
```clojure
(let [store (aor/get-store agent-node "session-data")]
  (doseq [[session-id data] session-updates]
    (store/put! store session-id data)))
```

## Type Constraints

### Supported Key Types
- `String` - String keys for named lookups
- `UUID` - UUID keys for unique identifiers
- `Long` - Numeric keys for indexed access
- Custom classes implementing appropriate interfaces

### Supported Value Types
- Java objects (serializable)
- Clojure data structures
- Custom classes with proper serialization
- Primitive wrappers (Long, Double, Boolean)

## Store Operations

### Read Operations
```clojure
(store/get store key)           ; Returns value or nil
(store/get store key default)   ; Returns value or default
(store/contains? store key)     ; Check existence
```

### Write Operations
```clojure
(store/put! store key value)    ; Store key-value pair
(store/delete! store key)       ; Remove key
(store/clear! store)            ; Remove all entries
```

## Partitioning and Distribution

### Automatic Partitioning
Keys are automatically distributed across cluster tasks based on hash partitioning for optimal load distribution.

### Task-Local Caching
Frequently accessed entries are cached locally on task nodes for improved read performance.

### Consistency Guarantees
- **Strong Consistency**: All reads return most recent writes
- **Atomic Operations**: Individual operations are atomic
- **Ordered Updates**: Updates to same key are processed in order

## Performance Characteristics

### Read Performance
- Task-local caching provides sub-millisecond access for cached entries
- Distributed reads typically complete in single-digit milliseconds
- Batch reads can be optimized through prefetching

### Write Performance
- Asynchronous writes provide low-latency updates
- Batch writes can improve throughput for bulk operations
- Automatic partitioning prevents hotspots

## Use Cases

### User Session Management
```clojure
(declare-key-value-store topology "sessions" String SessionData)

;; Store session
(store/put! sessions-store session-id
  {:user-id 123 :login-time (Instant/now) :preferences {...}})
```

### Configuration Storage
```clojure
(declare-key-value-store topology "config" String Object)

;; Store configuration
(store/put! config-store "feature-flags"
  {:new-ui true :beta-features false})
```

### Caching Layer
```clojure
(declare-key-value-store topology "cache" String CachedResult)

;; Cache expensive computations
(if-let [cached (store/get cache-store cache-key)]
  cached
  (let [result (expensive-computation)]
    (store/put! cache-store cache-key result)
    result))
```

## Integration with Agent Workflows

Key-value stores integrate seamlessly with agent execution:
- **State Persistence**: Maintain agent state across executions
- **Inter-Agent Communication**: Share data between different agents
- **Session Management**: Track user sessions and preferences
- **Configuration Management**: Store and retrieve runtime configuration

Key-value stores provide the foundation for stateful agent behavior, enabling persistent memory and data sharing across distributed agent executions with strong consistency and high performance.