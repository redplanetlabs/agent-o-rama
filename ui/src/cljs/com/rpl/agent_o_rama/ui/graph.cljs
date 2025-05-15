(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [com.rpl.agent-o-rama.ui.common :as common]
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
  (let [data (:invokes-map @(re-frame/subscribe [:com.rpl.agent-o-rama.ui.agents/selected-invoke]))
        selected-node @(re-frame/subscribe [::selected-node])
        
        g (new (.. Dagre -graphlib -Graph))
        
        nodes (into [] (map (fn [[id data]]
                              {:id (str id) :data (assoc data :label (:node data))}) data))
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
                 :target (str (get implicit->real to to))})
        ]
    
    (tap>)

    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {})
    
    (doall (for [edge edges] (.setEdge g (:source edge) (:target edge))))
    (doall (for [node nodes] (.setNode g (:id node) (clj->js
                                                     (merge node {:width 30 :height 30})))))
    
    (Dagre/layout g)

    (let [nodes (for [node nodes
                      :let [position (.node g (:id node))]]
                  (assoc node :position position))]

      [:<>
       [:div.border.border-gray-300.rounded.p-4
        [:h1.text-xl.font-semibold.mb-2 "agent graph render"]
        [:div {:style {:width "100%" :height "400px"}}
         [:> ReactFlow {:nodes nodes :edges edges
                        :proOptions {:hideAttribution true}
                        
                        :onNodeClick
                        (fn [_ node]
                          (re-frame/dispatch
                           [::set-selected-node (js->clj node :keywordize-keys true)]))
                        
                        :onPaneClick
                        (fn [_] (re-frame/dispatch [::clear-selected-node]))}
          [:> Background]
          [:> Controls]]]]
       [:div.border.border-gray-300.rounded.p-4
        [:h1.text-xl.font-semibold.mb-2 "node runs"]
        (if selected-node
          [:ol.list-decimal.list-inside
           (for [run-data (get-in selected-node [:data :runs])]
             [:li.mb-2 {:key (:root-invoke-id run-data)}
              [:div.p-2.bg-gray-50.rounded
               [:a.text-blue-600.hover:underline.mr-2 {:href (rfe/href ::invoke {:module-id (get-in selected-node [:data :module-id] "TODO") ; Placeholder
                                                                                 :agent-id (get-in selected-node [:data :agent-id] "TODO") ; Placeholder
                                                                                 :invoke-id (:root-invoke-id run-data)})}
                "explore"]
               [:span.mr-2 (:name run-data)] 
               [:pre.text-sm.overflow-x-auto (common/pp run-data)]]])]
          [:p "Select a node to see its runs."])]])))
