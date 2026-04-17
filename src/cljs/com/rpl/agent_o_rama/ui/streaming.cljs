(ns com.rpl.agent-o-rama.ui.streaming
  "Node streaming via a single path: POST SSE (`stream-node!!sse`) only.
  No graph-page replay; Rama proxy may be empty (stream ends) or return a final snapshot when complete."
  (:require
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.ui.state :as state]
   [uix.core :as uix]))

(defn use-node-stream
  [module-id agent-name invoke-id node-name node-invoke-id]
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
         (let [abort
               (rpc/call-sse
                :com.rpl.agent-o-rama.impl.ui.rpc.invocations/stream-node!!sse
                {:module-id module-id
                 :agent-name agent-name
                 :invoke-id invoke-id
                 :node-name node-name
                 :node-invoke-id node-invoke-id}
                (fn [data]
                  (set-stream-data
                   (fn [prev]
                     (if (:reset? data)
                       {:chunks (mapv str (:new-chunks data))
                        :text (apply str (map str (:new-chunks data)))
                        :reset-count (inc (:reset-count prev 0))
                        :complete? (boolean (:complete? data))
                        :streaming? (not (:complete? data))}
                       (let [n (mapv str (:new-chunks data))
                             ch (into (or (:chunks prev) []) n)]
                         {:chunks ch
                          :text (str (or (:text prev) "") (apply str n))
                          :reset-count (:reset-count prev 0)
                          :complete? (boolean (:complete? data))
                          :streaming? (not (:complete? data))}))))))]
           (fn [] (abort)))))
     [module-id agent-name invoke-id node-name node-invoke-id])
    stream-data))

(defn clear-stream-buffer!
  [stream-id]
  (state/dispatch [:stream/cleanup {:stream-id stream-id}]))

(defn get-stream-state
  [stream-id]
  (get-in @state/app-db [:streaming :buffers stream-id]))

(defn ^:export test-stream-start!
  [_invoke-id _node-name]
  (let [stream-id (str "stub-" (random-uuid))]
    (println "Streaming stub: test-stream-start! is a no-op" stream-id)
    stream-id))

(defn ^:export test-stream-stop!
  [_stream-id]
  (println "Streaming stub: test-stream-stop! is a no-op"))
