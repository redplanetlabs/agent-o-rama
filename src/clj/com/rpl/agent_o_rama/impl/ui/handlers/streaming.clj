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

(println "[STREAMING-BACKEND] *** NAMESPACE LOADED - streaming handlers registered ***")

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
  (println "[STREAMING-BACKEND] close-stream! called" {:uid uid :stream-id stream-id})
  (when-let [proxy (get-in @active-streams [uid stream-id])]
    (try
      (.close ^Closeable proxy)
      (println "[STREAMING-BACKEND] Successfully closed stream" {:uid uid :stream-id stream-id})
      (catch Exception e
        (println "[STREAMING-BACKEND] ERROR closing stream" {:uid uid :stream-id stream-id :error (.getMessage e)})))
    (swap! active-streams update uid dissoc stream-id)))

(defn close-all-streams-for-uid!
  "Closes all active streams for a given UID.
  Called when a client disconnects."
  [uid]
  (println "[STREAMING-BACKEND] close-all-streams-for-uid! called" {:uid uid})
  (when-let [streams (get @active-streams uid)]
    (println "[STREAMING-BACKEND] Closing" (count streams) "streams for uid" uid)
    (doseq [[stream-id _] streams]
      (close-stream! uid stream-id))
    (swap! active-streams dissoc uid)))

;; =============================================================================
;; EVENT HANDLERS
;; =============================================================================

(defmethod sente/-event-msg-handler :stream/start
  [{:keys [invoke-id node-name node-invoke-id stream-id client] :as data} uid]
  (println "[STREAMING-BACKEND] :stream/start received!")
  (println "[STREAMING-BACKEND]   data keys:" (keys data))
  (println "[STREAMING-BACKEND]   invoke-id:" invoke-id)
  (println "[STREAMING-BACKEND]   node-name:" node-name)
  (println "[STREAMING-BACKEND]   node-invoke-id:" node-invoke-id)
  (println "[STREAMING-BACKEND]   stream-id:" stream-id)
  (println "[STREAMING-BACKEND]   client:" (if client "present" "MISSING!"))
  (println "[STREAMING-BACKEND]   uid:" uid)

  (try
    (when-not client
      (println "[STREAMING-BACKEND] ERROR: No client available!")
      (throw (ex-info "No client available" {:invoke-id invoke-id})))

    (when-not stream-id
      (println "[STREAMING-BACKEND] ERROR: stream-id is required!")
      (throw (ex-info "stream-id is required" {:invoke-id invoke-id})))

    ;; Parse the invoke-id (format: "task-id-agent-id")
    (println "[STREAMING-BACKEND] Parsing invoke-id:" invoke-id)
    (let [[task-id agent-id] (common/parse-url-pair invoke-id)
          _ (println "[STREAMING-BACKEND] Parsed task-id:" task-id "agent-id:" agent-id)
          agent-invoke (aor-types/->AgentInvokeImpl task-id agent-id)
          _ (println "[STREAMING-BACKEND] Created AgentInvokeImpl:" agent-invoke)
          ;; Parse node-invoke-id if provided (for specific node streaming)
          parsed-node-invoke-id (when node-invoke-id
                                  (java.util.UUID/fromString node-invoke-id))
          _ (println "[STREAMING-BACKEND] Parsed node-invoke-id:" parsed-node-invoke-id)]

      (when-not parsed-node-invoke-id
        (println "[STREAMING-BACKEND] ERROR: node-invoke-id is required!")
        (throw (ex-info "node-invoke-id is required" {:invoke-id invoke-id :node-name node-name})))

      (println "[STREAMING-BACKEND] Opening Rama proxy for node:" node-name
               "(specific invoke:" parsed-node-invoke-id ")")

      ;; Open the Rama Proxy with callback that bridges to Sente
      (let [proxy (aor/agent-stream-specific
                   client
                   agent-invoke
                   node-name
                   parsed-node-invoke-id
                   (fn [all-chunks new-chunks reset? complete?]
                     ;; THE BRIDGE: Rama Callback -> Sente Push
                     (println "[STREAMING-BACKEND] CALLBACK FIRED!")
                     (println "[STREAMING-BACKEND]   node-invoke-id:" parsed-node-invoke-id)
                     (println "[STREAMING-BACKEND]   all-chunks count:" (count all-chunks))
                     (println "[STREAMING-BACKEND]   new-chunks count:" (count new-chunks))
                     (println "[STREAMING-BACKEND]   new-chunks:" (pr-str (take 3 new-chunks)))
                     (println "[STREAMING-BACKEND]   reset?:" reset?)
                     (println "[STREAMING-BACKEND]   complete?:" complete?)
                     (println "[STREAMING-BACKEND]   Sending to uid:" uid)

                     (try
                       (let [serialized-chunks (common/->ui-serializable new-chunks)
                             _ (println "[STREAMING-BACKEND]   serialized-chunks:" (pr-str (take 3 serialized-chunks)))
                             msg [:stream/update
                                  {:stream-id stream-id
                                   :new-chunks serialized-chunks
                                   :reset? reset?
                                   :complete? complete?}]]
                         (println "[STREAMING-BACKEND]   Calling chsk-send! with msg:" (pr-str msg))
                         (sente/chsk-send! uid msg)
                         (println "[STREAMING-BACKEND]   chsk-send! completed"))
                       (catch Exception e
                         (println "[STREAMING-BACKEND] ERROR in callback:" (.getMessage e))
                         (.printStackTrace e)))))]

        (println "[STREAMING-BACKEND] Proxy created successfully:" proxy)

        ;; Store proxy reference for cleanup
        (swap! active-streams assoc-in [uid stream-id] proxy)
        (println "[STREAMING-BACKEND] Stored proxy in active-streams. Current streams:" (keys @active-streams))

        {:status :ok :stream-id stream-id}))

    (catch Exception e
      (println "[STREAMING-BACKEND] ERROR in :stream/start:" (.getMessage e))
      (.printStackTrace e)
      {:status :error :message (.getMessage e)})))

(defmethod sente/-event-msg-handler :stream/stop
  [{:keys [stream-id] :as data} uid]
  (println "[STREAMING-BACKEND] :stream/stop received!")
  (println "[STREAMING-BACKEND]   stream-id:" stream-id)
  (println "[STREAMING-BACKEND]   uid:" uid)

  (try
    (close-stream! uid stream-id)
    (println "[STREAMING-BACKEND] Stream stopped successfully")
    {:status :ok}

    (catch Exception e
      (println "[STREAMING-BACKEND] ERROR in :stream/stop:" (.getMessage e))
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
  (println "[STREAMING-BACKEND] :stream/debug-active - active streams:" (get-active-streams))
  {:active-streams (get-active-streams)})
