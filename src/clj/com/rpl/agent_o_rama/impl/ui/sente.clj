(ns com.rpl.agent-o-rama.impl.ui.sente
  (:require
   [clojure.tools.logging :as log]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common] ;; <-- Add this require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [taoensso.sente.server-adapters.http-kit :as http-kit-adapter]))

(def transit-packer (sente-transit/get-transit-packer :json))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server!
       (http-kit-adapter/get-sch-adapter)
       {:csrf-token-fn nil
        :packer transit-packer})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defmulti -event-msg-handler :id)

(defn event-msg-handler
  "Smart router that preprocesses the event and then finds the dispatched handler."
  [ev-msg]
  (let [processed-ev-msg (common/preprocess-event-msg ev-msg)

        ;; The rest of the function now operates on the processed message
        {:keys [id ?reply-fn ?data uid]} processed-ev-msg
        handler-fn (get-method -event-msg-handler id)]

    ;; Check if we found a specific handler or just the default
    (if (= handler-fn (get-method -event-msg-handler :default))
      ;; This is an unhandled event, use the default logic
      (do
        (log/warn "Unhandled Sente event:" id)
        (when ?reply-fn
          (?reply-fn {:success false, :error (str "No handler for event: " id)})))

      ;; A specific handler was found, so we wrap it and call it
      (try
        ;; Call the core handler with the clean [data uid] signature
        (let [result (handler-fn ?data uid) ; Pass the processed ?data to the handler
              serializable-result (common/->ui-serializable result)]
          (when ?reply-fn
            (?reply-fn {:success true :data serializable-result})))
        (catch Exception e
          (.printStackTrace e) ; Helpful for server-side debugging
          (when ?reply-fn
            (?reply-fn {:success false, :error (.getMessage e)})))))))

;; A more robust default handler
(defmethod -event-msg-handler :default [_])

(defmethod -event-msg-handler :chsk/ws-ping [_ _])
(defmethod -event-msg-handler :chsk/ws-pong [_ _])
(defmethod -event-msg-handler :chsk/uidport-open [_ _])
(defmethod -event-msg-handler :chsk/uidport-close [_ _])

(defonce router_ (atom nil))

(defn stop-sente! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-sente! []
  (stop-sente!)
  (reset! router_ (sente/start-server-chsk-router! ch-chsk event-msg-handler)))
