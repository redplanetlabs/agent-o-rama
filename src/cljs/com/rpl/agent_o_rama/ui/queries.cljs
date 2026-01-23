(ns com.rpl.agent-o-rama.ui.queries
  (:require [uix.core :as uix :refer [defhook]]
            [com.rpl.agent-o-rama.ui.state :as state]
            [com.rpl.agent-o-rama.ui.sente :as sente]
            [com.rpl.agent-o-rama.ui.common :as common]
            [com.rpl.specter :as s]))

(defn has-more-pages?
  "Checks if pagination-params indicates more data is available.
   Handles backend formats:
   - String: 'next-cursor' or nil
   - Map: {:i0 'cursor'} or {:i0 nil}
   - UUID: A UUID cursor object"
  [pagination-params]
  (boolean
   (cond
     (nil? pagination-params) false
     (string? pagination-params) (seq pagination-params)
     (map? pagination-params) (some some? (vals pagination-params))
     (uuid? pagination-params) true
     :else false)))

;; =============================================================================
;; QUERY STATE MACHINE
;; =============================================================================

(def default-query-state
  {:status :idle
   :fetching? false
   :pending? false})

(defn query-state
  [ctx]
  (or (get-in @state/app-db (:state-path ctx)) {}))

(defn ready-now?
  [ctx]
  (and (:enabled? ctx) (:connected? ctx) (:page-visible? ctx)))

(defn can-start?
  [ctx st]
  (and (ready-now? ctx) (not (:fetching? st))))

(defn set-pending!
  [ctx st value]
  (state/dispatch
   [:db/set-value (:state-path ctx)
    (assoc (merge default-query-state st) :pending? value)]))

(defn cancel-poll!
  [ctx]
  (when-let [timeout-id @(-> ctx :timer-ref)]
    (js/clearTimeout timeout-id)
    (reset! (:timer-ref ctx) nil)))

(defn schedule-poll!
  [ctx]
  (cancel-poll! ctx)
  (let [{:keys [refetch-interval-ms]} ctx]
    (when (and (ready-now? ctx) refetch-interval-ms)
      (reset! (:timer-ref ctx)
              (js/setTimeout
               (fn []
                 (when-let [send! @(:send-event-ref ctx)]
                   (send! {:type :poll-tick})))
               refetch-interval-ms)))))

(defn start-request!
  [ctx st]
  (let [{:keys [query-key sente-event timeout-ms]} ctx]
    (cancel-poll! ctx)
    (set-pending! ctx st false)
    (state/dispatch [:query/fetch-start {:query-key query-key}])
    (sente/request!
     sente-event
     timeout-ms
     (fn [reply]
       (when-let [send! @(:send-event-ref ctx)]
         (if (:success reply)
           (send! {:type :response-success :data (:data reply)})
           (send! {:type :response-error
                   :error (or (:error reply)
                              (when (= reply :chsk/closed) "Connection closed")
                              "Request failed")})))))))

(defn trigger-handler
  [ctx st _]
  (if (can-start? ctx st)
    (start-request! ctx st)
    (set-pending! ctx st true)))

(defn mount-handler
  [ctx st _]
  (when (:refetch-on-mount? ctx)
    (trigger-handler ctx st nil)))

(defonce query-machine (atom {}))

(s/setval [s/ATOM :trigger-events]
          #{:mount :manual-refetch :invalidate :poll-tick}
          query-machine)

(s/setval [s/ATOM
           :states
           (s/multi-path :idle :loading :success :error)
           :mount]
          mount-handler
          query-machine)

(s/setval [s/ATOM
           :states
           (s/multi-path :idle :loading :success :error)
           (s/multi-path :manual-refetch :invalidate :poll-tick)]
          trigger-handler
          query-machine)

(s/setval [s/ATOM :any :resume]
          (fn [ctx st _]
            (when (and (:pending? st) (can-start? ctx st))
              (start-request! ctx st))
            (when (and (not (:fetching? st)) (not (:pending? st)))
              (schedule-poll! ctx)))
          query-machine)

(s/setval [s/ATOM :any :pause]
          (fn [ctx _ _]
            (cancel-poll! ctx))
          query-machine)
(s/setval [s/ATOM :any :response-success]
          (fn [ctx _ ev]
            (state/dispatch [:query/fetch-success {:query-key (:query-key ctx) :data (:data ev)}])
            (let [next-state (query-state ctx)]
              (if (and (:pending? next-state) (can-start? ctx next-state))
                (start-request! ctx next-state)
                (schedule-poll! ctx))))
          query-machine)
(s/setval [s/ATOM :any :response-error]
          (fn [ctx _ ev]
            (state/dispatch [:query/fetch-error {:query-key (:query-key ctx) :error (:error ev)}])
            (let [next-state (query-state ctx)]
              (if (and (:pending? next-state) (can-start? ctx next-state))
                (start-request! ctx next-state)
                (schedule-poll! ctx))))
          query-machine)

;; =============================================================================
;; QUERY EVENT HANDLERS
;; =============================================================================

(state/reg-event :query/fetch-start
                 (fn [db {:keys [query-key]}]
                   ;; Convert query-key with raw UUIDs to Specter path before navigating
                   (into (state/path->specter-path (into [:queries] query-key))
                         [(s/terminal (fn [current-state]
                                        (let [has-data? (some? (:data current-state))]
                                          (-> current-state
                                              (assoc :error nil
                                                     :fetching? true
                                                     :pending? false)
                                              (cond-> (not has-data?)
                                                (assoc :status :loading))))))])))

(state/reg-event :query/fetch-success
                 (fn [db {:keys [query-key data]}]
                   ;; Store queries in a flat map with the full query-key as the map key
                   (into (state/path->specter-path (into [:queries] query-key))
                         [(s/terminal (fn [current-state]
                                        (-> current-state
                                            (assoc :status :success
                                                   :data data
                                                   :error nil
                                                   :fetching? false))))])))

(state/reg-event :query/fetch-error
                 (fn [db {:keys [query-key error]}]
                   ;; Convert query-key with raw UUIDs to Specter path before navigating
                   (into (state/path->specter-path (into [:queries] query-key))
                         [(s/terminal (fn [current-state]
                                        (-> current-state
                                            (assoc :error error
                                                   :fetching? false)
                                            (cond-> (nil? (:data current-state))
                                              (assoc :status :error)))))])))

(state/reg-event :query/invalidate
                 (fn [db {:keys [query-key-pattern]}]
                   ;; Find all query keys that match the pattern and mark them for refetch
                   ;; Supports nested query-key vectors stored under :queries as nested maps
                   (let [queries-path [:queries]
                         current-queries (get-in @state/app-db queries-path {})
                         ;; Collect all full query-key vectors under :queries (leaf maps contain :status)
                         all-query-keys (letfn [(collect-keys [m prefix acc]
                                                  (reduce-kv
                                                   (fn [a k v]
                                                     (let [new-prefix (conj prefix k)]
                                                       (cond
                                                         (and (map? v) (contains? v :status))
                                                         (conj a (vec new-prefix))

                                                         (map? v)
                                                         (collect-keys v new-prefix a)
                                                         :else a)))
                                                   acc
                                                   m))]
                                          (collect-keys current-queries [] []))
                         matching-keys (filter
                                        (fn [query-key]
                                          (cond
                                            ;; Case 1: Pattern is a keyword: match first segment
                                            (keyword? query-key-pattern)
                                            (= (first query-key) query-key-pattern)

                                            ;; Case 2: Pattern is a vector: prefix match
                                            (vector? query-key-pattern)
                                            (and (>= (count query-key) (count query-key-pattern))
                                                 (= query-key-pattern (subvec query-key 0 (count query-key-pattern))))

                                            ;; Case 3: Pattern is a function (for complex logic)
                                            (fn? query-key-pattern)
                                            (query-key-pattern query-key)

                                            :else false))
                                        all-query-keys)]
                     ;; Mark matching queries as stale by setting a flag (:should-refetch?)
                     ;; Convert query-key paths to Specter paths before navigation
                     (when (seq matching-keys)
                       (apply s/multi-path
                              (map (fn [query-key]
                                     (into (state/path->specter-path (into queries-path query-key))
                                           [:should-refetch? (s/terminal-val true)]))
                                   matching-keys))))))

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
        page-is-visible? (common/use-page-visibility)
        ready? (and enabled? connected? page-is-visible?)
        ctx-ref (uix/use-ref nil)
        timer-ref (uix/use-ref nil)
        send-event-ref (uix/use-ref nil)]

    (reset! ctx-ref
            {:query-key query-key
             :state-path state-path
             :sente-event sente-event
             :timeout-ms timeout-ms
             :enabled? enabled?
             :connected? connected?
             :page-visible? page-is-visible?
             :refetch-interval-ms refetch-interval-ms
             :refetch-on-mount? refetch-on-mount
             :timer-ref timer-ref
             :send-event-ref send-event-ref})

    (reset! send-event-ref
            (fn [event]
              (let [ctx @ctx-ref
                    st (query-state ctx)
                    status (or (:status st) :idle)
                    event-type (:type event)
                    machine @query-machine
                    trigger-events (:trigger-events machine)
                    handler (or (get-in machine [:states status event-type])
                                (get-in machine [:any event-type]))]
                (cond
                  (and (contains? trigger-events event-type) (:fetching? st))
                  (set-pending! ctx st true)

                  handler
                  (handler ctx st event)

                  :else nil))))

        ;; Effect for mount (per query-key)
        (uix/use-effect
         (fn []
           (when-let [send! @send-event-ref]
             (send! {:type :mount}))
           js/undefined)
         [state-path])

        ;; Effect to handle ready/pause transitions and polling updates
        (uix/use-effect
         (fn []
           (when-let [send! @send-event-ref]
             (if ready?
               (send! {:type :resume})
               (send! {:type :pause})))
           js/undefined)
         [ready? refetch-interval-ms])

        ;; Effect to watch invalidation flag and trigger refetch
        (uix/use-effect
         (fn []
           (when should-refetch?
             ;; Clear the flag first to prevent infinite loops
             (state/dispatch [:db/set-value (into state-path [:should-refetch?]) false])
             (when-let [send! @send-event-ref]
               (send! {:type :invalidate})))
           js/undefined)
         [should-refetch? state-path])

        ;; Cleanup on unmount
        (uix/use-effect
         (fn []
           (fn []
             (cancel-poll!)))
         [])

        ;; Return the result map including the refetch function
        (let [current-state (merge default-query-state query-state)
              data (:data current-state)
              loading? (= (:status current-state) :loading)
              error (when (= (:status current-state) :error) (:error current-state))
              fetching? (:fetching? current-state)
              refetch (uix/use-callback
                       (fn []
                         (when-let [send! @send-event-ref]
                           (send! {:type :manual-refetch})))
                       [])]
          {:data data
           :loading? loading?
           :fetching? fetching?
           :error error
           :refetch refetch})))))

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
