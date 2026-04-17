(ns com.rpl.agent-o-rama.ui.invocations.detail
  (:require
   [re-frame.core :as rf]
   [uix.re-frame :refer [use-subscribe]]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.events] ;; Load event handlers
   [com.rpl.agent-o-rama.ui.invocations.graph-view :as view]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.specter :as s]
   [reitit.frontend.easy :as rfe]))

(defui invocation-page []
  (let [{:keys [module-id agent-name invoke-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        query-params (use-subscribe [::aor-rf/get-in [:route :query-params]])
        node-query-param (:node query-params)

        invocation-state (use-subscribe [::aor-rf/get-in [:invocations-data invoke-id]])

        {:keys [status graph summary is-complete implicit-edges
                root-invoke-id task-id forks fork-of error]}
        (or invocation-state {:status :loading})

        ;; Extract nested data
        nodes (:nodes graph)
        real-edges (:edges graph)
        summary-data summary

        ;; UI state subscriptions
        selected-node-id (use-subscribe [::aor-rf/get-in [:ui :selected-node-id]])
        forking-mode? (use-subscribe [::aor-rf/get-in [:ui :forking-mode?]])
        changed-nodes (use-subscribe [::aor-rf/get-in [:ui :changed-nodes]])

        ;; Transform nodes to graph-data format
        graph-data (when nodes
                     (into {}
                           (for [[node-id node-data] nodes]
                             [node-id node-data])))

        ;; 2. The single useEffect to initiate data loading
        _ (uix/use-effect
           (fn []
             (when (and invoke-id module-id agent-name)
               (rf/dispatch [:invocation/start-graph-loading
                                {:invoke-id invoke-id
                                 :module-id module-id
                                 :agent-name agent-name}]))
             ;; Cleanup function
             (fn []
               (rf/dispatch [:invocation/cleanup {:invoke-id invoke-id}])))
           [invoke-id module-id agent-name])

        ;; Auto-select node when graph data loads
        ;; Priority: 1) node from query param, 2) root node, 3) any first node
        _ (uix/use-effect
           (fn []
             (when (and graph-data (not selected-node-id))
               (let [;; Parse node from query param: format is "task-id-node-invoke-id"
                     ;; We need to extract the node-invoke-id (UUID) part
                     node-uuid (when node-query-param
                                 (let [parts (clojure.string/split node-query-param #"-" 2)]
                                   (when (= 2 (count parts))
                                     (try
                                       (uuid (second parts))
                                       (catch js/Error e
                                         nil)))))
                     ;; Check if the parsed node UUID exists in the graph
                     node-from-query (when (and node-uuid (get graph-data node-uuid))
                                       node-uuid)
                     ;; Fallback to root node
                     node-to-select (or node-from-query root-invoke-id)]
                 (when node-to-select
                   (rf/dispatch [:db/set-value [:ui :selected-node-id] node-to-select])))))
           [graph-data root-invoke-id selected-node-id node-query-param])

        ;; 3. Polling effect removed in favor of unified streaming loop in events

        ;; 4. Define callback functions that dispatch events
        handle-select-node (fn [node-id]
                             (rf/dispatch [:db/set-value [:ui :selected-node-id] node-id]))

        handle-execute-fork (fn []
                              (when (not (empty? changed-nodes))
                                (-> (rpc/call ::rpc-invocations/execute-fork!!
                                              {:module-id module-id
                                               :agent-name agent-name
                                               :invoke-id invoke-id
                                               :changed-nodes changed-nodes})
                                    (.then (fn [data]
                                             (let [{:keys [task-id agent-invoke-id]} data]
                                               (rf/dispatch [:ui/clear-fork-state])
                                               (rfe/push-state :agent/invocation-detail
                                                               {:module-id module-id :agent-name agent-name
                                                                :invoke-id (str task-id "-" agent-invoke-id)}))))
                                    (.catch (fn [err]
                                              (js/console.error "Fork failed:" (if (map? err) (or (:error err) (str err)) (str err))))))))

        handle-clear-fork (fn []
                            (rf/dispatch [:ui/clear-fork-state]))

        handle-change-node-input (fn [node-id new-input]
                                   (rf/dispatch [:db/update-value [:ui :changed-nodes] #(assoc % node-id new-input)]))

        handle-remove-node-change (fn [node-id]
                                    (rf/dispatch [:db/update-value [:ui :changed-nodes] #(dissoc % node-id)]))

        handle-toggle-forking-mode (fn []
                                     (rf/dispatch [:ui/toggle-forking-mode]))

        handle-paginate-node (fn [missing-node-id] :todo)

        ;; Prepare the data for the view
        view-props {:module-id module-id
                    :agent-name agent-name
                    :invoke-id invoke-id
                    :task-id task-id
                    :forks forks
                    :fork-of fork-of
                    :graph-data graph-data
                    :real-edges (or real-edges []) ; NEW: Pass pre-processed real edges
                    :summary-data summary-data
                    :implicit-edges (or implicit-edges [])
                    :is-complete is-complete
                    :is-live (not is-complete)
                    :connected? true
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

    ;; 5. Render based on explicit status and connection state
    (cond
      ;; Explicit loading state
      (= status :loading)
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Loading invocation data..."))

      ;; Explicit error state
      (= status :error)
      ($ :div.flex.items-center.justify-center.p-8
         (.log js/console "error" error)
         ($ :div.text-red-500 "Failed to load invocation: " (or (when error (str error)) "Unknown error - module or agent may not be loaded")))

      ;; Success state but no graph data yet (still loading graph)
      (and (= status :success) (not graph-data))
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Loading graph data..."))

      :else
      ($ view/graph-view view-props))))
