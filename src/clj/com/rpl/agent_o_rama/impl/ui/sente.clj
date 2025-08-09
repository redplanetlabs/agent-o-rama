(ns com.rpl.agent-o-rama.impl.ui.sente
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
  [{:as ev-msg :keys [id ?data]}]
  (log/debug (str "Unhandled Sente event: " id " with data: " (pr-str ?data))))

;; Our "hello world" handler
(defmethod -event-msg-handler :example/hello
  [{:as ev-msg :keys [?data]}]
  (println "[SERVER] Received :example/hello with data:" (pr-str ?data)))

;; =============================================================================
;; API HANDLERS - Clean abstraction for request/response pattern
;; =============================================================================

;; Example of how to add more endpoints:
;; (defmethod agents/api-handler :api/get-graph
;;   [_ {:keys [module-id agent-name]} uid]
;;   (:body (agents/get-graph {:path-params {:module-id module-id 
;;                                           :agent-name agent-name}})))

;; Generic handler for all API events
(defn handle-api-event
  [{:as ev-msg :keys [id ?data ?reply-fn uid]}]
  (println "[SERVER] Received" id "request from uid:" uid "with data:" ?data)
  (when ?reply-fn
    (try
      (let [result (agents/api-handler id ?data uid)]
        (println "[SERVER] Sending response for" id)
        (?reply-fn {:success true :data result}))
      (catch Exception e
        (log/error e "Error handling API event" id)
        (?reply-fn {:success false 
                    :error (.getMessage e)})))))

;; Register handlers for each API endpoint
(defmethod -event-msg-handler :api/get-agents
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/get-invocations
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/get-graph
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/run-agent
  [ev-msg]
  (handle-api-event ev-msg))

;; =============================================================================
;; LIVE GRAPH POLLING via Sente
;; =============================================================================

(defonce live-pollers (atom {}))
;; Structure: {uid { [module-id agent-name invoke-id] {:stop? atom<boolean>}}}

(defn- poll-once-and-push!
  [uid {:keys [module-id agent-name invoke-id]}]
  (try
    (when-let [nodes-map (agents/current-invocation-invokes-map module-id agent-name invoke-id)]
      ;; Push a merge of all nodes at once to minimize client work
      (chsk-send! uid [:graph/nodes-merge {:invoke-id invoke-id :nodes nodes-map}]))
    (catch Exception e
      (log/error e "Error during live graph poll" {:uid uid :invoke-id invoke-id}))))

(defn- start-live-poller!
  [uid {:keys [module-id agent-name invoke-id interval-ms] :as params}]
  (let [interval (long (or interval-ms 1000))
        key [module-id agent-name invoke-id]
        stop? (atom false)]
    (swap! live-pollers assoc-in [uid key] {:stop? stop?})
    (future
      (loop []
        (when-not @stop?
          (poll-once-and-push! uid params)
          (Thread/sleep interval)
          (recur))))))

(defn- stop-live-poller!
  [uid {:keys [module-id agent-name invoke-id]}]
  (let [key [module-id agent-name invoke-id]
        entry (get-in @live-pollers [uid key])]
    (when entry
      (reset! (:stop? entry) true)
      (swap! live-pollers update uid dissoc key))))

(defmethod -event-msg-handler :agent/live-graph-start
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (start-live-poller! uid ?data)
  (when ?reply-fn
    (?reply-fn {:success true :data {:started true}})))

(defmethod -event-msg-handler :agent/live-graph-stop
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (stop-live-poller! uid ?data)
  (when ?reply-fn
    (?reply-fn {:success true :data {:stopped true}})))

;; Handler for client connecting/disconnecting
(defmethod -event-msg-handler :chsk/uidport-open
  [{:as ev-msg :keys [uid]}]
  (log/info (str "Sente client connected, uid: " uid)))

(defmethod -event-msg-handler :chsk/uidport-close
  [{:as ev-msg :keys [uid]}]
  (log/info (str "Sente client disconnected, uid: " uid))
  ;; Stop any live pollers tied to this uid
  (when-let [entries (get @live-pollers uid)]
    (doseq [[k {:keys [stop?]}] entries]
      (when stop?
        (reset! stop? true)))
    (swap! live-pollers dissoc uid)))

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
