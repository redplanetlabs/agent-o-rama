(ns com.rpl.agent-o-rama.ui.streaming
  "React hooks for node streaming output.

  Live WebSocket streaming is not wired. When the graph includes
  `:streaming-chunks` on the node (replay from the server), this hook exposes
  those chunks so the Streaming Output panel can render."
  (:require
   [com.rpl.agent-o-rama.ui.state :as state]))

(defn use-node-stream
  ([module-id agent-name invoke-id node-name node-invoke-id]
   (use-node-stream module-id agent-name invoke-id node-name node-invoke-id nil))

  ([_module-id _agent-name _invoke-id _node-name node-invoke-id opts]
   (let [{:keys [replay-chunks is-live?]} opts
         chunks (or replay-chunks [])
         text (apply str (map str chunks))
         has-replay? (seq chunks)]
     {:chunks (vec chunks)
      :text text
      :streaming? (boolean (and is-live? (not has-replay?)))
      :reset-count 0
      :complete? (or has-replay? (not is-live?))
      :stream-id (when node-invoke-id (str node-invoke-id))})))

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
