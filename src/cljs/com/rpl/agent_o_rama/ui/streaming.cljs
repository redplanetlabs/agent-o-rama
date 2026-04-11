(ns com.rpl.agent-o-rama.ui.streaming
  "React hooks for node streaming output.

  Real-time streaming previously used WebSockets; that path is not wired yet.
  Hooks return empty/inactive state until a transport (e.g. SSE) is implemented."
  (:require
   [com.rpl.agent-o-rama.ui.state :as state]))

(defn use-node-stream
  ([_module-id _agent-name _invoke-id _node-name _node-invoke-id]
   (use-node-stream _module-id _agent-name _invoke-id _node-name _node-invoke-id nil))

  ([_module-id _agent-name _invoke-id _node-name _node-invoke-id _opts]
   {:chunks []
    :text ""
    :streaming? false
    :reset-count 0
    :complete? true
    :stream-id nil}))

(defn clear-stream-buffer!
  [stream-id]
  (state/dispatch [:stream/cleanup {:stream-id stream-id}]))

(defn get-stream-state
  [stream-id]
  (get-in @state/app-db [:streaming :buffers stream-id]))

(defn ^:export test-stream-start!
  [_invoke-id _node-name]
  (let [stream-id (str "stub-" (random-uuid))]
    (.warn js/console "Streaming stub: test-stream-start! is a no-op" stream-id)
    stream-id))

(defn ^:export test-stream-stop!
  [_stream-id]
  (.warn js/console "Streaming stub: test-stream-stop! is a no-op"))
