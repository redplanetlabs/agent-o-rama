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

;; =============================================================================
;; AGENT LIFECYCLE EVENT HANDLERS
;; =============================================================================

(defmethod -event-msg-handler :agent/run
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [module-id agent-name args] :as params} ?data
        args (or args [])]
    (try
      (let [client (agents/get-client module-id agent-name)
            ^AgentInvoke inv (apply aor/agent-initiate client args)
            invoke-id (invoke-id-str inv)
            stream (start-streaming! client inv uid invoke-id params)]
        (swap! drivers assoc [uid invoke-id]
               {:client client
                :invoke inv
                :params params
                :status :starting
                :stream stream})
        (chsk-send! uid [:agent/run-started {:invoke-id invoke-id
                                             :task-id (.getTaskId inv)}])
        (resume-driver-loop! uid params inv)
        (when ?reply-fn (?reply-fn {:success true :data {:invoke-id invoke-id :task-id (.getTaskId inv)}})))
      (catch Exception e
        (log/error e "Error starting agent run" {:params ?data})
        (when ?reply-fn (?reply-fn {:success false :error (.getMessage e)}))))))

(defmethod -event-msg-handler :agent/provide-human-input
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [invoke-id request-id response]} ?data]
    (if-let [{:keys [client invoke current-request params]} (get @drivers [uid invoke-id])]
      (try
        (aor/provide-human-input client current-request response)
        ;; Clear waiting state and resume
        (swap! drivers update [uid invoke-id]
               dissoc :current-request :request-id)
        (resume-driver-loop! uid params invoke)
        (when ?reply-fn (?reply-fn {:success true}))
        (catch Exception e
          (log/error e "Error providing human input" {:invoke-id invoke-id :request-id request-id})
          (when ?reply-fn (?reply-fn {:success false :error (.getMessage e)}))))
      (do
        (log/warn "No driver found for human input" {:uid uid :invoke-id invoke-id :request-id request-id})
        (when ?reply-fn (?reply-fn {:success false :error "No active driver"}))))))

(defmethod -event-msg-handler :agent/cancel-run
  [{:as ev-msg :keys [?data uid ?reply-fn]}]
  (let [{:keys [invoke-id]} ?data]
    (if (contains? @drivers [uid invoke-id])
      (do
        (stop-driver! uid invoke-id)
        (chsk-send! uid [:agent/run-failed {:invoke-id invoke-id :error "Cancelled by user"}])
        (when ?reply-fn (?reply-fn {:success true})))
      (when ?reply-fn (?reply-fn {:success false :error "No active driver"})))))

;; =============================================================================
;; AGENT RUN/STREAM/HITL DRIVER SYSTEM
;; =============================================================================

;; Unified driver system for managing agent lifecycles per user session
;; Each driver handles: initiation, token streaming, step advancement, HITL pauses/resumes
;; Structure: {[uid invoke-id] {:client agent-client
;;                              :invoke agent-invoke 
;;                              :params original-params
;;                              :status :starting|:running|:waiting-human|:complete|:failed
;;                              :stream stream-object
;;                              :loop-thread thread
;;                              :current-request human-input-request
;;                              :request-id uuid}}
(defonce drivers (atom {}))

(defn- invoke-id-str ^String [^AgentInvoke inv]
  (str (.getTaskId inv) "-" (.getAgentInvokeId inv)))

(defn- stop-driver! [uid invoke-id]
  (when-let [driver (get @drivers [uid invoke-id])]
    (when-let [stream (:stream driver)]
      (try
        (.close stream)
        (catch Exception _)) )
    (when-let [^Thread t (:loop-thread driver)]
      (try
        (.interrupt t)
        (catch Exception _)))
    (swap! drivers dissoc [uid invoke-id])
    (log/info "Stopped driver" {:uid uid :invoke-id invoke-id})))

(defn- start-streaming!
  "Optionally start a streamAll for a given node name. Returns the stream object or nil."
  [client ^AgentInvoke inv uid invoke-id {:keys [stream-node]}]
  (when (and stream-node (string? stream-node) (not (clojure.string/blank? stream-node)))
    (log/info "Starting token stream" {:uid uid :invoke-id invoke-id :node stream-node})
    (aor/agent-stream-all
     client
     inv
     stream-node
     (fn [all-chunks new-chunks reset-invoke-ids complete?]
       (try
         ;; Send per node-invoke-id updates for simplicity
         (doseq [[node-invoke-id chunks] new-chunks]
           (when (seq chunks)
             (chsk-send! uid
                         [:agent/token-chunk
                          {:invoke-id invoke-id
                           :node-invoke-id (str node-invoke-id)
                           :chunks chunks
                           :reset? (boolean (and reset-invoke-ids (contains? (set reset-invoke-ids) node-invoke-id)))
                           :complete? complete?}])) )
         (catch Exception e
           (log/warn e "Failed sending token chunk" {:invoke-id invoke-id})))))))

(defn- resume-driver-loop!
  [uid {:keys [module-id agent-name] :as params} ^AgentInvoke inv]
  (let [client (agents/get-client module-id agent-name)
        invoke-id (invoke-id-str inv)
        loop-fn (fn loop-fn []
                  (try
                    (loop []
                      (let [step (aor/agent-next-step client inv)]
                        (cond
                          (instance? AgentComplete step)
                          (do
                            (chsk-send! uid [:agent/run-complete {:invoke-id invoke-id
                                                                 :result (.getResult ^AgentComplete step)}])
                            (stop-driver! uid invoke-id))

                          (instance? HumanInputRequest step)
                          (let [^HumanInputRequest req step
                                req-id (str (UUID/randomUUID))
                                req-info {:invoke-id invoke-id
                                          :request-id req-id
                                          :prompt (.getPrompt req)
                                          :node (.getNode req)
                                          :node-invoke-id (str (.getNodeInvokeId req))}]
                            ;; Store waiting request and notify client, then exit loop to wait for response
                            (swap! drivers update [uid invoke-id]
                                   assoc :status :waiting-human :current-request req :request-id req-id)
                            (chsk-send! uid [:agent/human-input-request req-info]))

                          :else
                          (recur))))
                    (catch InterruptedException _
                      (log/info "Driver loop interrupted" {:uid uid :invoke-id invoke-id}))
                    (catch Exception e
                      (log/error e "Driver loop error" {:uid uid :invoke-id invoke-id})
                      (chsk-send! uid [:agent/run-failed {:invoke-id invoke-id :error (.getMessage e)}])
                      (stop-driver! uid invoke-id))))
        thread (doto (Thread. ^Runnable loop-fn)
                 (.setName (str "aor-driver-" uid "-" invoke-id))
                 (.start))]
    (swap! drivers update [uid invoke-id]
           assoc :loop-thread thread :status :running)
    nil))

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
    (swap! client-subscriptions dissoc uid))
  ;; Clean up all drivers for this client
  (let [user-drivers (filter #(= uid (first (first %))) @drivers)]
    (doseq [[[driver-uid invoke-id] _] user-drivers]
      (stop-driver! driver-uid invoke-id))))

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
