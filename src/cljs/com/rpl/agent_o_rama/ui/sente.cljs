(ns com.rpl.agent-o-rama.ui.sente
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [com.rpl.agent-o-rama.ui.state :as state]))

;; Sente client setup
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"  ; Sente WebSocket endpoint
       {:type :auto
        :packer (sente-transit/get-transit-packer)})]
  
  (def chsk chsk)
  (def ch-chsk ch-recv)  ; ChannelSocket's receive channel
  (def chsk-send! send-fn)  ; ChannelSocket's send API fn
  (def chsk-state state))   ; Watchable, read-only atom

;; Event message router
(defmulti -event-msg-handler
  "Multimethod to handle Sente `event-msg`s"
  :id) ; Dispatch on event-id

(defn event-msg-handler
  "Wraps `-event-msg-handler` with logging"
  [{:as ev-msg :keys [id ?data event]}]
  (js/console.log "Sente event:" (pr-str event))
  (-event-msg-handler ev-msg))

;; Default/fallback handler
(defmethod -event-msg-handler :default
  [{:keys [event]}]
  (js/console.log "Unhandled event:" (pr-str event)))

;; Handle successful connection
(defmethod -event-msg-handler :chsk/state
  [{:keys [?data]}]
  (let [[old-state-map new-state-map] ?data]
    (if (:first-open? new-state-map)
      (js/console.log "Sente channel socket successfully established!")
      (js/console.log "Sente channel socket state change:" new-state-map))))

;; Handle server pong
(defmethod -event-msg-handler :chsk/recv
  [{:keys [?data]}]
  (js/console.log "Push event from server:" (pr-str ?data)))

;; Handle handshake
(defmethod -event-msg-handler :chsk/handshake
  [{:keys [?data]}]
  (let [[?uid ?csrf-token ?handshake-data] ?data]
    (js/console.log "Handshake:" ?data)))

;; API response handlers - these will dispatch to our event system
(defmethod -event-msg-handler :api/agents-response
  [{:keys [?data]}]
  (state/dispatch [:agents/load-success ?data])
  (state/set-loading! :agents false))

;; Router setup
(def router_ (atom nil))

(defn stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router! []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router!
           ch-chsk event-msg-handler)))

;; Helper function to make API requests through Sente
(defn request! [event-id data & [timeout-ms]]
  (let [timeout (or timeout-ms 8000)]
    (chsk-send! [event-id data] timeout
                (fn [cb-reply]
                  (if (sente/cb-success? cb-reply)
                    cb-reply
                    (js/console.error "Sente request failed:" cb-reply))))))

;; Initialize Sente connection
(defn init! []
  (start-router!)
  (js/console.log "Sente initialized"))