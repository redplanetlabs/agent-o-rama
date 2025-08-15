(ns com.rpl.agent-o-rama.impl.ui.sente
  (:use [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.server-adapters.http-kit :as http-kit-adapter]
   [taoensso.sente.packers.transit :as sente-transit]
   [clojure.tools.logging :as log]
   [com.rpl.agent-o-rama.impl.ui.agents :as agents]))

;; Create Transit packer for serialization
(def transit-packer
  (sente-transit/get-transit-packer :json))

;; 1. Instantiate the Sente channel socket server
(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server!
       (http-kit-adapter/get-sch-adapter)
       ;; Sente options:
       {;; We'll just use the default user-id-fn, which looks for a `:uid`
        ;; in the session. This will be nil for now for all anonymous users.

        ;; Disable CSRF token check for development
        :csrf-token-fn nil

        ;; Use Transit packer for proper serialization
        :packer transit-packer})]

  ;; 2. Define the vars for our Sente server
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API fn
  (def connected-uids connected-uids) ; Watchable, read-only atom of user-ids
  )

;; 3. Define the Sente event router
(defmulti -event-msg-handler :id)

;; The multimethod for handling Sente events
(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging and error catching."
  [{:as ev-msg}]
  (-event-msg-handler ev-msg))

;; Default handler for events that don't have a specific implementation
(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [id ?data ?reply-fn uid]}]
  (try
    (let [result (agents/api-handler id ?data uid)]
      (?reply-fn {:success true :data result}))
    (catch Exception e
      (?reply-fn {:success false
                  :error (.getMessage e)}))))

;; do nothing
(defmethod -event-msg-handler :chsk/uidport-open [{:as ev-msg :keys [uid]}])
(defmethod -event-msg-handler :chsk/uidport-close [{:as ev-msg :keys [uid]}])

;; 4. Router lifecycle functions
(defonce router_ (atom nil))

(defn stop-sente! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-sente! []
  (stop-sente!)
  (reset! router_
          (sente/start-server-chsk-router!
           ch-chsk event-msg-handler)))
