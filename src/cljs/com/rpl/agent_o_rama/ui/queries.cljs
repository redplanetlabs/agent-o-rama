(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [uix.re-frame :refer [use-subscribe]]
            [re-frame.core :as rf]
            [re-frame.query :as rfq]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]))

(def has-more-pages? common/has-more-pages?)

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

(defhook use-rpc-query
  "HTTP RPC query via `re-frame.query` (same transport as `rfq/reg-query`).

  Options:
  - :rfq-key — Registered query keyword (e.g. `::rpc-datasets/get-all!!`)
  - :params — Argument map for `query-fn` / cache key
  - :enabled? — When false, skips fetch and uses `{:skip? true}` (default true)

  Returns:
  - :data — Response body from the RPC (what the server puts in the success payload)
  - :loading? — True during initial load (`:loading` or `:idle` status)
  - :fetching? — True while a request is in flight (including background refetch)
  - :error — Error value when status is `:error`
  - :refetch — Dispatches `:re-frame.query/refetch-query`"
  [{:keys [rfq-key params enabled?]
    :or {enabled? true}}]
  (let [params-sig (pr-str params)
        query-state (use-subscribe [:re-frame.query/query rfq-key params
                                    {:skip? (not enabled?)}])
        {:keys [status data error fetching?]} query-state
        loading? (#{:loading :idle} status)
        refetch (uix/use-callback
                 (fn []
                   (when enabled?
                     (rf/dispatch [:re-frame.query/refetch-query rfq-key params])))
                 [enabled? rfq-key params-sig])]
    {:data data
     :loading? loading?
     :fetching? (boolean fetching?)
     :error (when (= status :error) error)
     :refetch refetch}))

(defhook use-paginated-query
  "A hook for paginated Sente queries that supports a 'load more' pattern.

   Options:
   - :query-key - A unique vector key to identify this query's state.
   - :sente-event - The base Sente event vector. Pagination params will be merged into it.
   - :page-size - The number of items to fetch per page.
   - :initial-pagination - Optional starting pagination cursor (e.g., UUID to start from).
   - :enabled? - Boolean to control if the query should run.

   Returns a map with:
   - :data - Vector of all items fetched so far.
   - :isLoading - True only during the initial fetch.
   - :isFetchingMore - True during subsequent 'load more' fetches.
   - :hasMore - Boolean indicating if more pages are available.
   - :error - Error message if a fetch fails.
   - :loadMore - A function to call to fetch the next page.
   - :refetch - A function to clear all data and start from page 1."
  [{:keys [query-key sente-event page-size initial-pagination enabled?]
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
                                                   (:examples response-data)
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
         (fetch-page initial-pagination false))
       js/undefined)
     ;; Re-run when connection status or enabled changes, or when fetch-page changes
     [connected? enabled? data fetch-page initial-pagination])

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

(defhook use-infinite-rpc-query
  "Paginated lists backed by `re-frame.query` infinite queries (HTTP RPC).

  Uses the active `:re-frame.query/infinite-query` subscription (not the passive
  `-state` variant) so queries are marked active — required for
  `:re-frame.query/invalidate-tags` to refetch. When `:enabled?` is false, passes
  `{:skip? true}` so no fetch runs and the query is not marked active.

  Options:
  - :rfq-key — Registered query keyword (e.g. `::rpc-datasets/get-all-inf!!`)
  - :params — Base param map (module id, filters, etc.); `:limit` is merged from :page-size
  - :page-size — Passed to the server as `:limit` (default 20)
  - :enabled? — When false, skips subscription side effects (no fetch)
  - :flatten-items-fn — `(fn [pages] -> vector)` to merge page maps into row data

  Returns the same keys as `use-paginated-query` for drop-in replacement."
  [{:keys [rfq-key params page-size enabled? flatten-items-fn]
    :or {page-size 20
         enabled? true
         flatten-items-fn (fn [pages]
                            (vec (apply concat (map :items pages))))}}]
  (let [params' (assoc params :limit page-size)
        params-sig (pr-str params')
        query-state (use-subscribe [:re-frame.query/infinite-query rfq-key params'
                                    {:skip? (not enabled?)}])]
    (let [{:keys [status data error fetching-next?]} query-state
          pages (:pages data)
          items (if (seq pages)
                  (flatten-items-fn pages)
                  [])
          has-next? (boolean (:has-next? data))
          loading? (#{:loading :idle} status)
          load-more (uix/use-callback
                     (fn []
                       (when (and enabled? has-next?)
                         (rfq/fetch-next-page rfq-key params')))
                     [enabled? has-next? rfq-key params-sig])
          refetch (uix/use-callback
                   (fn []
                     (when enabled?
                       (rf/dispatch [:re-frame.query/refetch-infinite-query rfq-key params'])))
                   [enabled? rfq-key params-sig])]
      {:data items
       :isLoading (and loading? (empty? items))
       :isFetchingMore (boolean fetching-next?)
       :hasMore has-next?
       :error (when (= status :error) error)
       :loadMore load-more
       :refetch refetch})))
