(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.common :as common]))

(defhook use-sente-query
  "A hook for making Sente-based queries with automatic connection handling.

   Options:
   - :query-key - Vector path to store the query state (e.g. [:agents])
   - :sente-event - Vector event to send to server (e.g. [:api/get-agents])
   - :timeout-ms - Timeout in milliseconds (default: 10000)
   - :enabled? - Boolean to control if query should run (default: true)
   - :refetch-interval-ms - If set, will refetch data at this interval (in ms)
                            but only when the browser tab is visible.

   Returns:
   - :data - The fetched data
   - :loading? - Boolean indicating if request is in progress
   - :error - Error message if request failed"
  [{:keys [query-key sente-event timeout-ms enabled? refetch-interval-ms]
    :or {timeout-ms 10000 enabled? true}}]
  (let [state-path (into [:queries] query-key)
        query-state (state/use-sub state-path)
        connected? (state/use-sub [:sente :connected?])
        query-key-str (str (vec query-key))
        sente-event-str (str sente-event)

        ;; Use the page visibility hook
        page-is-visible? (common/use-page-visibility)]

    ;; Effect for initial fetch and polling setup
    (uix/use-effect
     (fn []
       (let [fetch-data (fn []
                          (println "🔄 use-sente-query: Fetching" query-key "via" sente-event "connected?" connected? "enabled?" enabled? "visible?" page-is-visible?)
                          (state/dispatch [:query/fetch-start {:query-key query-key}])
                          (sente/request! sente-event timeout-ms
                                          (fn [reply]
                                            (println "📡 use-sente-query: Got reply for" query-key ":" reply)
                                            (if (:success reply)
                                              (state/dispatch [:query/fetch-success {:query-key query-key :data (:data reply)}])
                                              (state/dispatch [:query/fetch-error {:query-key query-key
                                                                                   :error (or (:error reply)
                                                                                              (when (= reply :chsk/closed) "Connection closed")
                                                                                              "Request failed")}])))))
             interval-id (atom nil)]

         ;; Only proceed if all conditions are met
         (when (and connected? enabled? page-is-visible?)
           ;; Initial fetch
           (fetch-data)

           ;; Set up polling if interval is specified
           (when refetch-interval-ms
             (reset! interval-id (js/setInterval fetch-data refetch-interval-ms))))

         ;; Always return a cleanup function
         (fn []
           (when @interval-id
             (println "🧹 Cleaning up interval for" query-key)
             (js/clearInterval @interval-id)
             (reset! interval-id nil)))))

     ;; Dependencies - removed fetch-data to prevent infinite loops
     [connected? query-key-str sente-event-str enabled? page-is-visible? refetch-interval-ms timeout-ms])

    ;; Return the familiar data structure (stale-while-revalidate)
    (let [default-state {:data nil :status nil :error nil :fetching? false}
          current-state (or query-state default-state)
          data (:data current-state)
          loading? (= (:status current-state) :loading)
          error (when (= (:status current-state) :error) (:error current-state))
          fetching? (:fetching? current-state)]
      {:data data
       :loading? loading?
       :fetching? fetching?
       :error error})))

