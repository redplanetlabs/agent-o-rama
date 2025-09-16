# Agent Step

Individual execution units returned by agent processing that represent discrete progress points in agent execution flow.

## Purpose

Agent steps provide fine-grained control over agent execution:

- **Progress Tracking**: Monitor individual execution phases
- **Debugging Support**: Examine agent state at specific execution points
- **Human Interaction**: Handle human input requests at precise moments
- **Flow Control**: Enable step-by-step agent execution

## Step Types

### Result Step
Contains final output when agent execution completes:
```clojure
{:type :result
 :value "Final agent response"
 :metadata {:execution-time-ms 2500}}
```

### Human Input Request Step
Pauses execution to request human input:
```clojure
{:type :human-input-request
 :prompt "Please approve this action:"
 :context {:action "delete-user" :user-id 12345}
 :request-id "req-abc123"}
```

### Continuation Step
Indicates agent continues processing:
```clojure
{:type :continuation
 :status "processing"
 :current-node "analyze-data"
 :progress 0.6}
```

## Client-Side Processing

### Step-by-Step Execution
```clojure
(let [agent-invoke (agent-initiate client "MyAgent" input)]
  (loop [step (agent-next-step client agent-invoke)]
    (case (:type step)
      :result
      (println "Final result:" (:value step))

      :human-input-request
      (let [response (get-user-input (:prompt step))]
        (provide-human-input client agent-invoke (:request-id step) response)
        (recur (agent-next-step client agent-invoke)))

      :continuation
      (do (println "Progress:" (:progress step))
          (recur (agent-next-step client agent-invoke))))))
```

### Asynchronous Processing
```clojure
(agent-next-step-async client agent-invoke
  (fn [step]
    (handle-step step)
    (when-not (= :result (:type step))
      (schedule-next-step))))
```

## Integration with Streaming

Steps coordinate with streaming subscriptions:
- **Ordered Delivery**: Steps maintain causal ordering with stream chunks
- **State Synchronization**: Step progression aligns with streaming data
- **Completion Signaling**: Result steps signal end of streaming

## Monitoring and Observability

Steps provide detailed execution insights:
- **Execution Timeline**: Track agent progress through execution graph
- **Performance Metrics**: Measure time between steps and processing duration
- **Error Context**: Capture detailed error information at step level
- **Resource Utilization**: Monitor memory and CPU usage per step

## Error Handling

### Failed Steps
```clojure
{:type :error
 :error {:message "Processing failed"
         :cause "Network timeout"
         :retry-count 2}
 :recoverable true}
```

### Retry Logic
Steps support automatic retry with:
- **Exponential Backoff**: Increasing delays between retries
- **Max Retry Limits**: Configurable retry thresholds
- **Error Classification**: Distinguish transient vs permanent failures

## Use Cases

### Interactive Debugging
Step through agent execution to understand behavior and identify issues.

### Human-in-the-Loop Workflows
Handle approval processes, data validation, and decision points requiring human judgment.

### Progress Monitoring
Track long-running agent operations with real-time progress updates.

### Error Recovery
Implement sophisticated error handling and retry strategies based on step-level failures.

Agent steps provide the granular control necessary for building robust, interactive, and observable AI agent systems that can handle complex real-world scenarios requiring human oversight and intervention.