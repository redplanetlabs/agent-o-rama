(ns com.rpl.agent-o-rama.ui.sente
  (:require [taoensso.sente :as sente]
            [taoensso.sente.packers.transit :as sente-transit]
            [uix.core :as uix]
            [com.rpl.agent-o-rama.ui.state :as state]))

;; Create Transit packer for serialization (must match server)
(def transit-packer
  (sente-transit/get-transit-packer :json))

;; 1. Instantiate the Sente channel socket client
(let [{:keys [chsk ch-recv send-fn state]}
      (sente/make-channel-socket-client!
       "/chsk"
       nil ; No CSRF token for development
       {:type :auto  ; :auto will prefer WebSockets with Ajax fallback
        :packer transit-packer})]

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

;; Handle server responses and forward them to our event system
(defmethod -event-msg-handler :example/hello-response [{:as ev-msg :keys [?data]}]
  (.log js/console "Server replied to hello:" ?data))

;; Live graph and run lifecycle updates
(defmethod -event-msg-handler :graph/node-update [{:as ev-msg :keys [?data]}]
  (let [{:keys [node-id node-data]} ?data]
    (when node-id
      (state/dispatch [:invocation/update-node node-id node-data]))))

(defmethod -event-msg-handler :agent/run-started [{:as ev-msg :keys [?data]}])

(defmethod -event-msg-handler :agent/run-complete [{:as ev-msg :keys [?data]}])

(defmethod -event-msg-handler :agent/run-failed [{:as ev-msg :keys [?data]}])

;; Batch/merge of nodes from server polling
(defmethod -event-msg-handler :graph/nodes-merge [{:as ev-msg :keys [?data]}]
  (let [{:keys [invoke-id nodes]} ?data]
    ;; Always store the data under the correct invoke-id
    (when (and invoke-id (map? nodes) (not-empty nodes))
      ;; Dispatch a single event with the whole batch of nodes
      (state/dispatch [:invocation/merge-live-nodes invoke-id nodes]))))

;; Handler for next leaves update from server
(defmethod -event-msg-handler :live/update-next-leaves [{:as ev-msg :keys [?data]}]
  (let [{:keys [invoke-id next-leaves]} ?data
        current-db @state/app-db
        nodes (get-in current-db [:invocations-data invoke-id :graph :nodes])
        local-unfinished (state/get-unfinished-leaves current-db invoke-id)]
    (state/dispatch [:db/set-value [:invocations-data invoke-id :next-leaves] next-leaves])
    ;; Only mark complete if we have nodes AND no unfinished leaves locally
    (when (and (seq nodes) ;; We have at least some nodes
               (empty? local-unfinished) ;; No unfinished nodes locally
               (empty? next-leaves)) ;; Server also says no more
      (state/dispatch [:db/set-value [:invocations-data invoke-id :is-complete] true]))))

;; Handler to log connection state changes
(defmethod -event-msg-handler :chsk/state [{:as ev-msg :keys [?data]}]
  (let [[old-state new-state] ?data
        connected? (boolean (:open? new-state))]
    ;; Update app-db with connection state
    (state/dispatch [:db/set-value [:sente :connection-state] new-state])
    (state/dispatch [:db/set-value [:sente :connected?] connected?])))

;; Handler for successful handshake
(defmethod -event-msg-handler :chsk/handshake [{:as ev-msg :keys [?data]}]
  (state/dispatch [:db/set-value [:sente :connected?] true]))

;; 4. Router lifecycle functions
(defonce router_ (atom nil))

(defn stop-router! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-router! []
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
   (chsk-send! event-vec timeout-ms callback)))

(defn push!
  "Send a one-way message to the server (no response expected)."
  [event-vec]
  (chsk-send! event-vec))

(defn init! []
  (start-router!)
  ;; Clean up subscriptions when the window/tab is closed
  (.addEventListener js/window "beforeunload" 
                     (fn [_]
                       ;; Send synchronous unsubscribe if possible
                       (when-let [active-sub (get-in @state/app-db [:sente :active-subscription])]
                         (push! [:live/unsubscribe {:sub-key (:sub-key active-sub)
                                                    :sub-type :live-graph}])))))

;; =============================================================================
;; LIVE CONTROL (decoupled from React components)
;; =============================================================================

(defn live-start! [{:keys [module-id agent-name invoke-id interval-ms] :as opts}]
  (request! [:agent/live-graph-start
             {:module-id module-id
              :agent-name agent-name
              :invoke-id invoke-id
              :interval-ms (or interval-ms 1000)}] 5000 nil))

(defn live-stop! [{:keys [module-id agent-name invoke-id] :as opts}]
  (request! [:agent/live-graph-stop
             {:module-id module-id
              :agent-name agent-name
              :invoke-id invoke-id}] 3000 nil))