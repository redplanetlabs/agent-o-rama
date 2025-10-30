(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]))

(defn has-more-pages?
  "Checks if pagination-params indicates more data is available.
   Handles two backend formats:
   - String: 'next-cursor' or nil
   - Map: {:i0 'cursor'} or {:i0 nil}"
  [pagination-params]
  (boolean
   (cond
     (nil? pagination-params) false
     (string? pagination-params) (seq pagination-params)
     (map? pagination-params) (some some? (vals pagination-params))
     :else false)))

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

(defhook use-paginated-query
  "A hook for paginated Sente queries that supports a 'load more' pattern.

   Options:
   - :query-key - A unique vector key to identify this query's state.
   - :sente-event - The base Sente event vector. Pagination params will be merged into it.
   - :page-size - The number of items to fetch per page.
   - :enabled? - Boolean to control if the query should run.

   Returns a map with:
   - :data - Vector of all items fetched so far.
   - :isLoading - True only during the initial fetch.
   - :isFetchingMore - True during subsequent 'load more' fetches.
   - :hasMore - Boolean indicating if more pages are available.
   - :error - Error message if a fetch fails.
   - :loadMore - A function to call to fetch the next page.
   - :refetch - A function to clear all data and start from page 1."
  [{:keys [query-key sente-event page-size enabled?]
    :or {page-size 20 enabled? true}}]
  (let [state-path (into [:queries] query-key)
        query-state (state/use-sub state-path)
        should-refetch? (:should-refetch? query-state)
        connected? (state/use-sub [:sente :connected?])

        ;; Extract data from app-db state
        data (or (:data query-state) [])
        pagination-params (:pagination-params query-state)
        has-more? (get query-state :has-more? true)
        is-loading? (= (:status query-state) :loading)
        is-fetching-more? (:fetching-more? query-state)
        error (when (= (:status query-state) :error) (:error query-state))

        fetch-page (uix/use-callback
                    (fn [pagination-cursor append?]
                      (when (and enabled? connected?)
                        ;; Set loading state
                        (if append?
                          (state/dispatch [:db/set-value (into state-path [:fetching-more?]) true])
                          (state/dispatch [:db/set-value (into state-path [:status]) :loading]))

                        (let [[event-id event-data] sente-event
                              paginated-event [event-id (assoc event-data
                                                               :pagination pagination-cursor
                                                               :limit page-size)]]
                          (sente/request!
                           paginated-event
                           15000
                           (fn [reply]
                             ;; Clear fetching-more state
                             (state/dispatch [:db/set-value (into state-path [:fetching-more?]) false])

                             (if (:success reply)
                               (let [response-data (:data reply)
                                     new-items (or (:items response-data)
                                                   (:agent-invokes response-data)
                                                   (:datasets response-data)
                                                   [])
                                     new-pagination (:pagination-params response-data)
                                     ;; Check if more pages are available (handles both string and map formats)
                                     new-has-more? (has-more-pages? new-pagination)
                                     current-data (or (get-in @state/app-db (into state-path [:data])) [])]
                                 ;; Update data in app-db
                                 (if append?
                                   (state/dispatch [:db/set-value (into state-path [:data])
                                                    (vec (concat current-data new-items))])
                                   (state/dispatch [:db/set-value (into state-path [:data]) new-items]))
                                 (state/dispatch [:db/set-value (into state-path [:pagination-params]) new-pagination])
                                 (state/dispatch [:db/set-value (into state-path [:has-more?]) new-has-more?])
                                 ;; Set status to success AFTER data is updated
                                 (state/dispatch [:db/set-value (into state-path [:status]) :success]))
                               ;; Handle error
                               (do
                                 (state/dispatch [:db/set-value (into state-path [:status]) :error])
                                 (state/dispatch [:db/set-value (into state-path [:error])
                                                  (or (:error reply) "Failed to fetch data")]))))))))
                    [enabled? connected? sente-event page-size state-path])

        load-more (uix/use-callback
                   (fn []
                     (when (and has-more? (not is-loading?) (not is-fetching-more?))
                       (fetch-page pagination-params true)))
                   [has-more? is-loading? is-fetching-more? pagination-params fetch-page])

        refetch (uix/use-callback
                 (fn []
                   ;; Reset to initial state and fetch
                   (state/dispatch [:db/set-value state-path
                                    {:status :idle
                                     :data []
                                     :pagination-params nil
                                     :has-more? true
                                     :fetching-more? false
                                     :error nil
                                     :should-refetch? false}])
                   (fetch-page nil false))
                 [fetch-page state-path])]

    ;; Effect to reset state when query-key changes
    ;; This prevents stale data from being briefly visible
    (uix/use-effect
     (fn []
       ;; Always reset to initial idle state when query-key changes
       (state/dispatch [:db/set-value state-path
                        {:status :idle
                         :data []
                         :pagination-params nil
                         :has-more? true
                         :fetching-more? false
                         :error nil
                         :should-refetch? false}])
       js/undefined)
     [state-path]) ; Re-run whenever state-path (derived from query-key) changes

    ;; Effect for initial load - watches connection and enabled state
    (uix/use-effect
     (fn []
       ;; Only fetch if connected, enabled, and we don't have data yet
       (when (and connected? enabled? (empty? data))
         (fetch-page nil false))
       js/undefined)
     ;; Re-run when connection status or enabled changes, or when fetch-page changes
     [connected? enabled? data fetch-page])

    ;; Effect to watch for invalidation flag and auto-refetch
    (uix/use-effect
     (fn []
       (when (and should-refetch? connected? enabled?)
         ;; Clear the flag first to prevent infinite loops
         (state/dispatch [:db/set-value (into state-path [:should-refetch?]) false])
         ;; Then refetch the data (reset to page 1)
         (refetch)))
     [should-refetch? connected? enabled? refetch state-path])

    {:data data
     :isLoading is-loading?
     :isFetchingMore is-fetching-more?
     :hasMore has-more?
     :error error
     :loadMore load-more
     :refetch refetch}))
