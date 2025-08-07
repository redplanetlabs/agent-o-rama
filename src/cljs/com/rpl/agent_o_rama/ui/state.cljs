(ns com.rpl.agent-o-rama.ui.state
  (:require
   [com.rpl.specter :as s]
   [uix.core :as uix]))

;; The single source of truth for all application state
(def app-db (atom {:agents []
                   :current-invocation {:graph {} :summary {}}
                   :ui {:selected-node-id nil
                        :forking-mode? false
                        :changed-nodes {}}
                   :loading #{}}))

;; Event handlers registry
(def event-handlers (atom {}))

;; Register an event handler
(defn reg-event [event-key handler-fn]
  (swap! event-handlers assoc event-key handler-fn))

;; Dispatch an event to update the app state
(defn dispatch [event-vector]
  (let [event-key (first event-vector)
        event-data (rest event-vector)
        handler (get @event-handlers event-key)]
    (if handler
      (try
        (let [[path transform-fn] (apply handler @app-db event-data)]
          (swap! app-db s/transform path transform-fn))
        (catch js/Error e
          (js/console.error "Error in event handler" event-key e)))
      (js/console.warn "No handler for event" event-key))))

;; Custom hook to subscribe to app state using Specter paths
(defn use-sub [path]
  (let [state-atom (uix/use-atom app-db)]
    (s/select-one path state-atom)))

;; Helper to set loading state
(defn set-loading! [key loading?]
  (if loading?
    (dispatch [:ui/set-loading key])
    (dispatch [:ui/clear-loading key])))

;; Core UI event handlers
(reg-event :ui/set-loading
  (fn [db loading-key]
    ;; TODO transform can be better, use NONE-ELEM
    [[:loading] (s/transform s/ALL #(conj % loading-key))]))

(reg-event :ui/clear-loading
  (fn [db loading-key]
    [[:loading] (s/transform s/ALL #(disj % loading-key))]))

(reg-event :agents/load-success
  (fn [db agents]
    [[:agents] (s/setval agents)]))

(reg-event :select-node
  (fn [db node-id]
    [[:ui :selected-node-id] (s/setval node-id)]))