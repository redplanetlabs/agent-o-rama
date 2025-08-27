(ns com.rpl.agent-o-rama.impl.ui.sente
  (:require
   [clojure.tools.logging :as log]
   [com.rpl.agent-o-rama.impl.ui.handlers.agents] ; Require to register defmethods
   [com.rpl.agent-o-rama.impl.ui.handlers.config] ;
   [com.rpl.agent-o-rama.impl.ui.handlers.datasets] ;
   [com.rpl.agent-o-rama.impl.ui.handlers.invocations] ;
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
  "Wraps `-event-msg-handler` with logging and error catching."
  [{:as ev-msg}]
  (-event-msg-handler ev-msg))

;; A more robust default handler
(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [id ?reply-fn]}]
  (log/warn "Unhandled Sente event:" id)
  (when ?reply-fn
    (?reply-fn {:success false, :error (str "No handler for event: " id)})))

(defmethod -event-msg-handler :chsk/ws-ping [_])
(defmethod -event-msg-handler :chsk/ws-pong [_])
(defmethod -event-msg-handler :chsk/uidport-open [_])
(defmethod -event-msg-handler :chsk/uidport-close [_])

(defonce router_ (atom nil))

(defn stop-sente! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-sente! []
  (stop-sente!)
  (reset! router_ (sente/start-server-chsk-router! ch-chsk event-msg-handler)))