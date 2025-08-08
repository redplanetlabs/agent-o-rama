(ns com.rpl.agent-o-rama.ui.sente
  (:require [taoensso.sente :as sente]
            [uix.core :as uix]))

;; 1. Instantiate the Sente channel socket client
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       nil ; No CSRF token for development
       {:type :auto})] ; :auto will prefer WebSockets with Ajax fallback

  ;; 2. Define the vars for our Sente client
  (def chsk chsk) ; The channel socket itself
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API function
  (def chsk-state state)) ; Watchable atom of connection state

;; 3. Define the Sente event router for the client
(defmulti -event-msg-handler :id)

(defn event-msg-handler [ev-msg]
  (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default [{:as ev-msg :keys [id ?data]}]
  (.log js/console (str "Unhandled Sente event: " id) ?data))

;; Handler to log connection state changes
(defmethod -event-msg-handler :chsk/state [{:as ev-msg :keys [?data]}]
  (let [[old-state new-state] ?data]
    (.log js/console "Sente connection state change:" new-state)))

;; Handler for successful handshake
(defmethod -event-msg-handler :chsk/handshake [{:as ev-msg :keys [?data]}]
  (.log js/console "✅ Sente handshake successful!" ?data))

;; 4. Router lifecycle functions
(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))

(defn init! []
  (start-router!))
