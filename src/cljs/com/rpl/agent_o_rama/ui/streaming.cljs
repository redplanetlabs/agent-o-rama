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
  "Hook to stream chunks from a specific agent node in real-time.
  
  Automatically manages the streaming lifecycle:
  - Opens Rama proxy on mount
  - Receives pushed chunks via WebSocket
  - Cleans up proxy on unmount
  
  Parameters:
  - invoke-id: String in format 'task-id-agent-id'
  - node-name: String name of the node to stream from
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
  (let [{:keys [text streaming?]} (use-node-stream invoke-id \"llm-node\")]
    ($ :div
       ($ :pre text)
       (when streaming?
         ($ :span.animate-pulse \"█\"))))
  ```"
  ([invoke-id node-name]
   (use-node-stream invoke-id node-name nil))

  ([invoke-id node-name opts]
   (let [;; Generate unique stream ID (or use provided one)
         stream-id (uix/use-memo
                    (fn []
                      (let [id (or (:stream-id opts)
                                   (str invoke-id "-" node-name "-" (random-uuid)))]
                        (println "[STREAMING-FRONTEND] Generated stream-id:" id)
                        id))
                    [invoke-id node-name])

         ;; Subscribe to the stream buffer in app-db
         stream-state (state/use-sub [:streaming :buffers stream-id])
         _ (println "[STREAMING-FRONTEND] stream-state for" stream-id ":" (pr-str stream-state))

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

     (println "[STREAMING-FRONTEND] use-node-stream render:"
              {:invoke-id invoke-id
               :node-name node-name
               :stream-id stream-id
               :chunk-count (count chunks)
               :complete? complete?})

     ;; Manage streaming lifecycle
     (uix/use-effect
      (fn []
        ;; 1. Start streaming on mount
        (println "[STREAMING-FRONTEND] useEffect MOUNT - Starting stream")
        (println "[STREAMING-FRONTEND]   invoke-id:" invoke-id)
        (println "[STREAMING-FRONTEND]   node-name:" node-name)
        (println "[STREAMING-FRONTEND]   stream-id:" stream-id)

        (let [msg [:stream/start {:invoke-id invoke-id
                                  :node-name node-name
                                  :stream-id stream-id}]]
          (println "[STREAMING-FRONTEND]   Sending via sente/push!:" (pr-str msg))
          (sente/push! msg)
          (println "[STREAMING-FRONTEND]   push! completed"))

        ;; 2. Cleanup on unmount
        (fn []
          (println "[STREAMING-FRONTEND] useEffect UNMOUNT - Stopping stream")
          (println "[STREAMING-FRONTEND]   stream-id:" stream-id)
          (sente/push! [:stream/stop {:stream-id stream-id}])
          (state/dispatch [:stream/cleanup {:stream-id stream-id}])
          (println "[STREAMING-FRONTEND]   Cleanup dispatched")))
      ;; Re-run effect if invoke-id or node-name changes
      [invoke-id node-name stream-id])

     ;; Call optional callbacks
     (uix/use-effect
      (fn []
        (when (and (seq chunks) (:on-chunk opts))
          (println "[STREAMING-FRONTEND] Calling on-chunk callback")
          ((:on-chunk opts) (last chunks))))
      [chunks])

     (uix/use-effect
      (fn []
        (when (and complete? (:on-complete opts))
          (println "[STREAMING-FRONTEND] Calling on-complete callback")
          ((:on-complete opts) chunks)))
      [complete?])

     (uix/use-effect
      (fn []
        (when (and (pos? reset-count) (:on-reset opts))
          (println "[STREAMING-FRONTEND] Calling on-reset callback")
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
  (println "[STREAMING-FRONTEND] clear-stream-buffer!" stream-id)
  (state/dispatch [:stream/cleanup {:stream-id stream-id}]))

(defn get-stream-state
  "Get the current state of a stream (without subscribing).
  Returns nil if stream doesn't exist."
  [stream-id]
  (let [state (get-in @state/app-db [:streaming :buffers stream-id])]
    (println "[STREAMING-FRONTEND] get-stream-state" stream-id ":" (pr-str state))
    state))

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
    (println "[STREAMING-TEST] Starting test stream")
    (println "[STREAMING-TEST]   invoke-id:" invoke-id)
    (println "[STREAMING-TEST]   node-name:" node-name)
    (println "[STREAMING-TEST]   stream-id:" stream-id)
    ;; Use request! with callback to ensure we get a response and can debug
    (sente/request! [:stream/start {:invoke-id invoke-id
                                     :node-name node-name
                                     :stream-id stream-id}]
                    10000
                    (fn [reply]
                      (println "[STREAMING-TEST] Got reply from server:" (pr-str reply))))
    (println "[STREAMING-TEST] Request sent! Watch for backend logs and reply.")
    (println "[STREAMING-TEST] Stream ID for cleanup:" stream-id)
    stream-id))

(defn ^:export test-stream-stop!
  "Test function to manually stop a stream from browser console.
   Usage: com.rpl.agent_o_rama.ui.streaming.test_stream_stop_BANG_('stream-id')"
  [stream-id]
  (println "[STREAMING-TEST] Stopping test stream:" stream-id)
  (sente/push! [:stream/stop {:stream-id stream-id}])
  (println "[STREAMING-TEST] Stop sent!"))
