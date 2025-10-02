(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]))

(defn query-key->specter-path
  "Converts a query-key vector (which may contain UUID objects) into a Specter path.
   UUIDs are wrapped with s/keypath since Specter can't use them directly as navigators.
   Other values (keywords, strings) are left as-is.
   
   Example:
     (query-key->specter-path [:dataset-examples \"module-1\" #uuid \"...\" \"snapshot\" \"search\"])
     => [:dataset-examples \"module-1\" (s/keypath #uuid \"...\") \"snapshot\" \"search\"]"
  [query-key]
  (mapv (fn [segment]
          (if (uuid? segment)
            (s/keypath segment)
            segment))
        query-key))

(defhook use-sente-query
  "A hook for making Sente-based queries with automatic connection handling.

   Options:
   - :query-key - Vector path to store the query state (e.g. [:agents])
   - :sente-event - Vector event to send to server (e.g. [:api/get-agents])
   - :timeout-ms - Timeout in milliseconds (default: 10000)
   - :enabled? - Boolean to control if query should run (default: true)
   - :refetch-interval-ms - If set, will refetch data at this interval (in ms)
                            but only when the browser tab is visible.
   - :refetch-on-mount - Boolean to control initial fetch (default: true)

   Returns:
   - :data - The fetched data
   - :loading? - Boolean indicating if request is in progress
   - :error - Error message if request failed
   - :refetch - Function to manually trigger a refetch"
  [{:keys [query-key sente-event timeout-ms enabled? refetch-interval-ms refetch-on-mount]
    :or {timeout-ms 10000 enabled? true refetch-on-mount true}}]
  (let [state-path (into [:queries] query-key)
        query-state (state/use-sub state-path)
        should-refetch? (:should-refetch? query-state)
        connected? (state/use-sub [:sente :connected?])
        query-key-str (str (vec query-key))
        sente-event-str (str sente-event)

        ;; Use the page visibility hook
        page-is-visible? (common/use-page-visibility)

        ;; Define the fetch function inside the hook so it has access to the closure
        fetch-data (uix/use-callback
                    (fn []
                      (state/dispatch [:query/fetch-start {:query-key query-key}])
                      (sente/request! sente-event timeout-ms
                                      (fn [reply]
                                        (if (:success reply)
                                          (state/dispatch [:query/fetch-success {:query-key query-key :data (:data reply)}])
                                          (state/dispatch [:query/fetch-error {:query-key query-key
                                                                               :error (or (:error reply)
                                                                                          (when (= reply :chsk/closed) "Connection closed")
                                                                                          "Request failed")}])))))
                    [sente-event query-key query-key-str sente-event-str timeout-ms])]

    ;; Effect for initial fetch and polling setup
    (uix/use-effect
     (fn []
       (let [interval-id (atom nil)]
         (when (and connected? enabled? page-is-visible?)
           ;; Control initial fetch with new option
           (when refetch-on-mount (fetch-data))

           (when refetch-interval-ms
             (reset! interval-id (js/setInterval fetch-data refetch-interval-ms))))
         (fn []
           (when @interval-id
             (js/clearInterval @interval-id)
             (reset! interval-id nil)))))
     ;; Re-run effect if `fetch-data` identity changes
     [connected? enabled? page-is-visible? refetch-interval-ms fetch-data refetch-on-mount])

    ;; Effect to watch for invalidation flag and auto-refetch
    (uix/use-effect
     (fn []
       (when (and should-refetch? connected? enabled? page-is-visible?)
         ;; Clear the flag first to prevent infinite loops
         (state/dispatch [:db/set-value (into state-path [:should-refetch?]) false])
         ;; Then refetch the data
         (fetch-data)))
     ;; CRITICAL: Only watch the boolean value itself, not state-path or query-state
     ;; state-path is not needed because it's derived from query-key which is in fetch-data deps
     ;; Including state-path or query-state causes infinite loops
     [should-refetch? connected? enabled? page-is-visible? fetch-data])

    ;; Return the result map including the refetch function
    (let [default-state {:data nil :status nil :error nil :fetching? false}
          current-state (or query-state default-state)
          data (:data current-state)
          loading? (= (:status current-state) :loading)
          error (when (= (:status current-state) :error) (:error current-state))
          fetching? (:fetching? current-state)]
      {:data data
       :loading? loading?
       :fetching? fetching?
       :error error
       :refetch fetch-data})))
