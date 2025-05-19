(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.query :as query]
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [reitit.frontend.easy :as rfe]
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useOnSelectionChange]]
   ["@dagrejs/dagre" :as Dagre]))

;; ========== re-frame subscriptions and events ==========

(re-frame/reg-sub
 ::selected-node
 (fn [db _]
   (::selected-node db)))

(re-frame/reg-event-db
 ::set-selected-node
 (fn [db [_ node-data]]
   (assoc db ::selected-node node-data)))

(re-frame/reg-event-db
 ::clear-selected-node
 (fn [db _]
   (dissoc db ::selected-node)))

(defn graph []
  (let [{:keys [module-id agent-id invoke-id]} @(re-frame/subscribe [:route-params])
        query-key [:invoke module-id agent-id invoke-id]
        invoke-data @(re-frame/subscribe [:query/data query-key])
        data (:invokes-map invoke-data)
        selected-node @(re-frame/subscribe [::selected-node])
        
        g (new (.. Dagre -graphlib -Graph))
    
        nodes (s/select [s/ALL
                         (s/selected? s/LAST (s/must :emits))
                      
                         (s/view (fn [[id data]]
                                   {:id (str id)
                                    :data (assoc data :label (str (:node data)))}))]
                        data)
        implicit->real (into {}
                             (s/select [s/ALL
                                        (s/collect-one s/FIRST)
                                        s/LAST
                                        (s/must :invoked-agg-invoke-id)] data))
        edges (for [[from to]
                    (s/select [s/ALL
                               (s/collect-one s/FIRST)
                               s/LAST
                               (s/must :emits)
                               s/ALL
                               :invoke-id] data)]
             
                {:id (str from to)
                 :source (str from)
                 :target (str (get implicit->real to to))})]
    
    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {})
    
    (doall (for [edge edges] (.setEdge g (:source edge) (:target edge))))
    (doall (for [node nodes]
             (.setNode g (:id node) (clj->js
                                     (merge node {:width 170 :height 40})))))
    
    (Dagre/layout g)
    
    (let [nodes (for [node nodes
                      :let [position (.node g (:id node))]]
                  (assoc node :position position))]
      
      [:<>
       [:div.rounded-lg.overflow-hidden
        [:h2.text-2xl.font-semibold.mb-4.text-gray-700 "Agent Invocation Graph"]
        [:div {:style {:width "100%" :height "500px"}}
         [:> ReactFlow {:nodes nodes :edges edges
                        :proOptions {:hideAttribution true}
                        :nodeTypes {:custom (fn [props]
                                              [:div.bg-indigo-500.text-white.p-3.rounded-md.shadow-lg {:style {:width "170px" :height "40px"}}
                                               (-> props .-data .-label)])}
                        :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}}
                        
                        :onNodeClick
                        (fn [_ node]
                          (re-frame/dispatch
                           [::set-selected-node (js->clj node :keywordize-keys true)]))
                        
                        :onPaneClick
                        (fn [_] (re-frame/dispatch [::clear-selected-node]))}
          [:> Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"}]
          [:> Controls {:className "fill-gray-500 stroke-gray-500"}]]]
        (when selected-node
          [:div.mt-6.bg-white.p-6.rounded-lg.shadow
           [:h3.text-xl.font-semibold.mb-3.text-gray-700 "Selected Node Details"]
           [:pre.bg-gray-50.p-4.rounded-md.text-sm.overflow-x-auto.border.border-gray-200
            (common/pp selected-node)]])]])))

