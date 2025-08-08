(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]))

;; =============================================================================
;; APP-DB: The Single Source of Truth
;; =============================================================================

(def initial-db
  {:current-invocation {:graph {}
                        :summary {}
                        :invoke-id nil
                        :module-id nil
                        :agent-name nil}
   :agents {:agents []
            :loading? false
            :error nil}
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
          (if (vector? result)
            (let [[specter-path transform-fn] result]
              (if (and specter-path transform-fn)
                (swap! app-db (fn [db] (s/transform specter-path transform-fn db)))
                (println "⚠️ Event handler" event-id "returned invalid [path transform-fn] tuple:" result)))
            (println "❌ Event handler" event-id "must return a vector [path transform-fn], got:" result)))
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
;; CORE EVENT HANDLERS
;; =============================================================================

;; UI Events
(reg-event :ui/select-node
  (fn [db node-id]
    [[:ui :selected-node-id] (constantly node-id)]))

(reg-event :ui/toggle-forking-mode
  (fn [db]
    [[:ui :forking-mode?] not]))

(reg-event :ui/set-changed-node
  (fn [db node-id changes]
    [[:ui :changed-nodes node-id] (constantly changes)]))

(reg-event :ui/clear-changed-nodes
  (fn [db]
    [[:ui :changed-nodes] (constantly {})]))

(reg-event :ui/toggle-sidebar
  (fn [db]
    [[:ui :sidebar-collapsed?] not]))

(reg-event :ui/set-route
  (fn [db route]
    [[:ui :current-route] (constantly route)]))

;; Sente Connection Events
(reg-event :sente/connection-state-changed
  (fn [db new-state]
    [[:sente :connection-state] (constantly new-state)]))

(reg-event :sente/set-connected
  (fn [db connected?]
    [[:sente :connected?] (constantly connected?)]))

;; Current Invocation Events
(reg-event :invocation/set-current
  (fn [db {:keys [invoke-id module-id agent-name]}]
    [[:current-invocation] 
     (constantly {:invoke-id invoke-id
                  :module-id module-id
                  :agent-name agent-name
                  :graph {}
                  :summary {}})]))

(reg-event :invocation/load-graph-success
  (fn [db graph-data]
    [[:current-invocation :graph] (constantly graph-data)]))

(reg-event :invocation/load-summary-success
  (fn [db summary-data]
    [[:current-invocation :summary] (constantly summary-data)]))

(reg-event :invocation/update-node
  (fn [db node-id node-data]
    [[:current-invocation :graph :nodes node-id] (constantly node-data)]))

;; Agent Events
(reg-event :agents/set-loading
  (fn [db loading?]
    [[:agents :loading?] (constantly loading?)]))

(reg-event :agents/load-success
  (fn [db agents-data]
    [[:agents] (constantly {:agents agents-data
                            :loading? false
                            :error nil})]))

(reg-event :agents/load-error
  (fn [db error]
    [[:agents] (constantly {:agents []
                            :loading? false
                            :error error})]))

;; Agent Loading Effect
(reg-event :agents/load
  (fn [db]
    ;; This event triggers the async loading
    ;; We set loading state immediately, then trigger the request
    ;; Note: The actual request will be triggered from the component
    [[:agents :loading?] (constantly true)]))

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

;; Development helpers - expose globally for REPL access
(when ^boolean goog.DEBUG
  (set! js/window.appDb app-db)
  (set! js/window.dispatch dispatch)
  (set! js/window.debugState debug-state)
  (set! js/window.resetDb reset-db!))
