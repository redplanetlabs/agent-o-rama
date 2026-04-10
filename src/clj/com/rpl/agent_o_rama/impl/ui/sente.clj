(ns com.rpl.agent-o-rama.impl.ui.sente
  (:require
   [clojure.tools.logging :as cljlogging]
   [com.rpl.agent-o-rama.impl.ui.handlers.common :as common] ;; <-- Add this require
   [taoensso.sente :as sente]
   [taoensso.sente.packers.transit :as sente-transit]
   [taoensso.sente.server-adapters.http-kit :as http-kit-adapter]))

(def transit-packer (sente-transit/get-transit-packer :json))

(defn- get-user-id
  "Extract or generate a unique user ID from the Ring request.
  Uses the session :uid if present, otherwise falls back to :sente/nil-uid."
  [request]
  
  (or (get-in request [:session :uid])
      (throw (ex-info "no uid found in session" {:session (:session request)}))))

(let [{:keys [ch-recv send-fn connected-uids
              ajax-post-fn ajax-get-or-ws-handshake-fn]}
      (sente/make-channel-socket-server!
       (http-kit-adapter/get-sch-adapter)
       {:csrf-token-fn nil
        :packer transit-packer
        :user-id-fn get-user-id})]
  (def ring-ajax-post ajax-post-fn)
  (def ring-ajax-get-or-ws-handshake ajax-get-or-ws-handshake-fn)
  (def ch-chsk ch-recv)
  (def chsk-send! send-fn)
  (def connected-uids connected-uids))

(defmulti -event-msg-handler :id)

(defn invoke-event
  "Invoke a UI RPC handler and return a Sente-style reply map."
  [{:keys [id data uid]}]
  (try
    (let [processed-ev-msg (common/preprocess-event-msg {:id id
                                                         :?data data
                                                         :uid uid})

          ;; The rest of the function now operates on the processed message
          {:keys [id ?data uid]} processed-ev-msg
          handler-fn (get-method -event-msg-handler id)]

      ;; Check if we found a specific handler or just the default
      (if (= handler-fn (get-method -event-msg-handler :default))
        ;; This is an unhandled event, use the default logic
        (do (cljlogging/warn "Unhandled Sente event:" id)
            {:success false
             :error (str "No handler for event: " id)
             :http-status 404})

        ;; A specific handler was found, so we wrap it and call it
        (try
          ;; Call the core handler with the clean [data uid] signature
          (let [result (handler-fn ?data uid) ; Pass the processed ?data to the handler
                serializable-result (common/->ui-serializable result)]
            {:success true :data serializable-result})
          (catch Throwable e
            ;; Catch Throwable (not just Exception) to also handle Errors like NoSuchFieldError
            (cljlogging/error e "Error executing handler for" id)
            (let [error-msg (or (.getMessage e) (str e) "Unknown error occurred")]
              {:success false
               :error error-msg
               :http-status 500})))))
    (catch Throwable e
      ;; Catch errors in preprocessing or anywhere else in the handler
      (cljlogging/error e "Fatal error in Sente event-msg-handler")
      {:success false
       :error (str "Fatal error: " (.getMessage e))
       :http-status 500})))

(defn event-msg-handler
  "Smart router that preprocesses the event and then finds the dispatched handler."
  [{:keys [id ?data uid ?reply-fn]}]
  (when ?reply-fn
    (?reply-fn (invoke-event {:id id :data ?data :uid uid}))))

;; A more robust default handler
(defmethod -event-msg-handler :default [_])

(defmethod -event-msg-handler :chsk/ws-ping [_ _])
(defmethod -event-msg-handler :chsk/ws-pong [_ _])
(defmethod -event-msg-handler :chsk/uidport-open [_ _])
(defmethod -event-msg-handler :chsk/uidport-close
  [_data uid]
  ;; Call streaming cleanup - require dynamically to avoid circular deps
  (when-let [cleanup-fn (try
                          (requiring-resolve 'com.rpl.agent-o-rama.impl.ui.handlers.streaming/close-all-streams-for-uid!)
                          (catch Exception e
                            (println "exception" e)
                            nil))]

    (cleanup-fn uid)))

(defonce router_ (atom nil))

(defn stop-sente! []
  (when-let [stop-fn @router_]
    (stop-fn)))

(defn start-sente! []
  (stop-sente!)
  (let [router (sente/start-server-chsk-router! ch-chsk event-msg-handler)]
    (reset! router_ router)
    (println "✓ Sente router started successfully")
    (cljlogging/info "Sente router started")
    router))

