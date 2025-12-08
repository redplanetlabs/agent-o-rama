(ns com.rpl.agent-o-rama.impl.ui.handlers.streaming
  "Real-time streaming bridge between Rama proxies and Sente WebSockets.
  
  This handler manages the lifecycle of Rama agent-stream proxies and pipes
  their callbacks into Sente push messages for live UI updates."
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common])
  (:import
   [java.io Closeable]))

;; =============================================================================
;; STATE MANAGEMENT
;; =============================================================================

;; Holds active Rama proxies: {uid {stream-id proxy-object}}
;; This ensures we can clean up resources when streams are stopped or
;; when clients disconnect unexpectedly
(defonce active-streams (atom {}))

;; =============================================================================
;; LIFECYCLE MANAGEMENT
;; =============================================================================

(defn- close-stream!
  "Closes a Rama proxy and removes it from active streams.
  This stops network traffic and frees resources."
  [uid stream-id]
  (when-let [proxy (get-in @active-streams [uid stream-id])]
    (try
      (.close ^Closeable proxy)
      (catch Exception _))
    (swap! active-streams update uid dissoc stream-id)))

(defn close-all-streams-for-uid!
  "Closes all active streams for a given UID.
  Called when a client disconnects."
  [uid]
  (when-let [streams (get @active-streams uid)]
    (doseq [[stream-id _] streams]
      (close-stream! uid stream-id))
    (swap! active-streams dissoc uid)))

;; =============================================================================
;; EVENT HANDLERS
;; =============================================================================

(defmethod sente/-event-msg-handler :stream/start
  [{:keys [invoke-id node-name node-invoke-id stream-id client]} uid]
  (try
    (when-not client
      (throw (ex-info "No client available" {:invoke-id invoke-id})))

    (when-not stream-id
      (throw (ex-info "stream-id is required" {:invoke-id invoke-id})))

    (let [[task-id agent-id] (common/parse-url-pair invoke-id)
          agent-invoke (aor-types/->AgentInvokeImpl task-id agent-id)
          parsed-node-invoke-id (when node-invoke-id
                                  (java.util.UUID/fromString node-invoke-id))]

      (when-not parsed-node-invoke-id
        (throw (ex-info "node-invoke-id is required" {:invoke-id invoke-id :node-name node-name})))

      ;; Open the Rama Proxy with callback that bridges to Sente
      (let [proxy (aor/agent-stream-specific
                    client
                    agent-invoke
                    node-name
                    parsed-node-invoke-id
                    (fn [_all-chunks new-chunks reset? complete?]
                     ;; THE BRIDGE: Rama Callback -> Sente Push
                      (try
                        (let [serialized-chunks (common/->ui-serializable new-chunks)]
                          (sente/chsk-send! uid [:stream/update
                                                 {:stream-id stream-id
                                                  :new-chunks serialized-chunks
                                                  :reset? reset?
                                                  :complete? complete?}]))
                        (catch Exception _
                         ;; Client likely disconnected - clean up the stream proxy
                          (close-stream! uid stream-id)))))]

        ;; Store proxy reference for cleanup
        (swap! active-streams assoc-in [uid stream-id] proxy)

        {:status :ok :stream-id stream-id}))

    (catch Exception e
      {:status :error :message (.getMessage e)})))

(defmethod sente/-event-msg-handler :stream/stop
  [{:keys [stream-id]} uid]
  (try
    (close-stream! uid stream-id)
    {:status :ok}
    (catch Exception e
      {:status :error :message (.getMessage e)})))

;; =============================================================================
;; ADMIN / DEBUG
;; =============================================================================

(defn get-active-streams
  "Returns the current state of active streams (for debugging)."
  []
  (into {}
        (map (fn [[uid streams]]
               [uid (into {} (map (fn [[sid _]] [sid :active]) streams))]))
        @active-streams))

(defmethod sente/-event-msg-handler :stream/debug-active
  [_ _]
  {:active-streams (get-active-streams)})
