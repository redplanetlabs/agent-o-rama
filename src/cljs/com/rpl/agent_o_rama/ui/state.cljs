(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.schemas :as schemas]
   [re-frame.core :as rf]
   [schema.core :as s-core :include-macros true]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

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

(defn app-db->window-db
  "Convert app-db into a JS-friendly structure for ad hoc browser debugging."
  [db]
  (clj->js db {:keyword-fn (fn [k] (str/replace (name k) "-" "_"))}))

(defn expose-db!
  "Expose the current app-db as window.db when debugging manually."
  []
  (aset js/window "db" (app-db->window-db @app-db)))

(defn clear-exposed-db!
  "Remove any manually exposed window.db value."
  []
  (js-delete js/window "db"))

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
   If no custom handler is registered, falls through to rf/dispatch for
   re-frame events (e.g. modal/show-form, modal/hide, forms/*).
   The handler must return a Specter path navigator suitable for s/multi-transform.
   Includes centralized schema validation for development builds."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (when-not handler
      ;; Fall through to re-frame for events not handled by the custom system
      (rf/dispatch event))
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
             (into (path->specter-path path) [(s/terminal update-fn)])))

;; Usage: (dispatch [:db/set-values [[:path1] v1] [[:path2 :k] v2] ...])
(reg-event :db/set-values
           (fn [db & path-value-pairs]
             (apply s/multi-path
                    (map (fn [[path value]]
                           (into (path->specter-path path) [(s/terminal-val value)]))
                         path-value-pairs))))

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
             ;; Convert query-key with raw UUIDs to Specter path before navigating
             (into (path->specter-path (into [:queries] query-key))
                   [(s/terminal (fn [current-state]
                                  (-> current-state
                                      (assoc :error error
                                             :fetching? false)
                                      (cond-> (nil? (:data current-state))
                                        (assoc :status :error)))))])))

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

(reg-event :form/set-rule-scope-type
           (fn [db form-id new-type]
             [:forms form-id :node-name
              (s/terminal-val (if (= new-type :agent)
                                nil ; Agent-level scope is represented by nil
                                ""))])) ; Node-level scope starts empty to trigger validation

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


;; Re-frame bridge: allow rf/dispatch [:query/invalidate ...] to reach the
;; custom state system without going through state/dispatch (avoids re-entry)
(rf/reg-event-fx :query/invalidate-bridge
  (fn [_ [_ invalidation-map]]
    ;; Directly call state/dispatch — no circular risk since we're in an fx handler
    (dispatch [:query/invalidate invalidation-map])
    nil))

;; Re-frame subscription for modal state — uses long name to avoid Closure collision
(rf/reg-sub ::aor-global-modal
  (fn [db _]
    (get-in db [:ui :modal] {:active nil :data {} :form {}})))

;; Also expose forms map via re-frame sub for cross-system visibility
(rf/reg-sub :forms/all
  (fn [db _]
    (:forms db {})))

(defn invalidate!
  "Invalidate both the old query system and rfq for a given query-key-pattern and rfq tags.
  Call this after any mutation that affects queries from both systems."
  [{:keys [query-key-pattern rfq-tags]}]
  (when query-key-pattern
    (dispatch [:query/invalidate {:query-key-pattern query-key-pattern}]))
  (when (seq rfq-tags)
    (rf/dispatch [:re-frame.query/invalidate-tags rfq-tags])))

(reg-event :datasets/clear-selection
           (fn [db {:keys [dataset-id]}]
             [:ui :datasets :selected-examples (s/terminal #(dissoc % dataset-id))]))

(reg-event :datasets/set-selected-snapshot
           (fn [db {:keys [dataset-id snapshot-name]}]
             ;; Convert path with raw UUID to Specter path
             (into (path->specter-path [:ui :datasets :selected-snapshot-per-dataset dataset-id])
                   [(s/terminal-val snapshot-name)])))
