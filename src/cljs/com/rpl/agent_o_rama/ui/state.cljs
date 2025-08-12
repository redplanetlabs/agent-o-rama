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
   :invocations-data {} ;; Keyed by invoke-id -> {:graph {:nodes ...} :summary ...}
   :invocations {:all-invokes []
                 :pagination-params nil ;; Next pagination params from server
                 :has-more? true
                 :loading? false}
   :queries {} ; New map to store all query states
   :ui {:selected-node-id nil
        :forking-mode? false
        :changed-nodes {}
        :sidebar-collapsed? false
        :current-route "/"
        :breadcrumbs []}
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
  "Register an event handler. Handler should return [specter-path transform-fn] tuple."
  [event-id handler-fn]
  (swap! event-handlers assoc event-id handler-fn))

(defn dispatch
  "Dispatch an event to update app-db. Event is a vector [event-id & args]."
  [event]
  (let [event-id (first event)
        event-args (rest event)
        handler (get @event-handlers event-id)]
    (if handler
      (try
        (let [current-db @app-db
              result (apply handler current-db event-args)]
          (when result ;; Allow handlers to return nil to indicate they handled the update themselves
            (if (vector? result)
              (let [[specter-path transform-fn] result]
                (if (and specter-path transform-fn)
                  (swap! app-db #(s/transform specter-path transform-fn %))
                  (println "⚠️ Event handler" event-id "returned invalid [path transform-fn] tuple:" result)))
              (println "❌ Event handler" event-id "must return a vector [path transform-fn], got:" result))))
        (catch :default e
          (println "💥 Error in event handler" event-id ":" e)))
      (js/console.warn "No handler registered for event:" event-id))))

;; =============================================================================
;; SUBSCRIPTIONS (REACTIVE STATE ACCESS)
;; =============================================================================

(defn use-sub
  "Subscribe to a value at the given Specter path in app-db.
   Component will re-render only when the value at that path changes."
  [specter-path]
  (let [extract-value (fn [db] (s/select-one specter-path db))
        [value set-value] (uix/use-state (extract-value @app-db))]
    
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
     []) ; Empty deps array - only run once
    
    value))

;; =============================================================================
;; SELECTORS
;; =============================================================================

(defn get-unfinished-leaves 
  "Find all unfinished leaf nodes for a given invoke-id.
   Returns a vector of [task-id node-id] pairs that can be used for pagination."
  [db invoke-id]
  (let [nodes-map (s/select-one [:invocations-data invoke-id :graph :nodes] db)]
    (s/select [s/MAP-VALS
               (s/selected? (s/must :node-task-id))
               (s/view (fn [node-data]
                        [(:node-task-id node-data) (:node-id node-data)]))]
              (or nodes-map {}))))

;; =============================================================================
;; CORE EVENT HANDLERS
;; =============================================================================

;; UI Events - Only keep complex or toggle events
(reg-event :ui/toggle-forking-mode
  (fn [db]
    [[:ui :forking-mode?] not]))

(reg-event :ui/toggle-sidebar
  (fn [db]
    [[:ui :sidebar-collapsed?] not]))

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
    [[:current-invocation]
     (constantly {:invoke-id invoke-id
                  :module-id module-id
                  :agent-name agent-name})]))

(reg-event :invocation/load-graph-success
  (fn [db invoke-id graph-data]
    [[:invocations-data invoke-id :graph] (constantly graph-data)]))

(reg-event :invocation/load-summary-success
  (fn [db invoke-id summary-data]
    [[:invocations-data invoke-id :summary] (constantly summary-data)]))

(reg-event :invocation/update-node
  (fn [db invoke-id node-id node-data]
    [[:invocations-data invoke-id :graph :nodes]
     (fn [nodes]
       (assoc (or nodes {}) node-id node-data))]))

;; Generic state update events
;; Usage: (dispatch [:db/set-value [:some :path] value])
(reg-event :db/set-value
  (fn [db path value]
    ;; Return path and a function that ignores the old value and returns the new one
    [path (fn [_] value)]))

;; Usage: (dispatch [:db/update-value [:some :path] update-fn])
(reg-event :db/update-value
  (fn [db path update-fn]
    [path update-fn]))


;; Specific complex events that do more than just setting a value
(reg-event :invocations/append
  (fn [db invokes]
    [[:invocations :all-invokes] #(concat % invokes)]))

(reg-event :invocations/set-loading
  (fn [db loading?]
    [[:invocations :loading?] (constantly loading?)]))

(reg-event :invocations/set-pagination
  (fn [db {:keys [pagination-params has-more?]}]
    [[:invocations] #(assoc % 
                           :pagination-params pagination-params
                           :has-more? has-more?)]))

(reg-event :invocations/reset
  (fn [db]
    [[:invocations] (constantly {:all-invokes []
                                 :pagination-params nil
                                 :has-more? true
                                 :loading? false})]))

;; =============================================================================
;; GENERIC QUERY HANDLERS - For useSenteQuery hook
;; =============================================================================

(reg-event :query/fetch-start
  (fn [db {:keys [query-key]}]
    [(into [:queries] query-key)
     (fn [current-state]
       (assoc current-state :status :loading :error nil))]))

(reg-event :query/fetch-success
  (fn [db {:keys [query-key data]}]
    [(into [:queries] query-key)
     (constantly {:status :success :data data :error nil})]))

(reg-event :query/fetch-error
  (fn [db {:keys [query-key error]}]
    [(into [:queries] query-key)
     (fn [current-state]
       (assoc current-state :status :error :error error))]))

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