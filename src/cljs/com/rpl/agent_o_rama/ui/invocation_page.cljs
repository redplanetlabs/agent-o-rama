(ns com.rpl.agent-o-rama.ui.invocation-page
  (:require
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.events] ;; Load event handlers
   [com.rpl.agent-o-rama.ui.invocation-graph-view :as view]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.specter :as s]
   [reitit.frontend.easy :as rfe]))

(defui invocation-page []
  (let [{:keys [module-id agent-name invoke-id]} (state/use-sub [:route :path-params])

        invocation-state (state/use-sub [:invocations-data invoke-id])

        {:keys [status graph summary next-leaves is-complete implicit-edges
                root-invoke-id task-id forks fork-of error]}
        (or invocation-state {:status :loading})

        ;; Extract nested data
        nodes        (:nodes graph)
        real-edges   (:edges graph)
        summary-data summary

        ;; UI state subscriptions
        selected-node-id (state/use-sub [:ui :selected-node-id])
        forking-mode?    (state/use-sub [:ui :forking-mode?])
        changed-nodes    (state/use-sub [:ui :changed-nodes])

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
                                {:invoke-id  invoke-id
                                 :module-id  module-id
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
                                 [:invocations/execute-fork
                                  {:module-id     module-id
                                   :agent-name    agent-name
                                   :invoke-id     invoke-id
                                   :changed-nodes changed-nodes}]
                                 5000
                                 (fn [reply]
                                   (if (:success reply)
                                     (let [{:keys [task-id agent-invoke-id]} (:data reply)
                                           new-path                          (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name)
                                                                                  "/invocations/" task-id "-" agent-invoke-id)]
                                       (state/dispatch [:ui/clear-fork-state])
                                       (rfe/push-state :agent/invocation-detail {:module-id module-id :agent-name agent-name :invoke-id (str task-id "-" agent-invoke-id)}))
                                     (js/console.error "Fork failed:" (:error reply)))))))

        handle-clear-fork (fn []
                            (state/dispatch [:ui/clear-fork-state]))

        handle-change-node-input (fn [node-id new-input]
                                   (state/dispatch [:db/update-value [:ui :changed-nodes] #(assoc % node-id new-input)]))

        handle-remove-node-change (fn [node-id]
                                    (state/dispatch [:db/update-value [:ui :changed-nodes] #(dissoc % node-id)]))

        handle-toggle-forking-mode (fn []
                                     (state/dispatch [:ui/toggle-forking-mode]))

        handle-paginate-node (fn [missing-node-id] :todo)

        ;; Prepare the data for the view
        view-props {:module-id              module-id
                    :agent-name             agent-name
                    :invoke-id              invoke-id
                    :task-id                task-id
                    :forks                  forks
                    :fork-of                fork-of
                    :graph-data             graph-data
                    :real-edges             (or real-edges []) ; NEW: Pass pre-processed real edges
                    :summary-data           summary-data
                    :implicit-edges         (or implicit-edges [])
                    :is-complete            is-complete
                    :is-live                (not is-complete)
                    :connected?             connected?
                    :selected-node-id       selected-node-id
                    :forking-mode?          forking-mode?
                    :changed-nodes          changed-nodes
                    :on-select-node         handle-select-node
                    :on-execute-fork        handle-execute-fork
                    :on-clear-fork          handle-clear-fork
                    :on-change-node-input   handle-change-node-input
                    :on-remove-node-change  handle-remove-node-change
                    :on-toggle-forking-mode handle-toggle-forking-mode
                    :on-paginate-node       handle-paginate-node}]

    ;; 5. Render based on explicit status and connection state
    (cond
      (not connected?)
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Connecting to server..."))

      ;; Explicit loading state
      (= status :loading)
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Loading invocation data..."))

      ;; Explicit error state
      (= status :error)
      ($ :div.flex.items-center.justify-center.p-8
         (.log js/console "error" error)
         ($ :div.text-red-500 "Failed to load invocation: " (str error)))

      ;; Success state but no graph data yet (still loading graph)
      (and (= status :success) (not graph-data))
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Loading graph data..."))

      :else
      ($ view/graph-view view-props))))
