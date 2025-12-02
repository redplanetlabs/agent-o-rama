(ns com.rpl.agent-o-rama.ui.streaming
  "React hooks and utilities for real-time streaming from agent nodes.
  
  Provides a simple interface to subscribe to streaming chunks from agent
  execution, with automatic lifecycle management and cleanup."
  (:require
   [uix.core :as uix]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.state :as state]))

;; =============================================================================
;; STREAMING HOOK
;; =============================================================================

(defn use-node-stream
  "Hook to stream chunks from a specific agent node invocation in real-time.
  
  Automatically manages the streaming lifecycle:
  - Opens Rama proxy on mount
  - Receives pushed chunks via WebSocket
  - Cleans up proxy on unmount
  
  Parameters:
  - module-id: Module ID (URL-encoded)
  - agent-name: Agent name (URL-encoded)
  - invoke-id: String in format 'task-id-agent-id' (agent invocation)
  - node-name: String name of the node to stream from
  - node-invoke-id: UUID of the specific node invocation (to distinguish multiple invocations of same node)
  - opts: Optional map with:
    - :stream-id - Custom stream ID (defaults to auto-generated)
    - :on-chunk - Callback fn called with each new chunk
    - :on-complete - Callback fn called when streaming completes
    - :on-reset - Callback fn called when stream resets (node retry)
  
  Returns map with:
  - :chunks - Vector of all chunks received so far
  - :text - String concatenation of all chunks (assumes text chunks)
  - :streaming? - Boolean indicating if streaming is in progress
  - :reset-count - Number of times the stream has reset
  - :stream-id - The stream ID being used
  
  Example:
  ```
  (let [{:keys [text streaming?]} (use-node-stream module-id agent-name invoke-id \"llm-node\" node-invoke-id)]
    ($ :div
       ($ :pre text)
       (when streaming?
         ($ :span.animate-pulse \"█\"))))
  ```"
  ([module-id agent-name invoke-id node-name node-invoke-id]
   (use-node-stream module-id agent-name invoke-id node-name node-invoke-id nil))

  ([module-id agent-name invoke-id node-name node-invoke-id opts]
   (let [;; Generate unique stream ID based on the specific node invocation
         ;; This ensures each node instance gets its own stream
         stream-id (uix/use-memo
                    (fn []
                      (or (:stream-id opts)
                          ;; Use node-invoke-id to make stream unique per node instance
                          (str invoke-id "-" node-name "-" node-invoke-id)))
                    [invoke-id node-name node-invoke-id])

         ;; Subscribe to the stream buffer in app-db
         stream-state (state/use-sub [:streaming :buffers stream-id])

         chunks (:chunks stream-state [])
         complete? (:complete? stream-state false)
         reset-count (:reset-count stream-state 0)

         ;; Derive text for display (assumes chunks are strings or have :chunk field)
         text (apply str
                     (map (fn [chunk]
                            (if (string? chunk)
                              chunk
                              (:chunk chunk chunk)))
                          chunks))]

     ;; Manage streaming lifecycle
     (uix/use-effect
      (fn []
        ;; 1. Start streaming on mount
        (sente/push! [:stream/start {:module-id module-id
                                     :agent-name agent-name
                                     :invoke-id invoke-id
                                     :node-name node-name
                                     :node-invoke-id (str node-invoke-id)
                                     :stream-id stream-id}])

        ;; 2. Cleanup on unmount
        (fn []
          (sente/push! [:stream/stop {:stream-id stream-id}])
          (state/dispatch [:stream/cleanup {:stream-id stream-id}])))
      ;; Re-run effect if any of these change
      [module-id agent-name invoke-id node-name node-invoke-id stream-id])

     ;; Call optional callbacks
     (uix/use-effect
      (fn []
        (when (and (seq chunks) (:on-chunk opts))
          ((:on-chunk opts) (last chunks))))
      [chunks])

     (uix/use-effect
      (fn []
        (when (and complete? (:on-complete opts))
          ((:on-complete opts) chunks)))
      [complete?])

     (uix/use-effect
      (fn []
        (when (and (pos? reset-count) (:on-reset opts))
          ((:on-reset opts) reset-count)))
      [reset-count])

     ;; Return streaming state
     {:chunks chunks
      :text text
      :streaming? (not complete?)
      :reset-count reset-count
      :complete? complete?
      :stream-id stream-id})))

;; =============================================================================
;; UTILITIES
;; =============================================================================

(defn clear-stream-buffer!
  "Manually clear a stream buffer. Usually not needed as cleanup is automatic."
  [stream-id]
  (state/dispatch [:stream/cleanup {:stream-id stream-id}]))

(defn get-stream-state
  "Get the current state of a stream (without subscribing).
  Returns nil if stream doesn't exist."
  [stream-id]
  (get-in @state/app-db [:streaming :buffers stream-id]))

;; =============================================================================
;; MANUAL TEST FUNCTIONS (for browser console)
;; =============================================================================

(defn ^:export test-stream-start!
  "Test function to manually start a stream from browser console.
   Usage: com.rpl.agent_o_rama.ui.streaming.test_stream_start_BANG_('0-uuid-here', 'node-name')
   
   Or in ClojureScript REPL:
   (streaming/test-stream-start! \"0-uuid-here\" \"node-name\")"
  [invoke-id node-name]
  (let [stream-id (str "test-" (random-uuid))]
    (sente/request! [:stream/start {:invoke-id invoke-id
                                     :node-name node-name
                                     :stream-id stream-id}]
                    10000
                    (fn [reply]
                      (println "Test stream reply:" (pr-str reply))))
    stream-id))

(defn ^:export test-stream-stop!
  "Test function to manually stop a stream from browser console.
   Usage: com.rpl.agent_o_rama.ui.streaming.test_stream_stop_BANG_('stream-id')"
  [stream-id]
  (sente/push! [:stream/stop {:stream-id stream-id}]))
