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
;; SHARED STATE MACHINE PRIMITIVES
;; =============================================================================

(defn edge-matches?
  "Check if a state machine edge matches the current state and event."
  [edge ctx st ev]
  (let [status (:status st)
        event-type (:type ev)
        from (:from edge)
        event (:event edge)
        from-match? (cond
                      (nil? from) false
                      (keyword? from) (= from status)
                      (set? from) (contains? from status)
                      (vector? from) (some #(= status %) from)
                      :else false)
        event-match? (cond
                       (nil? event) false
                       (keyword? event) (= event event-type)
                       (set? event) (contains? event event-type)
                       (vector? event) (some #(= event-type %) event)
                       :else false)
        guard (:guard edge)]
    (and from-match?
         event-match?
         (if guard (guard ctx st ev) true))))

(defn send-machine-event!
  "Send an event to a state machine, finding and executing the matching edge action."
  [machine ctx-ref event]
  (when-let [ctx @ctx-ref]
    (let [get-state (:get-state ctx)
          default-state (:default-state ctx)
          st (merge default-state (get-state ctx))
          edge (first (filter #(edge-matches? % ctx st event) (:edges machine)))]
      (when-let [action (:action edge)]
        (action ctx st event)))))

(defn can-fetch?
  "Shared predicate: can we start a fetch right now?"
  [ctx st]
  (and (:enabled? ctx) (:connected? ctx) (not (:fetching? st))))

;; =============================================================================
;; POLLING QUERY STATE MACHINE (use-sente-query)
;; =============================================================================

(def default-query-state
  {:status :idle
   :fetching? false
   :pending? false
   :retry-count 0})

(defn polling-query-state
  [ctx]
  (or (get-in @state/app-db (:state-path ctx)) {}))

(defn ready-now?
  "For polling queries: ready when enabled, connected, AND page visible."
  [ctx]
  (and (:enabled? ctx) (:connected? ctx) (:page-visible? ctx)))

(defn can-start-polling?
  "For polling queries: can start when ready AND not already fetching."
  [ctx st]
  (and (ready-now? ctx) (not (:fetching? st))))

(defn set-pending!
  [ctx st value]
  (state/dispatch
   [:db/set-value (:state-path ctx)
    (assoc (merge default-query-state st) :pending? value)]))

(defn retry-base-ms
  [ctx]
  (or (:retry-base-ms ctx) (:refetch-interval-ms ctx)))

(defn next-poll-delay-ms
  [ctx st]
  (let [base (:refetch-interval-ms ctx)
        retry-base (retry-base-ms ctx)
        retry-factor (or (:retry-factor ctx) 2)
        retry-max-ms (:retry-max-ms ctx)
        retry-count (max 0 (or (:retry-count st) 0))]
    (cond
      (= (:status st) :error)
      (when retry-base
        (let [delay (* retry-base (js/Math.pow retry-factor (max 0 (dec retry-count))))]
          (if retry-max-ms
            (min retry-max-ms delay)
            delay)))

      :else base)))

(defn cancel-poll!
  [ctx]
  (when-let [timeout-id @(-> ctx :timer-ref)]
    (js/clearTimeout timeout-id)
    (reset! (:timer-ref ctx) nil)))

(defn schedule-poll!
  [ctx st]
  (cancel-poll! ctx)
  (when-let [delay-ms (next-poll-delay-ms ctx st)]
    (when (ready-now? ctx)
      (reset! (:timer-ref ctx)
              (js/setTimeout
               (fn []
                 (when-let [send! @(:send-event-ref ctx)]
                   (send! {:type :poll-tick})))
               delay-ms)))))

(defn start-polling-request!
  [ctx st]
  (let [{:keys [query-key sente-event timeout-ms send-event-ref]} ctx]
    (cancel-poll! ctx)
    (set-pending! ctx st false)
    (state/dispatch [:query/fetch-start {:query-key query-key}])
    (sente/request!
     sente-event
     timeout-ms
     (fn [reply]
       (when-let [send! @send-event-ref]
         (if (:success reply)
           (send! {:type :response-success :data (:data reply)})
           (send! {:type :response-error
                   :error (or (:error reply)
                              (when (= reply :chsk/closed) "Connection closed")
                              "Request failed")})))))))

(def polling-query-machine
  (let [states #{:idle :loading :success :error}
        trigger-events #{:manual-refetch :invalidate :poll-tick}]
    {:nodes states
     :edges
     [{:from states
       :event :mount
       :guard (fn [ctx st _]
                (and (:refetch-on-mount? ctx) (can-start-polling? ctx st)))
       :action start-polling-request!}
      {:from states
       :event :mount
       :guard (fn [ctx st _]
                (and (:refetch-on-mount? ctx) (not (can-start-polling? ctx st))))
       :action (fn [ctx st _] (set-pending! ctx st true))}
      {:from states
       :event trigger-events
       :guard (fn [ctx st _] (can-start-polling? ctx st))
       :action start-polling-request!}
      {:from states
       :event trigger-events
       :guard (fn [ctx st _] (not (can-start-polling? ctx st)))
       :action (fn [ctx st _] (set-pending! ctx st true))}
      {:from states
       :event :resume
       :guard (fn [ctx st _] (and (:pending? st) (can-start-polling? ctx st)))
       :action start-polling-request!}
      {:from states
       :event :resume
       :guard (fn [ctx st _] (and (not (:fetching? st)) (not (:pending? st))))
       :action (fn [ctx st _] (schedule-poll! ctx st))}
      {:from states
       :event :pause
       :action (fn [ctx _ _] (cancel-poll! ctx))}
      {:from states
       :event :response-success
       :action (fn [ctx _ ev]
                 (state/dispatch [:query/fetch-success {:query-key (:query-key ctx) :data (:data ev)}])
                 (let [next-state (polling-query-state ctx)]
                   (if (and (:pending? next-state) (can-start-polling? ctx next-state))
                     (start-polling-request! ctx next-state)
                     (schedule-poll! ctx next-state))))}
      {:from states
       :event :response-error
       :action (fn [ctx _ ev]
                 (state/dispatch [:query/fetch-error {:query-key (:query-key ctx) :error (:error ev)}])
                 (let [next-state (polling-query-state ctx)]
                   (if (and (:pending? next-state) (can-start-polling? ctx next-state))
                     (start-polling-request! ctx next-state)
                     (schedule-poll! ctx next-state))))}]}))

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
                                                   :fetching? false
                                                   :retry-count 0))))])))

(state/reg-event :query/fetch-error
                 (fn [db {:keys [query-key error]}]
                   ;; Convert query-key with raw UUIDs to Specter path before navigating
                   (into (state/path->specter-path (into [:queries] query-key))
                         [(s/terminal (fn [current-state]
                                        (-> current-state
                                            (assoc :error error
                                                   :fetching? false
                                                   :retry-count (inc (or (:retry-count current-state) 0))
                                                   :status :error))))])))

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
   - :retry-base-ms - Base delay for exponential retry after errors (in ms).
                      Defaults to :refetch-interval-ms when unset.
   - :retry-max-ms - Optional max delay cap for exponential retry.
   - :retry-factor - Exponential backoff factor (default: 2).
   - :refetch-on-mount - Boolean to control initial fetch (default: true)

   Returns:
   - :data - The fetched data
   - :loading? - Boolean indicating if request is in progress
   - :error - Error message if request failed
   - :refetch - Function to manually trigger a refetch"
  [{:keys [query-key sente-event timeout-ms enabled? refetch-interval-ms refetch-on-mount
           retry-base-ms retry-max-ms retry-factor]
    :or {timeout-ms 10000 enabled? true refetch-on-mount true retry-factor 2}}]
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
             :retry-base-ms retry-base-ms
             :retry-max-ms retry-max-ms
             :retry-factor retry-factor
             :refetch-on-mount? refetch-on-mount
             :timer-ref timer-ref
             :send-event-ref send-event-ref
             :get-state polling-query-state
             :default-state default-query-state})

    (reset! send-event-ref
            (fn [event]
              (send-machine-event! polling-query-machine ctx-ref event)))

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
         (when-let [ctx @ctx-ref]
           (cancel-poll! ctx))))
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
       :refetch refetch})))

;; =============================================================================
;; PAGINATED QUERY STATE MACHINE
;; =============================================================================

(def default-paginated-state
  {:status :idle
   :fetching? false
   :data []
   :error nil
   :retry-count 0
   ;; Bidirectional pagination cursors
   :next-cursor nil      ; cursor for loading more (forward)
   :prev-cursor nil      ; first item ID for loading previous (backward)
   :has-more-next? true  ; more items forward
   :has-more-prev? false ; more items backward (starts false at beginning)
   :fetch-direction nil}) ; :forward or :backward

(defn paginated-query-state
  [ctx]
  (or (get-in @state/app-db (:state-path ctx)) {}))

(defn extract-items-from-response
  "Extract items array from paginated response, handling various backend formats."
  [response-data]
  (or (:items response-data)
      (:agent-invokes response-data)
      (:datasets response-data)
      (:examples response-data)
      []))

(defn build-paginated-event
  "Build the sente event with pagination parameters."
  [ctx cursor direction]
  (let [[event-id event-data] (:sente-event ctx)
        page-size (:page-size ctx)
        reverse? (= direction :backward)]
    [event-id (cond-> (assoc event-data
                             :pagination cursor
                             :limit page-size)
                reverse? (assoc :reverse? true)
                (:include-cursor? ctx) (assoc :include-cursor? true))]))

(defn update-paginated-state-for-success!
  "Update state after a successful paginated fetch."
  [ctx direction new-items new-cursor]
  (let [state-path (:state-path ctx)
        current-data (or (get-in @state/app-db (into state-path [:data])) [])
        merge-fn (or (:merge-fn ctx) (fn [existing new dir]
                                        (if (= dir :backward)
                                          (vec (concat new existing))
                                          (vec (concat existing new)))))
        updated-data (if (empty? current-data)
                       (vec new-items)
                       (merge-fn current-data new-items direction))
        ;; Track first item ID for backwards pagination
        first-item-id (when (seq updated-data)
                        (:id (first updated-data)))]
    ;; Update all state in one batch for consistency
    (state/dispatch [:db/set-value state-path
                     (merge (paginated-query-state ctx)
                            {:status :success
                             :fetching? false
                             :error nil
                             :retry-count 0
                             :data updated-data
                             :fetch-direction nil}
                            (if (= direction :backward)
                              {:prev-cursor first-item-id
                               :has-more-prev? (has-more-pages? new-cursor)}
                              {:next-cursor new-cursor
                               :has-more-next? (has-more-pages? new-cursor)
                               :prev-cursor (or first-item-id (:prev-cursor (paginated-query-state ctx)))}))])))

(defn start-paginated-request!
  "Start a paginated fetch request in the given direction."
  [ctx st direction]
  (let [{:keys [state-path timeout-ms send-event-ref]} ctx
        cursor (if (= direction :backward)
                 (:prev-cursor st)
                 (:next-cursor st))]
    ;; Mark as fetching
    (state/dispatch [:db/set-value state-path
                     (assoc st
                            :fetching? true
                            :error nil
                            :fetch-direction direction)])
    ;; Send request
    (sente/request!
     (build-paginated-event ctx cursor direction)
     (or timeout-ms 15000)
     (fn [reply]
       (when-let [send! @send-event-ref]
         (if (:success reply)
           (send! {:type :response-success
                   :data (:data reply)
                   :direction direction})
           (send! {:type :response-error
                   :error (or (:error reply)
                              (when (= reply :chsk/closed) "Connection closed")
                              "Request failed")
                   :direction direction})))))))

(def paginated-query-machine
  (let [states #{:idle :loading :success :error}]
    {:nodes states
     :edges
     [;; Mount: fetch initial data if empty
      {:from #{:idle}
       :event :mount
       :guard (fn [ctx st _] (and (can-fetch? ctx st) (empty? (:data st))))
       :action (fn [ctx st _]
                 (state/dispatch [:db/set-value (:state-path ctx) (assoc st :status :loading)])
                 (start-paginated-request! ctx (assoc st :status :loading) :forward))}

      ;; Load more (forward)
      {:from #{:success :error}
       :event :load-more
       :guard (fn [ctx st _] (and (can-fetch? ctx st) (:has-more-next? st)))
       :action (fn [ctx st _] (start-paginated-request! ctx st :forward))}

      ;; Load previous (backward)
      {:from #{:success :error}
       :event :load-previous
       :guard (fn [ctx st _] (and (can-fetch? ctx st) (:has-more-prev? st)))
       :action (fn [ctx st _] (start-paginated-request! ctx st :backward))}

      ;; Invalidate: reset and refetch
      {:from states
       :event :invalidate
       :guard (fn [ctx st _] (can-fetch? ctx st))
       :action (fn [ctx _ _]
                 (let [fresh-state (assoc default-paginated-state :status :loading)]
                   (state/dispatch [:db/set-value (:state-path ctx) fresh-state])
                   (start-paginated-request! ctx fresh-state :forward)))}

      ;; Manual refetch: same as invalidate
      {:from states
       :event :manual-refetch
       :guard (fn [ctx st _] (can-fetch? ctx st))
       :action (fn [ctx _ _]
                 (let [fresh-state (assoc default-paginated-state :status :loading)]
                   (state/dispatch [:db/set-value (:state-path ctx) fresh-state])
                   (start-paginated-request! ctx fresh-state :forward)))}

      ;; Response success
      {:from states
       :event :response-success
       :action (fn [ctx _ ev]
                 (let [response-data (:data ev)
                       direction (:direction ev)
                       new-items (extract-items-from-response response-data)
                       new-cursor (:pagination-params response-data)]
                   (update-paginated-state-for-success! ctx direction new-items new-cursor)))}

      ;; Response error
      {:from states
       :event :response-error
       :action (fn [ctx st ev]
                 (state/dispatch [:db/set-value (:state-path ctx)
                                  (-> st
                                      (assoc :status :error
                                             :fetching? false
                                             :error (:error ev)
                                             :fetch-direction nil)
                                      (update :retry-count inc))]))}]}))

(defhook use-paginated-query
  "A hook for paginated Sente queries with bidirectional support.

   Options:
   - :query-key - A unique vector key to identify this query's state.
   - :sente-event - The base Sente event vector. Pagination params will be merged into it.
   - :page-size - The number of items to fetch per page (default: 20).
   - :timeout-ms - Request timeout in milliseconds (default: 15000).
   - :enabled? - Boolean to control if the query should run (default: true).
   - :initial-cursor - Optional starting pagination cursor (e.g., UUID to start from).
   - :include-cursor? - If true, include the cursor item in results (default: false).
   - :merge-fn - Custom function (existing, new, direction) -> merged for deduplication.
   - :has-more-prev? - Initial value for backward pagination availability (default: false).

   Returns a map with:
   - :data - Vector of all items fetched so far.
   - :isLoading - True only during the initial fetch.
   - :isFetching - True during any fetch operation.
   - :hasMoreNext - Boolean indicating if more pages are available forward.
   - :hasMorePrev - Boolean indicating if more pages are available backward.
   - :error - Error message if a fetch fails.
   - :loadMore - A function to fetch the next page (forward).
   - :loadPrevious - A function to fetch the previous page (backward).
   - :refetch - A function to clear all data and start from beginning."
  [{:keys [query-key sente-event page-size timeout-ms enabled?
           initial-cursor include-cursor? merge-fn has-more-prev?]
    :or {page-size 20 timeout-ms 15000 enabled? true has-more-prev? false}}]
  (let [state-path (into [:queries] query-key)
        query-state (state/use-sub state-path)
        should-refetch? (:should-refetch? query-state)
        connected? (state/use-sub [:sente :connected?])
        ctx-ref (uix/use-ref nil)
        send-event-ref (uix/use-ref nil)

        ;; Extract state
        current-state (merge default-paginated-state query-state)
        data (:data current-state)
        is-loading? (= (:status current-state) :loading)
        is-fetching? (:fetching? current-state)
        has-more-next? (:has-more-next? current-state)
        has-more-prev-state? (:has-more-prev? current-state)
        error (when (= (:status current-state) :error) (:error current-state))]

    ;; Update context ref
    (reset! ctx-ref
            {:query-key query-key
             :state-path state-path
             :sente-event sente-event
             :page-size page-size
             :timeout-ms timeout-ms
             :enabled? enabled?
             :connected? connected?
             :initial-cursor initial-cursor
             :include-cursor? include-cursor?
             :merge-fn merge-fn
             :send-event-ref send-event-ref
             :get-state paginated-query-state
             :default-state default-paginated-state})

    ;; Set up event sender
    (reset! send-event-ref
            (fn [event]
              (send-machine-event! paginated-query-machine ctx-ref event)))

    ;; Effect: reset state when query-key changes
    (uix/use-effect
     (fn []
       (state/dispatch [:db/set-value state-path
                        (assoc default-paginated-state
                               :has-more-prev? has-more-prev?
                               :next-cursor initial-cursor)])
       js/undefined)
     [state-path])

    ;; Effect: mount - fetch if needed
    (uix/use-effect
     (fn []
       (when (and connected? enabled?)
         (when-let [send! @send-event-ref]
           (send! {:type :mount})))
       js/undefined)
     [connected? enabled? state-path])

    ;; Effect: watch invalidation flag
    (uix/use-effect
     (fn []
       (when (and should-refetch? connected? enabled?)
         (state/dispatch [:db/set-value (into state-path [:should-refetch?]) false])
         (when-let [send! @send-event-ref]
           (send! {:type :invalidate})))
       js/undefined)
     [should-refetch? connected? enabled? state-path])

    ;; Return API
    (let [load-more (uix/use-callback
                     (fn []
                       (when-let [send! @send-event-ref]
                         (send! {:type :load-more})))
                     [])
          load-previous (uix/use-callback
                         (fn []
                           (when-let [send! @send-event-ref]
                             (send! {:type :load-previous})))
                         [])
          refetch (uix/use-callback
                   (fn []
                     (when-let [send! @send-event-ref]
                       (send! {:type :manual-refetch})))
                   [])]
      {:data data
       :isLoading is-loading?
       :isFetching is-fetching?
       :isFetchingMore (and is-fetching? (not is-loading?)) ; backwards compat
       :hasMore has-more-next?  ; backwards compat alias
       :hasMoreNext has-more-next?
       :hasMorePrev has-more-prev-state?
       :error error
       :loadMore load-more
       :loadPrevious load-previous
       :refetch refetch})))
