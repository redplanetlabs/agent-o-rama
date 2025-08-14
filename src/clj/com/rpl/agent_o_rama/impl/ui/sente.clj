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

(defmethod -event-msg-handler :api/get-invocation-summary
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/get-full-graph
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/paginate-node
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/execute-fork
  [ev-msg]
  (handle-api-event ev-msg))

(defmethod -event-msg-handler :api/provide-human-input
  [ev-msg]
  (handle-api-event ev-msg))

;; =============================================================================
;; ROBUST SUBSCRIPTION MANAGEMENT
;; =============================================================================

;; The single source of truth for all client subscriptions on the server.
;; Structure: {
;;   :live-graph #{ {:uid "..." :module-id "..." :agent-name "..." :invoke-id "..."} ... },
;;   :token-stream #{ {:uid "..." :stream-key "..."} ... },
;;   :human-input-waiter #{ {:uid "..." :invoke-id "..."} ... }
;; }
(defonce subscriptions (atom {}))

;; Track subscriptions per uid/sub-key for proper cleanup
;; Structure: {uid {sub-key {:sub-type ... :params ...}}}
(defonce client-subscriptions (atom {}))

(defn subscribe!
  "Add a subscription to the registry"
  [uid sub-type params]
  (let [subscription-data (assoc params :uid uid)]
    (swap! subscriptions update sub-type (fnil conj #{}) subscription-data)
    (log/info "Client subscribed:" {:uid uid :type sub-type :params params})))

(defn unsubscribe!
  "Remove a subscription from the registry"
  [uid sub-type params]
  (let [subscription-data (assoc params :uid uid)
        before-count (count (get @subscriptions sub-type))]
    (swap! subscriptions update sub-type disj subscription-data)
    (let [after-count (count (get @subscriptions sub-type))]
      (log/info "Client unsubscribed:" {:uid uid :type sub-type :params params
                                        :before-count before-count :after-count after-count
                                        :removed? (not= before-count after-count)}))))

;; =============================================================================
;; CLIENT-DRIVEN UPDATE HANDLER - Clients request updates based on their state
;; =============================================================================

(defmethod -event-msg-handler :live/get-updates
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [module-id agent-name invoke-id leaves]} ?data]
    ;; Check if uid is actually subscribed to this invoke-id
    (let [subscribed? (contains? (get @subscriptions :live-graph)
                                 {:uid uid :module-id module-id 
                                  :agent-name agent-name :invoke-id invoke-id})]
      (if-not subscribed?
        (log/warn "Client" uid "requested updates for" invoke-id "without subscription")
        (try
          (let [client-objects (agents/objects module-id agent-name)
                
                ;; If there are no leaves, start from the root. Otherwise, start from the leaves.
                start-pairs (if (empty? leaves)
                              (let [root-pstate (:root-pstate client-objects)
                                    [task-id agent-id] (agents/parse-url-pair invoke-id)
                                    root-invoke-id (foreign-select-one [(keypath agent-id) :root-invoke-id] 
                                                                       root-pstate {:pkey task-id})]
                                [[task-id root-invoke-id]])
                              leaves)]
            
            ;; Use the helper function that returns both nodes and next leaves
            (when-let [result (agents/current-invocation-invokes-map module-id agent-name invoke-id start-pairs)]
              (when-let [nodes-map (:invokes-map result)]
                (chsk-send! uid [:graph/nodes-merge {:invoke-id invoke-id :nodes nodes-map}]))
              
              ;; Send back the next set of leaves to continue from!
              (chsk-send! uid [:live/update-next-leaves {:invoke-id invoke-id
                                                         :next-leaves (:next-task-invoke-pairs result)}])))
          (catch Exception e
            (log/error e "Error fetching live updates for" invoke-id)))))))

;; =============================================================================
;; SUBSCRIPTION EVENT HANDLERS
;; =============================================================================

(defmethod -event-msg-handler :live/subscribe
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [sub-key sub-type params]} ?data]
    (log/info "Subscription request:" {:uid uid :sub-key sub-key :sub-type sub-type :params params})
    ;; Track this subscription for this client
    (swap! client-subscriptions assoc-in [uid sub-key] {:sub-type sub-type :params params})
    (log/info "Stored subscription. Current keys for uid" uid ":" (keys (get @client-subscriptions uid)))
    ;; Add to master subscription registry
    (subscribe! uid sub-type params)
    (when ?reply-fn
      (?reply-fn {:success true :data {:subscribed true}}))))

(defmethod -event-msg-handler :live/unsubscribe
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [sub-key sub-type]} ?data
        subscription (get-in @client-subscriptions [uid sub-key])]
    (log/info "Unsubscribe request:" {:uid uid :sub-key sub-key :sub-type sub-type})
    (if subscription
      (let [{:keys [params]} subscription]
        (log/info "Found subscription to remove:" {:sub-key sub-key :params params})
        ;; Remove from client tracking
        (swap! client-subscriptions update uid dissoc sub-key)
        ;; Remove from master subscription registry
        (unsubscribe! uid sub-type params))
      (log/warn "No subscription found for sub-key:" sub-key "uid:" uid 
                "Available keys:" (keys (get @client-subscriptions uid))))
    (when ?reply-fn
      (?reply-fn {:success true :data {:unsubscribed true}}))))

;; Handler for client connecting/disconnecting
(defmethod -event-msg-handler :chsk/uidport-open
  [{:as ev-msg :keys [uid]}]
  (log/info (str "Sente client connected, uid: " uid)))

(defmethod -event-msg-handler :chsk/uidport-close
  [{:as ev-msg :keys [uid]}]
  (log/info (str "Sente client disconnected, cleaning up subscriptions and drivers for uid: " uid))
  ;; Clean up all subscriptions for this client
  (when-let [client-subs (get @client-subscriptions uid)]
    (doseq [[sub-key {:keys [sub-type params]}] client-subs]
      (unsubscribe! uid sub-type params))
    (swap! client-subscriptions dissoc uid)))

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
