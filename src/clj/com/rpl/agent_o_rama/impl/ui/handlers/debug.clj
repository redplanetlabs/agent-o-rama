(ns com.rpl.agent-o-rama.impl.ui.handlers.debug
  (:require
   [com.rpl.agent-o-rama.impl.ui.sente :as sente]))

(defonce debug-query-state (atom {}))

(def default-debug-state
  {:config {:delay-ms 0
            :failures-left 0}
   :metrics {:request-count 0
             :in-flight 0
             :max-in-flight 0}})

(defn- normalize-user-state
  [state]
  (merge default-debug-state
         state
         {:config (merge (:config default-debug-state) (:config state))
          :metrics (merge (:metrics default-debug-state) (:metrics state))}))

(defn- update-user!
  [uid f]
  (let [result (atom nil)]
    (swap! debug-query-state
           (fn [state]
             (let [user (normalize-user-state (get state uid))
                   [user' res] (f user)]
               (reset! result res)
               (assoc state uid user'))))
    @result))

(defmethod sente/-event-msg-handler :debug/query-config
  [{:keys [delay-ms failures-left]} uid]
  (update-user!
   uid
   (fn [user]
     (let [config {:delay-ms (or delay-ms 0)
                   :failures-left (or failures-left 0)}]
       [(-> user
            (assoc :config config)
            (assoc :metrics (:metrics default-debug-state)))
        {:config config}]))))

(defmethod sente/-event-msg-handler :debug/query
  [_ uid]
  (let [{:keys [delay-ms should-fail? request-count max-in-flight failures-left]}
        (update-user!
         uid
         (fn [user]
           (let [delay-ms (get-in user [:config :delay-ms] 0)
                 failures-left (get-in user [:config :failures-left] 0)
                 should-fail? (pos? failures-left)
                 remaining (max 0 (dec failures-left))
                 request-count (inc (get-in user [:metrics :request-count] 0))
                 in-flight (inc (get-in user [:metrics :in-flight] 0))
                 max-in-flight (max (get-in user [:metrics :max-in-flight] 0) in-flight)]
             [(-> user
                  (assoc-in [:metrics :request-count] request-count)
                  (assoc-in [:metrics :in-flight] in-flight)
                  (assoc-in [:metrics :max-in-flight] max-in-flight)
                  (assoc-in [:config :failures-left] remaining))
              {:delay-ms delay-ms
               :should-fail? should-fail?
               :request-count request-count
               :max-in-flight max-in-flight
               :failures-left remaining}])))]
    (try
      (when (pos? delay-ms)
        (Thread/sleep (long delay-ms)))
      (when should-fail?
        (throw (ex-info "Debug query error" {:failures-left failures-left})))
      {:request-count request-count
       :max-in-flight max-in-flight
       :failures-left failures-left
       :delay-ms delay-ms}
      (finally
        (swap! debug-query-state update-in [uid :metrics :in-flight]
               (fn [value]
                 (max 0 (dec (or value 0)))))))))
