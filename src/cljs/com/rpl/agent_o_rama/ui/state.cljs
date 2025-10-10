(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.schemas :as schemas]
   [schema.core :as s-core :include-macros true]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

;; =============================================================================
;; FORM VALIDATORS (defined here to avoid circular dependencies)
;; =============================================================================

(def required
  "Validator for required fields"
  (fn [value]
    (when (str/blank? value)
      "This field is required")))

(def valid-json
  "Validator for JSON strings"
  (fn [value]
    (when-not (str/blank? value)
      (try
        (js/JSON.parse value)
        nil ; Valid JSON
        (catch js/Error e
          (str "Invalid JSON: " (.-message e)))))))

;; schema defined in schemas.cljs
(def initial-db
  {:current-invocation {:invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :invocations-data {}
   :invocations {:all-invokes []
                 :pagination-params nil
                 :has-more? true
                 :loading? false}
   :queries {}
   :route nil
   :forms {}
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :active-tab :info
        :current-route "/"
        :modal {:active nil
                :data {}
                :form {:submitting? false
                       :error nil}}
        :hitl {:responses {}
               :submitting {}}
        :datasets {:selected-examples {}
                   :selected-snapshot-per-dataset {}}
        :rules {:refetch-trigger {}}}
   :sente {:connected? false}})

(defonce app-db (atom initial-db))

;; TODO disable with shado-cljs, for performance
(add-watch app-db :console-logger
           (fn [key atom old-state new-state]
             ;; This runs on EVERY state change
             (aset js/window "db" (clj->js new-state {:keyword-fn (fn [k] (str/replace (name k) "-" "_"))}))))

;; =============================================================================
;; EVENT SYSTEM
;; =============================================================================

;; Registry for event handlers
(defonce event-handlers (atom {}))

(defn reg-event
  "Register an event handler. Handler should return a Specter path (navigator)
   that will be applied to the current app-db via s/multi-transform. Handlers
   may return nil to indicate no state change is needed."
  [event-id handler-fn]
  (if (contains? @event-handlers event-id)
    (println "⚠️ Event handler already registered for event:" event-id)
    (swap! event-handlers assoc event-id handler-fn)))

(defn dispatch
  "Dispatch an event to update app-db. Event is a vector [event-id & args].
   The handler must return a Specter path navigator suitable for s/multi-transform.
   Includes centralized schema validation for development builds."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (if handler
      (try
        (let [current-db @app-db
              specter-path (apply handler current-db event-args)]
          ;; Allow handlers to return nil to indicate no state change is needed
          (when specter-path
            ;; Perform the state transformation
            (let [new-db (s/multi-transform specter-path current-db)]

              ;; <<< START: CENTRALIZED VALIDATION HOOK >>>
              ;; This validation runs only in dev builds (thanks to goog.DEBUG)
              ;; It checks the entire state tree after every single change.
              (when ^boolean js/goog.DEBUG
                (try
                  (s-core/validate schemas/AppDbSchema new-db)
                  (catch :default e
                    (println "🔥🔥 SCHEMA VALIDATION FAILED 🔥🔥")
                    (println "Event that caused failure:" event)
                    (println "Validation error details:" (ex-data e))
                    ;; For aggressive debugging, you can throw the error to halt execution
                    ;; (throw e)
                    )))
              ;; <<< END: CENTRALIZED VALIDATION HOOK >>>

              ;; Atomically update the database
              (reset! app-db new-db))))
        (catch :default e
          (println "💥 Error in event handler" event-id ":" e)
          (throw e)))
      (println "⚠️ No handler registered for event:" event-id))))

;; =============================================================================
;; SUBSCRIPTIONS (REACTIVE STATE ACCESS)
;; =============================================================================

(defn path->specter-path
  "Converts a path vector (which may contain UUID objects) into a Specter path.
   UUIDs are wrapped with s/keypath since Specter can't use them directly as navigators.
   Other values (keywords, strings) are left as-is."
  [path]
  (mapv (fn [segment]
          (if (uuid? segment)
            (s/keypath segment)
            segment))
        path))

(defn use-sub
  "Subscribe to a value at the given path in app-db.
   The path may contain raw UUIDs - they will be converted to Specter navigators internally.
   Component will re-render only when the value at that path changes.

   Example:
     (use-sub [:ui :datasets :selected-examples dataset-id])
   where dataset-id is a raw UUID object."
  [path]
  (let [;; Convert the path once, outside the callback
        ;; This ensures we have a stable specter-path for the dependency array
        specter-path (uix/use-memo
                      (fn [] (path->specter-path path))
                      [path])
        ;; Memoize the extractor function to have stable reference
        extract-value (uix/use-callback
                       (fn [db] (s/select-one specter-path db))
                       [specter-path])
        [value set-value] (uix/use-state (fn [] (extract-value @app-db)))]

    (uix/use-effect
     (fn []
       (let [watch-key (gensym "sub-")]
         (add-watch app-db watch-key
                    (fn [_ _ old-db new-db]
                      (let [old-val (extract-value old-db)
                            new-val (extract-value new-db)]
                        (when (not= old-val new-val)
                          (set-value new-val)))))

         ;; Sync with current state immediately after adding watch
         ;; This handles race conditions where the state changed between initial render and effect
         (let [current-value (extract-value @app-db)]
           (when (not= value current-value)
             (set-value current-value)))

         ;; Cleanup function
         (fn []
           (remove-watch app-db watch-key))))
     [extract-value]) ; Include extract-value as dependency

    value))

;; =============================================================================
;; SELECTORS
;; =============================================================================

(defn get-unfinished-leaves
  "Find all unfinished leaf nodes for a given invoke-id.
   Returns a vector of unique [task-id node-id] pairs that can be used for pagination."
  [db invoke-id]
  (let [nodes-map (s/select-one [:invocations-data invoke-id :graph :nodes] db)]
    (->> (s/select [s/ALL ;; Use ALL to get [key value] pairs
                    (s/selected? s/LAST ;; Check the value (node-data)
                                 (s/must :node-task-id)
                                 (s/pred #(not (:finish-time-millis %))))
                    (s/view (fn [[node-id node-data]] ;; Destructure [key value]
                              [(:node-task-id node-data)
                               (or (:invoke-id node-data) ;; Use invoke-id from data
                                   node-id)]))] ;; Or the map key as fallback
                   (or nodes-map {}))
         ;; Remove duplicates
         distinct
         vec)))

;; =============================================================================
;; CORE EVENT HANDLERS
;; =============================================================================

;; UI Events - Only keep complex or toggle events
(reg-event :ui/toggle-forking-mode
           (fn [db]
             [:ui :forking-mode? (s/terminal not)]))

;; Note: Simple setters should use :db/set-value
;; Examples:
;; (dispatch [:db/set-value [:ui :selected-node-id] node-id])
;; (dispatch [:db/set-value [:ui :current-route] route])
;; (dispatch [:db/set-value [:ui :changed-nodes node-id] changes])
;; (dispatch [:db/set-value [:ui :changed-nodes] {}])

;; Note: Sente connection events should use :db/set-value
;; Examples:
;; (dispatch [:db/set-value [:sente :connection-state] new-state])
;; (dispatch [:db/set-value [:sente :connected?] connected?])

;; Current Invocation Events
(reg-event :invocation/set-current
           (fn [db {:keys [invoke-id module-id agent-name]}]
    ;; Simply set the current invocation context
    ;; Data is stored separately under invocations-data
             [:current-invocation (s/terminal-val {:invoke-id invoke-id
                                                   :module-id module-id
                                                   :agent-name agent-name})]))

(reg-event :invocation/load-graph-success
           (fn [db invoke-id graph-data]
             [:invocations-data invoke-id :graph (s/terminal-val graph-data)]))

(reg-event :invocation/load-summary-success
           (fn [db invoke-id summary-data]
             [:invocations-data invoke-id :summary (s/terminal-val summary-data)]))

(reg-event :invocation/update-node
           (fn [db invoke-id node-id node-data]
             [:invocations-data invoke-id :graph :nodes
              (s/terminal (fn [nodes]
                            (assoc (or nodes {}) node-id node-data)))]))

;; Generic state update events
;; Usage: (dispatch [:db/set-value [:some :path] value])
(reg-event :db/set-value
           (fn [db path value]
             ;; Build a Specter navigator that sets the value at the given path
             ;; Convert any UUIDs in the path to keypath navigators
             (into (path->specter-path path) [(s/terminal-val value)])))

;; Usage: (dispatch [:db/update-value [:some :path] update-fn])
(reg-event :db/update-value
           (fn [db path update-fn]
             (into path [(s/terminal update-fn)])))

;; Usage: (dispatch [:db/set-values [[:path1] v1] [[:path2 :k] v2] ...])
(reg-event :db/set-values
           (fn [db & path-value-pairs]
             (apply s/multi-path
                    (map (fn [[path value]]
                           (into (path->specter-path path) [(s/terminal-val value)]))
                         path-value-pairs))))

;; Specific complex events that do more than just setting a value
(reg-event :invocations/append
           (fn [db invokes]
             [:invocations :all-invokes (s/terminal #(concat % invokes))]))

(reg-event :invocations/set-loading
           (fn [db loading?]
             [:invocations :loading? (s/terminal-val loading?)]))

(reg-event :invocations/set-pagination
           (fn [db {:keys [pagination-params has-more?]}]
             [:invocations (s/terminal #(assoc %
                                               :pagination-params pagination-params
                                               :has-more? has-more?))]))

(reg-event :invocations/reset
           (fn [db]
             [:invocations (s/terminal-val {:all-invokes []
                                            :pagination-params nil
                                            :has-more? true
                                            :loading? false})]))

;; =============================================================================
;; GENERIC QUERY HANDLERS - For useSenteQuery hook
;; =============================================================================

(reg-event :query/fetch-start
           (fn [db {:keys [query-key]}]
             ;; Convert query-key with raw UUIDs to Specter path before navigating
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [current-state]
                                  (let [has-data? (some? (:data current-state))]
                                    (-> current-state
                                        (assoc :error nil
                                               :fetching? true)
                                        (cond-> (not has-data?)
                                          (assoc :status :loading))))))])))

(reg-event :query/fetch-success
           (fn [db {:keys [query-key data]}]
             ;; Store queries in a flat map with the full query-key as the map key
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [_]
                                  {:status :success
                                   :data data
                                   :error nil
                                   :fetching? false}))])))

(reg-event :query/fetch-error
           (fn [db {:keys [query-key error]}]
             ;; Store queries in a flat map with the full query-key as the map key
             [:queries (s/keypath query-key)
              (s/terminal (fn [current-state]
                            (-> current-state
                                (assoc :error error
                                       :fetching? false)
                                (cond-> (nil? (:data current-state))
                                  (assoc :status :error)))))]))

(reg-event :query/invalidate
           (fn [db {:keys [query-key-pattern]}]
             ;; Find all query keys that match the pattern and mark them for refetch
             ;; Supports nested query-key vectors stored under :queries as nested maps
             (let [queries-path [:queries]
                   current-queries (get-in @app-db queries-path {})
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
                               (into (path->specter-path (into queries-path query-key))
                                     [:should-refetch? (s/terminal-val true)]))
                             matching-keys))))))

;; =============================================================================
;; FORM STATE MANAGEMENT EVENTS
;; =============================================================================

;; =============================================================================
;; ROUTING EVENTS
;; =============================================================================

(reg-event :route/navigated
           (fn [db new-match]
             [:route
              (s/terminal-val
               (s/transform
                [:path-params s/MAP-VALS]
                (comp common/coerce-uuid common/url-decode)
                new-match))]))

 ;; =============================================================================
;; DEBUGGING HELPERS
;; =============================================================================

(defn get-db [] @app-db)

(defn reset-db!
  "Reset app-db to initial state. Useful for development."
  []
  (reset! app-db initial-db))

(defn debug-state
  "Print current app-db state to console. Optionally filter by path."
  ([]
   (js/console.log "Current app-db:" (clj->js @app-db)))
  ([specter-path]
   (js/console.log "Value at path" specter-path ":"
                   (clj->js (s/select-one specter-path @app-db)))))

 ;; Dataset selection event handlers
(reg-event :datasets/toggle-selection
           (fn [db {:keys [dataset-id example-id]}]
             (into (path->specter-path [:ui :datasets :selected-examples dataset-id])
                   [(s/terminal #(if (contains? % example-id)
                                   (disj % example-id)
                                   (conj (or % #{}) example-id)))])))

(reg-event :datasets/toggle-all-selection
           (fn [db {:keys [dataset-id example-ids-on-page select-all?]}]
             (into (path->specter-path [:ui :datasets :selected-examples dataset-id])
                   [(s/terminal #(if select-all?
                                   (into (or % #{}) example-ids-on-page)
                                   (apply disj (or % #{}) example-ids-on-page)))])))

(reg-event :datasets/clear-selection
           (fn [db {:keys [dataset-id]}]
             [:ui :datasets :selected-examples (s/terminal #(dissoc % dataset-id))]))

(reg-event :datasets/set-selected-snapshot
           (fn [db {:keys [dataset-id snapshot-name]}]
             ;; Convert path with raw UUID to Specter path
             (into (path->specter-path [:ui :datasets :selected-snapshot-per-dataset dataset-id])
                   [(s/terminal-val snapshot-name)])))
