(ns com.rpl.agent-o-rama.ui.sente
  (:require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [uix.core :as uix]
   [com.rpl.agent-o-rama.ui.state :as state]))

;; Create Transit packer for serialization (must match server)
(def transit-packer
  (sente-transit/get-transit-packer
   :json
   ;; because theres a difference between cljs.core/UUID and Transit$UUID
   {:handlers
    {"u" (reify Object
           (tag [_ _] "u")
           (rep [_ v] (str v))
           (stringRep [_ v] (str v)))}}
   {:handlers
    {"u" (fn [v] (uuid v))}}))

(defn firefox?
  []
  (when (and (exists? js/navigator)
             (.-userAgent js/navigator))
    (re-find #"Firefox" (.-userAgent js/navigator))))

;; 1. Instantiate the Sente channel socket client
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       nil ; No CSRF token for development
       {:type (if (firefox?) :ajax :auto)
        :packer transit-packer})]

  ;; 2. Define the vars for our Sente client
  (def chsk chsk) ; The channel socket itself
  (def ch-chsk ch-recv) ; ChannelSocket's receive channel
  (def chsk-send! send-fn) ; ChannelSocket's send API function
  (def chsk-state state)); Watchable atom of connection state

;; 3. Define the Sente event router for the client
(defmulti -event-msg-handler :id)

(defn event-msg-handler [ev-msg] (-event-msg-handler ev-msg))

(defmethod -event-msg-handler :default
  [{:as ev-msg :keys [id ?data]}]
  (.log js/console (str "Unhandled Sente event: " id) ?data))

(defmethod -event-msg-handler :chsk/ws-ping [ev-msg])
(defmethod -event-msg-handler :chsk/ws-pong [ev-msg])

;; Handler to log connection state changes
(defmethod -event-msg-handler :chsk/state
  [{:as ev-msg :keys [?data]}]
  (let [[old-state new-state] ?data
        connected? (boolean (:open? new-state))]
    ;; Update app-db with connection state
    (state/dispatch [:db/set-value [:sente :connection-state] new-state])
    (state/dispatch [:db/set-value [:sente :connected?] connected?])))

;; Handler for successful handshake
(defmethod -event-msg-handler :chsk/handshake
  [{:as ev-msg :keys [?data]}]
  (state/dispatch [:db/set-value [:sente :connected?] true]))

;; 4. Router lifecycle functions
(defonce router_ (atom nil))

(defn stop-router!
  []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router!
  []
  (stop-router!)
  (reset! router_
          (sente/start-client-chsk-router! ch-chsk event-msg-handler)))

;; =============================================================================
;; REQUEST HELPERS
;; =============================================================================

(defn request!
  "Make a request through Sente with optional timeout and callback.
   Usage: (request! [:api/get-agents] 5000 (fn [reply] ...))"
  ([event-vec]
   (request! event-vec 5000 nil))
  ([event-vec timeout-ms]
   (request! event-vec timeout-ms nil))
  ([event-vec timeout-ms callback]
   #_(println "SENDING SENTE EVENT:" event-vec timeout-ms callback)
   (chsk-send! event-vec
               timeout-ms
               (fn [reply]
                 #_(println "SENTE EVENT REPLY:" reply)
                 (callback reply)))))

(defn push!
  "Send a one-way message to the server (no response expected)."
  [event-vec]
  (chsk-send! event-vec))

(defn init!
  []
  (start-router!)
  ;; Defer the connection state check to ensure React components have mounted
  ;; and their watches are set up before we dispatch the state
  (js/setTimeout
   (fn []
     (when-let [current-state @chsk-state]
       (let [connected? (boolean (:open? current-state))]
         (state/dispatch [:db/set-value [:sente :connected?] connected?]))))
   100))
