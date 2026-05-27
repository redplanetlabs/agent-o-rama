(ns com.rpl.agent-o-rama.ui.invocations.detail
  (:require
   [re-frame.core :as rf]
   [uix.re-frame :refer [use-subscribe]]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [$ defui]]
   [com.rpl.agent-o-rama.ui.events]
   [com.rpl.agent-o-rama.ui.invocations.subs]
   [com.rpl.agent-o-rama.ui.invocations.graph-view :as view]
   [com.rpl.agent-o-rama.ui.invocations.subs :as inv-subs]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]
   [com.rpl.agent-o-rama.ui.common :as common]
   [reitit.frontend.easy :as rfe]))

(defui invocation-page []
  (let [{:keys [module-id agent-name invoke-id]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        query-params (use-subscribe [::aor-rf/get-in [:route :query-params]])
        node-query-param (:node query-params)

        invocation-state (use-subscribe [:invocation/state invoke-id])
        graph-data (use-subscribe [:invocation/graph-data invoke-id])
        selected-node-id (use-subscribe [:invocation/selected-node-id invoke-id])
        changed-nodes (use-subscribe [:invocation/changed-nodes invoke-id])
        root-invoke-id (use-subscribe [:invocation/root-invoke-id invoke-id])

        {:keys [status summary error task-id forks fork-of]}
        (or invocation-state {:status :loading})

        summary-data summary
        graph-ready? (contains? (:graph (or invocation-state {})) :nodes)

        _ (uix/use-effect
           (fn []
             (when (and invoke-id module-id agent-name)
               (rf/dispatch [:invocation/start-graph-loading
                             {:invoke-id invoke-id
                              :module-id module-id
                              :agent-name agent-name}]))
             (fn []
               (rf/dispatch [:invocation/cleanup {:invoke-id invoke-id}])))
           [invoke-id module-id agent-name])

        _ (uix/use-effect
           (fn []
             (when (and graph-data (not selected-node-id))
               (let [node-uuid (when node-query-param
                                 (let [parts (clojure.string/split node-query-param #"-" 2)]
                                   (when (= 2 (count parts))
                                     (try
                                       (uuid (second parts))
                                       (catch js/Error _ nil)))))
                     node-from-query (when (and node-uuid (get graph-data node-uuid))
                                       node-uuid)
                     node-to-select (or node-from-query root-invoke-id)]
                 (when node-to-select
                   (rf/dispatch [:invocation/select-node invoke-id node-to-select])))))
           [graph-data root-invoke-id selected-node-id node-query-param invoke-id])

        handle-select-node (fn [node-id]
                             (rf/dispatch [:invocation/select-node invoke-id node-id]))

        handle-execute-fork (fn []
                              (when (seq changed-nodes)
                                (-> (rpc/call ::rpc-invocations/execute-fork!!
                                              {:module-id module-id
                                               :agent-name agent-name
                                               :invoke-id invoke-id
                                               :changed-nodes changed-nodes})
                                    (.then (fn [data]
                                             (let [{:keys [task-id agent-invoke-id]} data]
                                               (rf/dispatch [:ui/clear-fork-state invoke-id])
                                               (rfe/push-state :agent/invocation-detail
                                                               {:module-id module-id
                                                                :agent-name agent-name
                                                                :invoke-id (str task-id "-" agent-invoke-id)}))))
                                    (.catch (fn [err]
                                              (js/console.error "Fork failed:"
                                                                (if (map? err) (or (:error err) (str err)) (str err))))))))

        handle-clear-fork (fn []
                            (rf/dispatch [:ui/clear-fork-state invoke-id]))

        handle-change-node-input (fn [node-id new-input]
                                   (rf/dispatch [:db/update-value
                                                 (conj (inv-subs/invocation-ui-path invoke-id) :changed-nodes)
                                                 #(assoc % node-id new-input)]))

        handle-remove-node-change (fn [node-id]
                                    (rf/dispatch [:db/update-value
                                                  (conj (inv-subs/invocation-ui-path invoke-id) :changed-nodes)
                                                  #(dissoc % node-id)]))

        handle-toggle-forking-mode (fn []
                                     (rf/dispatch [:ui/toggle-forking-mode invoke-id]))

        handle-paginate-node (fn [_missing-node-id] :todo)]

    (cond
      (= status :loading)
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2 "Loading invocation data..."))

      (= status :error)
      ($ :div.flex.items-center.justify-center.p-8
         ($ :div.text-red-500 "Failed to load invocation: "
            (or (when error (str error)) "Unknown error - module or agent may not be loaded")))

      (and (not graph-ready?) (not= status :error))
      ($ :div.flex.items-center.justify-center.p-8
         ($ common/spinner {:size :medium})
         ($ :div.text-gray-500.ml-2
            (if (= status :loading)
              "Loading invocation data..."
              "Loading graph data...")))

      :else
      ($ view/graph-view {:module-id module-id
                          :agent-name agent-name
                          :invoke-id invoke-id
                          :task-id task-id
                          :forks forks
                          :fork-of fork-of
                          :on-select-node handle-select-node
                          :on-execute-fork handle-execute-fork
                          :on-clear-fork handle-clear-fork
                          :on-change-node-input handle-change-node-input
                          :on-remove-node-change handle-remove-node-change
                          :on-toggle-forking-mode handle-toggle-forking-mode
                          :on-paginate-node handle-paginate-node}))))
