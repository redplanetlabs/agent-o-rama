(ns com.rpl.agent-o-rama.ui.invocation-page
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.events] ;; Load event handlers
   [com.rpl.agent-o-rama.ui.invocation-graph-view :as view]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.specter :as s]
   ["wouter" :refer [useParams useLocation]]))

(defui invocation-page []
  (let [{:strs [module-id agent-name invoke-id]} (js->clj (useParams))
        [location set-location] (useLocation)

        ;; 1. Subscribe to all necessary state from app-db
        nodes (state/use-sub [:invocations-data invoke-id :graph :nodes])
        real-edges (state/use-sub [:invocations-data invoke-id :graph :edges])
        summary-data (state/use-sub [:invocations-data invoke-id :summary])
        next-leaves (state/use-sub [:invocations-data invoke-id :next-leaves])
        is-complete (state/use-sub [:invocations-data invoke-id :is-complete])
        implicit-edges (state/use-sub [:invocations-data invoke-id :implicit-edges])
        root-invoke-id (state/use-sub [:invocations-data invoke-id :root-invoke-id])
        task-id (state/use-sub [:invocations-data invoke-id :task-id])

        ;; UI state subscriptions
        selected-node-id (state/use-sub [:ui :selected-node-id])
        forking-mode? (state/use-sub [:ui :forking-mode?])
        changed-nodes (state/use-sub [:ui :changed-nodes])

        ;; Connection state
        connected? (state/use-sub [:sente :connected?])

        ;; Transform nodes to graph-data format
        graph-data (when nodes
                     (into {}
                           (for [[node-id node-data] nodes]
                             [node-id node-data])))

        ;; 2. The single useEffect to initiate data loading
        _ (uix/use-effect
           (fn []
             (when (and invoke-id module-id agent-name connected?)
               (state/dispatch [:invocation/start-graph-loading
                                {:invoke-id invoke-id
                                 :module-id module-id
                                 :agent-name agent-name}]))
             ;; Cleanup function
             (fn []
               (state/dispatch [:invocation/cleanup {:invoke-id invoke-id}])))
           [invoke-id module-id agent-name connected?])

        ;; 3. Polling effect removed in favor of unified streaming loop in events

        ;; 4. Define callback functions that dispatch events
        handle-select-node (fn [node-id]
                             (state/dispatch [:db/set-value [:ui :selected-node-id] node-id]))

        handle-execute-fork (fn []
                              (when (not (empty? changed-nodes))
                                (sente/request!
                                 [:api/execute-fork
                                  {:module-id module-id
                                   :agent-name agent-name
                                   :invoke-id invoke-id
                                   :changed-nodes changed-nodes}]
                                 5000
                                 (fn [reply]
                                   (if (:success reply)
                                     (let [{:keys [task-id agent-invoke-id]} (:data reply)
                                           new-path (str "/agents/" module-id "/" agent-name
                                                         "/invocations/" task-id "-" agent-invoke-id)]
                                       (state/dispatch [:ui/clear-fork-state])
                                       (set-location new-path))
                                     (js/console.error "Fork failed:" (:error reply)))))))

        handle-clear-fork (fn []
                            (state/dispatch [:ui/clear-fork-state]))

        handle-change-node-input (fn [node-id new-input]
                                   (state/dispatch [:db/update-value [:ui :changed-nodes] #(assoc % node-id new-input)]))

        handle-remove-node-change (fn [node-id]
                                    (state/dispatch [:db/update-value [:ui :changed-nodes] #(dissoc % node-id)]))

        handle-toggle-forking-mode (fn []
                                     (state/dispatch [:ui/toggle-forking-mode]))

        handle-paginate-node (fn [missing-node-id]
                               ;; Only allow pagination for complete invocations
                               (when is-complete
                                 (sente/request!
                                  [:api/paginate-node
                                   {:module-id module-id
                                    :agent-name agent-name
                                    :invoke-id invoke-id
                                    :missing-node-id missing-node-id}]
                                  5000
                                  (fn [reply]
                                    (when (:success reply)
                                      ;; Extract nodes from paginated response format
                                      (let [{:keys [invokes-map]} (:data reply)]
                                        (state/dispatch [:invocation/merge-nodes
                                                         invoke-id
                                                         invokes-map])))))))

        ;; Prepare the data for the view
        view-props {:module-id module-id
                    :agent-name agent-name
                    :invoke-id invoke-id
                    :graph-data graph-data
                    :real-edges (or real-edges []) ; NEW: Pass pre-processed real edges
                    :summary-data summary-data
                    :implicit-edges (or implicit-edges [])
                    :is-complete is-complete
                    :is-live (not is-complete)
                    :connected? connected?
                    :selected-node-id selected-node-id
                    :forking-mode? forking-mode?
                    :changed-nodes changed-nodes
                    :on-select-node handle-select-node
                    :on-execute-fork handle-execute-fork
                    :on-clear-fork handle-clear-fork
                    :on-change-node-input handle-change-node-input
                    :on-remove-node-change handle-remove-node-change
                    :on-toggle-forking-mode handle-toggle-forking-mode
                    :on-paginate-node handle-paginate-node}]

    ;; 5. Render based on connection and data state
    (cond
      (not connected?)
      ($ :div.flex.items-center.justify-center.p-8
         ($ :div.text-gray-500 "Connecting to server..."))

      (and (not graph-data) (not is-complete))
      ($ :div.flex.items-center.justify-center.p-8
         ($ :div.text-gray-500 "Loading invocation..."))

      :else
      ($ view/graph-view view-props))))
