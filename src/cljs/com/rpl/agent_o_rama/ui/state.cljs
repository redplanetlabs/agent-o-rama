(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

(def initial-db
  {:current-invocation {:invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :invocations-data {} ;; Keyed by invoke-id -> {:graph {:raw-nodes {} :nodes {} :edges []} :implicit-edges [] :summary ... :root-invoke-id ... :task-id ... :is-complete false}
   :invocations {:all-invokes []
                 :pagination-params nil ;; Next pagination params from server
                 :has-more? true
                 :loading? false}
   :queries {} ; New map to store all query states
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :active-tab :info
        :current-route "/"
        :breadcrumbs []
        :modal {:active nil ;; nil or modal type keyword
                :data {}} ;; modal-specific data
        :hitl {:responses {} ;; Keyed by invoke-id -> response text
               :submitting {}}}
   :sente {:connected? false
           :connection-state {}}
   :session {:user-id nil
             :preferences {}}})

(defonce app-db (atom initial-db))

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
  (swap! event-handlers assoc event-id handler-fn))

(defn dispatch
  "Dispatch an event to update app-db. Event is a vector [event-id & args].
   The handler must return a Specter path navigator suitable for s/multi-transform."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (if handler
      (try
        (let [current-db @app-db
              specter-path (apply handler current-db event-args)]
          ;; Allow handlers to return nil to indicate they handled the update themselves
          (when specter-path
            (swap! app-db #(s/multi-transform specter-path %))))
        (catch :default e
          (println "💥 Error in event handler" event-id ":" e)))
      (println "⚠️ No handler registered for event:" event-id))))

;; =============================================================================
;; SUBSCRIPTIONS (REACTIVE STATE ACCESS)
;; =============================================================================

(defn use-sub
  "Subscribe to a value at the given Specter path in app-db.
   Component will re-render only when the value at that path changes."
  [specter-path]
  (let [;; Memoize the extractor function to have stable reference
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
             (into path [(s/terminal-val value)])))

;; Usage: (dispatch [:db/update-value [:some :path] update-fn])
(reg-event :db/update-value
           (fn [db path update-fn]
             (into path [(s/terminal update-fn)])))

;; Usage: (dispatch [:db/set-values [[:path1] v1] [[:path2 :k] v2] ...])
(reg-event :db/set-values
           (fn [db & path-value-pairs]
             (apply s/multi-path
                    (map (fn [[path value]]
                           (into path [(s/terminal-val value)]))
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
             (into (into [:queries] query-key)
                   [(s/terminal (fn [current-state]
                                  (let [has-data? (some? (:data current-state))]
                                    (-> current-state
                                        (assoc :error nil
                                               :fetching? true)
                                        (cond-> (not has-data?)
                                          (assoc :status :loading))))))])))

(reg-event :query/fetch-success
           (fn [db {:keys [query-key data]}]
             (into (into [:queries] query-key)
                   [(s/terminal (fn [_]
                                  {:status :success
                                   :data data
                                   :error nil
                                   :fetching? false}))])))

(reg-event :query/fetch-error
           (fn [db {:keys [query-key error]}]
             (into (into [:queries] query-key)
                   [(s/terminal (fn [current-state]
                                  (-> current-state
                                      (assoc :error error
                                             :fetching? false)
                                      (cond-> (nil? (:data current-state))
                                        (assoc :status :error)))))])))

 ;; =============================================================================
;; MODAL EVENTS
;; =============================================================================

(reg-event :modal/show
           (fn [db modal-type modal-data]
             [:ui :modal (s/terminal-val {:active modal-type
                                          :data modal-data})]))

(reg-event :modal/hide
           (fn [db]
             [:ui :modal (s/terminal-val {:active nil
                                          :data {}})]))

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