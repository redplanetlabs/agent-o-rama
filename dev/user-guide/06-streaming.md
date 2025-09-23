# Streaming

Your agents can stream data in real-time using [streaming chunks](../terms/streaming-chunk.md) and [streaming subscriptions](../terms/streaming-subscription.md). This enables live progress updates, real-time monitoring, and responsive user interfaces.

> **Reference**: See [Streaming Chunk](../terms/streaming-chunk.md) and [Streaming Subscription](../terms/streaming-subscription.md) documentation for comprehensive details.

## Streaming Chunks

[Streaming chunks](../terms/streaming-chunk.md) are individual pieces of data emitted from agent nodes during execution. Use `stream-chunk!` to send live updates:

```clojure
(aor/node "process-data" nil
  (fn [agent-node dataset]
    (doseq [item dataset]
      ;; Stream progress updates
      (aor/stream-chunk! agent-node
        {:progress (count-processed)
         :current-item item
         :timestamp (System/currentTimeMillis)})
      ;; Process the item
      (process-item item))
    ;; Return final result
    (aor/result! agent-node {:status "completed"
                            :total-processed (count dataset)})))
```

Streaming chunks are separate from the agent's final result - you can stream progress while the agent continues processing.

## Streaming Subscriptions

[Streaming subscriptions](../terms/streaming-subscription.md) let clients receive streaming data from agent nodes during execution. There are three types of subscriptions for different monitoring needs.

### Stream from First Invoke

```clojure
;; Subscribe to streams from first invocation of a node
(let [agent-invoke (aor/agent-initiate agent input)
      ;; Stream from first invoke of "process-data" node
      stream (aor/agent-stream agent agent-invoke "process-data")]

  ;; Process chunks as they arrive
  (doseq [chunk stream]
    (println "Progress:" (:progress chunk)))

  ;; Get final result when streaming ends
  (let [result (aor/agent-result agent agent-invoke)]
    (println "Final result:" result)))
```

### Stream from Specific Invoke

```clojure
;; Subscribe to streams from a specific invocation
(let [agent-invoke (aor/agent-initiate agent input)
      ;; Stream from specific invoke with callback
      stream (aor/agent-stream-specific agent agent-invoke "process-data")]

  ;; Process chunks with invocation context
  (doseq [chunk stream]
    (println "Invocation" (:invocation-id chunk) "progress:" (:data chunk))))
```

### Stream All Invocations

```clojure
;; Subscribe to all invocations of a node (chunks grouped by invocation-id as map)
(let [agent-invoke (aor/agent-initiate agent input)
      all-streams (aor/agent-stream-all agent agent-invoke)]

  ;; Monitor all invocations - returns map from invocation-id to chunk sequences
  (doseq [[invocation-id chunks] all-streams]
    (println "Invocation" invocation-id "chunks:")
    (doseq [chunk chunks]
      (println "  -" (:data chunk)))))
```

## Complete Streaming Example

Here's a complete example from streaming_agent.clj:

```clojure
(ns com.rpl.agent.basic.streaming-agent
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.rama :as rama]
   [com.rpl.rama.test :as rtest]))

(aor/defagentmodule StreamingAgentModule
  [topology]

  (-> (aor/new-agent topology "StreamingAgent")
      (aor/node "process-items" nil
        (fn [agent-node items]
          (let [total (count items)]
            (loop [remaining items
                   processed 0]
              (if (empty? remaining)
                ;; All done - return final result
                (aor/result! agent-node
                           {:status "completed"
                            :total-processed total})
                ;; Process next item and stream progress
                (let [current (first remaining)
                      new-processed (inc processed)]
                  ;; Stream current progress
                  (aor/stream-chunk! agent-node
                    {:type "progress"
                     :processed new-processed
                     :total total
                     :current-item current
                     :percentage (int (* 100 (/ new-processed total)))})

                  ;; Simulate processing time
                  (Thread/sleep 200)

                  ;; Stream item result
                  (aor/stream-chunk! agent-node
                    {:type "item-completed"
                     :item current
                     :result (str "Processed: " current)})

                  ;; Continue with remaining items
                  (recur (rest remaining) new-processed)))))))))

(defn -main [& _args]
  (with-open [ipc (rtest/create-ipc)]
    (rtest/launch-module! ipc StreamingAgentModule {:tasks 1 :threads 1})

    (let [manager (aor/agent-manager ipc (rama/get-module-name StreamingAgentModule))
          agent (aor/agent-client manager "StreamingAgent")
          items ["task-1" "task-2" "task-3" "task-4" "task-5"]]

      (println "Starting streaming agent with items:" items)

      ;; Start agent and get streaming subscription
      (let [agent-invoke (aor/agent-initiate agent items)
            stream (aor/agent-stream agent agent-invoke "process-items")]

        ;; Process streaming chunks
        (println "\n--- Streaming Updates ---")
        (doseq [chunk stream]
          (case (:type chunk)
            "progress"
            (println (format "Progress: %d/%d (%d%%) - Processing: %s"
                           (:processed chunk)
                           (:total chunk)
                           (:percentage chunk)
                           (:current-item chunk)))

            "item-completed"
            (println (format "Completed: %s -> %s"
                           (:item chunk)
                           (:result chunk)))))

        ;; Get final result
        (let [result (aor/agent-result agent agent-invoke)]
          (println "\n--- Final Result ---")
          (println "Status:" (:status result))
          (println "Total processed:" (:total-processed result)))))))
```

## Streaming Patterns

### Progress Updates
```clojure
;; Stream processing progress
(aor/stream-chunk! agent-node
  {:progress current-count
   :total total-count
   :percentage (int (* 100 (/ current-count total-count)))})
```

### Intermediate Results
```clojure
;; Stream partial results as they're computed
(aor/stream-chunk! agent-node
  {:partial-result computed-value
   :computation-step step-name})
```

### Live Notifications
```clojure
;; Stream event notifications
(aor/stream-chunk! agent-node
  {:event "user-action"
   :details action-data
   :timestamp (System/currentTimeMillis)})
```

### Error Reporting
```clojure
;; Stream error details while continuing processing
(when error-occurred
  (aor/stream-chunk! agent-node
    {:error error-message
     :recovery-action "retrying"
     :item failed-item}))
```

## Async Streaming

For non-blocking streaming consumption:

```clojure
;; Process streams asynchronously
(let [agent-invoke (aor/agent-initiate agent input)
      stream (aor/agent-stream agent agent-invoke "processing-node")]

  ;; Handle chunks asynchronously
  (future
    (doseq [chunk stream]
      (handle-stream-chunk chunk)))

  ;; Continue with other work
  (do-other-tasks)

  ;; Get result when ready
  (aor/agent-result agent agent-invoke))
```

## Streaming vs Result

**Use streaming for:**
- Progress updates during long-running operations
- Real-time notifications and events
- Partial results as they become available
- Live monitoring and debugging

**Use result for:**
- Final output of agent execution
- Complete processed data
- Success/failure status
- Summary information

## Key Concepts

You've learned streaming patterns:

1. **[Streaming Chunk](../terms/streaming-chunk.md)**: Individual streamed data pieces
2. **[Streaming Subscription](../terms/streaming-subscription.md)**: Client-side stream receivers
3. **Stream Types**: Progress, results, notifications, errors
4. **Async Patterns**: Non-blocking stream consumption

These patterns enable real-time communication between agents and clients.

## What's Next

You can stream real-time data from your agents. Next, learn [AI Integration](07-ai-integration.md) to connect your agents with language models and external tools.