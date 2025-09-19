# Agent Objects

Shared resources that agents access during execution, including AI models, databases, APIs, and other external services with managed lifecycles.

## Purpose

Agent objects solve resource management challenges in distributed agent systems:

- **Resource Sharing**: Enable multiple agents to share expensive resources like AI models
- **Lifecycle Management**: Automatic initialization, connection pooling, and cleanup
- **Configuration Management**: Centralized setup and configuration of external services
- **Performance Optimization**: Connection reuse and caching across agent executions

## Declaration and Setup

### Static Objects
```clojure
(aor/declare-agent-object topology "config-data"
  {:api-key "secret-key"
   :model-name "gpt-4o-mini"})
```

### Dynamic Builder Objects
```clojure
(aor/declare-agent-object-builder topology "openai-model"
  (fn [setup]
    (-> (OpenAiChatModel/builder)
        (.apiKey (get-env "OPENAI_API_KEY"))
        (.modelName "gpt-4o-mini")
        (.timeout (Duration/ofSeconds 60))
        .build)))
```

### Builder with Options
```clojure
(aor/declare-agent-object-builder topology "database"
  (fn [setup]
    (create-connection (:database-url setup)))
  {:database-url "jdbc:postgresql://localhost/agents"
   :pool-size 10})
```

## Access Patterns

### Within Agent Nodes
```clojure
(aor/node "chat" "process"
  (fn [agent-node messages]
    (let [model (aor/get-agent-object agent-node "openai-model")
          db (aor/get-agent-object agent-node "database")]
      ;; Use shared resources
      (process-with-ai model db messages))))
```

### Resource Validation
```clojure
(let [model (aor/get-agent-object agent-node "openai-model")]
  (when-not model
    (throw (ex-info "Model not available" {:object-name "openai-model"}))))
```

## Common Object Types

### AI Models
```clojure
;; OpenAI ChatModel
(declare-agent-object-builder topology "gpt4"
  (fn [_] (OpenAiChatModel/builder)...))

;; LangChain4j StreamingChatModel
(declare-agent-object-builder topology "streaming-model"
  (fn [_] (OpenAiStreamingChatModel/builder)...))
```

### Database Connections
```clojure
(declare-agent-object-builder topology "postgres"
  (fn [{:keys [url user password]}]
    (-> (HikariConfig.)
        (.setJdbcUrl url)
        (.setUsername user)
        (.setPassword password)
        (HikariDataSource.))))
```

### External APIs
```clojure
(declare-agent-object-builder topology "weather-api"
  (fn [{:keys [api-key base-url]}]
    {:client (http/create-client)
     :api-key api-key
     :base-url base-url}))
```

## Lifecycle Management

### Initialization
Objects are created once per topology deployment and shared across all agent executions.

### Connection Pooling
Database and API connections automatically pool connections for optimal performance.

### Cleanup
Resources are automatically closed when topology shuts down.

### Error Handling
Failed object creation is logged and can trigger topology deployment failures.

## Configuration Integration

Objects integrate with agent configuration system:
```clojure
(declare-agent-object-builder topology "configurable-model"
  (fn [config]
    (-> (OpenAiChatModel/builder)
        (.modelName (:model-name config "gpt-4o-mini"))
        (.temperature (:temperature config 0.7))
        .build)))
```

## Monitoring and Observability

Agent objects provide instrumentation:
- **Usage Metrics**: Track object access frequency and patterns
- **Performance Monitoring**: Measure response times and error rates
- **Resource Utilization**: Monitor connection pool health and capacity
- **Error Tracking**: Log and alert on object access failures

## Best Practices

### Immutable Configuration
Use immutable configuration objects to prevent runtime state corruption.

### Graceful Degradation
Handle missing or failed objects gracefully with fallback mechanisms.

### Resource Limits
Configure appropriate timeouts, connection limits, and retry policies.

### Security
Secure credential management through environment variables or secret management systems.

Agent objects enable robust, scalable agent systems by providing managed access to external resources with automatic lifecycle management, performance optimization, and comprehensive monitoring capabilities.