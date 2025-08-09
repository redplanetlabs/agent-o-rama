(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]))

(defhook use-sente-query 
  "A hook for making Sente-based queries with automatic connection handling.
   
   Options:
   - :query-key - Vector path to store the query state (e.g. [:agents])
   - :sente-event - Vector event to send to server (e.g. [:api/get-agents])
   - :timeout-ms - Timeout in milliseconds (default: 10000)
   
   Returns:
   - :data - The fetched data
   - :loading? - Boolean indicating if request is in progress
   - :error - Error message if request failed"
  [{:keys [query-key sente-event timeout-ms]
    :or {timeout-ms 10000}}]
  (let [state-path (into [:queries] query-key)
        
        ;; Subscribe to the specific query's state and connection status
        query-state (state/use-sub state-path)
        connected? (state/use-sub [:sente :connected?])
        
        ;; A ref to prevent re-fetching on every render if already successful
        has-fetched-ref (uix/use-ref false)]
    
    (uix/use-effect
     (fn []
       ;; Only fetch if connected and we haven't fetched yet
       (when (and connected? (not @has-fetched-ref))
         (println "🔄 use-sente-query: Fetching" query-key "via" sente-event)
         
         ;; 1. Set the state to loading
         (state/dispatch [:query/fetch-start {:query-key query-key}])
         (reset! has-fetched-ref true)
         
         ;; 2. Make the Sente request
         (sente/request! sente-event timeout-ms
           (fn [reply]
             (println "📡 use-sente-query: Got reply for" query-key ":" reply)
             ;; 3. Dispatch success or error based on the reply
             (if (:success reply)
               (state/dispatch [:query/fetch-success {:query-key query-key :data (:data reply)}])
               (state/dispatch [:query/fetch-error {:query-key query-key 
                                                    :error (or (:error reply) 
                                                              (when (= reply :chsk/closed) "Connection closed")
                                                              "Request failed")}])))))
       ;; Cleanup function
       (constantly nil))
     [connected?]) ;; Only re-run if connection status changes
    
    ;; Return the familiar data structure
    (let [default-state {:data nil :loading? false :error nil}
          current-state (or query-state default-state)]
      {:data (:data current-state)
       :loading? (= (:status current-state) :loading)
       :error (:error current-state)})))

