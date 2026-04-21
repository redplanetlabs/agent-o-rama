(ns com.rpl.agent-o-rama.ui.streaming
  "Node streaming via a single path: POST SSE (`stream-node!!sse`) only.
  No graph-page replay; Rama proxy may be empty (stream ends) or return a final snapshot when complete."
  (:require
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [uix.core :as uix]))

(defn- apply-stream-event!
  [set-stream-data data]
  (set-stream-data
   (fn [prev]
     (if (or (:replace? data) (:reset? data))
       {:chunks (mapv str (:new-chunks data))
        :text (apply str (map str (:new-chunks data)))
        :reset-count (if (:reset? data)
                       (inc (:reset-count prev 0))
                       (:reset-count prev 0))
        :complete? (boolean (:complete? data))
        :streaming? (not (:complete? data))}
       (let [n (mapv str (:new-chunks data))
             ch (into (or (:chunks prev) []) n)]
         {:chunks ch
          :text (str (or (:text prev) "") (apply str n))
          :reset-count (:reset-count prev 0)
          :complete? (boolean (:complete? data))
          :streaming? (not (:complete? data))})))))

(defn use-node-stream
  "When `replay-traced-node?` is true (node has `finish-time-millis` in the trace), loads stream
  chunks via a one-shot RPC instead of SSE so finished traces do not leave hung connections."
  [module-id agent-name invoke-id node-name node-invoke-id replay-traced-node?]
  (let [[stream-data set-stream-data]
        (uix/use-state
         (fn []
           {:chunks []
            :text ""
            :streaming? false
            :reset-count 0
            :complete? false}))]
    
    (uix/use-effect
     (fn []
       (if-not (and module-id agent-name invoke-id node-name node-invoke-id)
         js/undefined
         (if replay-traced-node?
           (do
             (-> (rpc/call
                  :com.rpl.agent-o-rama.impl.ui.rpc.invocations/get-node-stream-snapshot!!
                  {:module-id module-id
                   :agent-name agent-name
                   :invoke-id invoke-id
                   :node-name node-name
                   :node-invoke-id node-invoke-id})
                (.then (fn [data]
                         ;; Snapshot is authoritative for completed traces; replace without reset badge.
                         (apply-stream-event! set-stream-data (assoc data :replace? true))))
                 (.catch (fn [_err]
                           (set-stream-data
                            (fn [prev]
                              (-> prev
                                  (assoc :complete? true :streaming? false)))))))
             (fn [] nil))
           (let [abort
                 (rpc/call-sse
                  :com.rpl.agent-o-rama.impl.ui.rpc.invocations/stream-node!!sse
                  {:module-id module-id
                   :agent-name agent-name
                   :invoke-id invoke-id
                   :node-name node-name
                   :node-invoke-id node-invoke-id}
                  (fn [data] (apply-stream-event! set-stream-data data)))]
             (fn [] (abort))))))
     [module-id agent-name invoke-id node-name node-invoke-id replay-traced-node?])
    stream-data))

(defn clear-stream-buffer!
  [stream-id]
  (rf/dispatch [:stream/cleanup {:stream-id stream-id}]))

(defn get-stream-state
  [stream-id]
  (get-in @rdb/app-db [:streaming :buffers stream-id]))

(defn ^:export test-stream-start!
  [_invoke-id _node-name]
  (let [stream-id (str "stub-" (random-uuid))]
    (println "Streaming stub: test-stream-start! is a no-op" stream-id)
    stream-id))

(defn ^:export test-stream-stop!
  [_stream-id]
  (println "Streaming stub: test-stream-stop! is a no-op"))
