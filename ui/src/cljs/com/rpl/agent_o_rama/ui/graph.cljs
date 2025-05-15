(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [com.rpl.agent-o-rama.ui.common :as common]
   [reitit.frontend.easy :as rfe]

   ["@xyflow/react" :refer [ReactFlow Background Controls useOnSelectionChange]]
   ["react" :refer [useState useCallback]]))

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
  (let [data @(re-frame/subscribe [:com.rpl.agent-o-rama.ui.agents/selected-agent])
        selected-node @(re-frame/subscribe [::selected-node])
        nodes [{:id "1" :position {:x 0 :y 0} :data {:label "first node" :runs [{:root-invoke-id 101 :name "Run A"}]}}
               {:id "2" :position {:x 0 :y 100} :data {:label "second node" :arbitrary 3 :runs [{:root-invoke-id 201 :name "Run B"} {:root-invoke-id 202 :name "Run C"}]}}]
        edges [{:id "1->2" :source "1" :target "2"}]]
    [:<>
     [:div.border.border-gray-300.rounded.p-4
      [:h1.text-xl.font-semibold.mb-2 "agent graph render"]
      [:div {:style {:width "100%" :height "400px"}}
       [:> ReactFlow {:nodes nodes :edges edges
                      :proOptions {:hideAttribution true}
                      :onNodeClick (fn [_ node] (re-frame/dispatch [::set-selected-node (js->clj node :keywordize-keys true)]))
                      :onPaneClick (fn [_] (re-frame/dispatch [::clear-selected-node]))}
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
        [:p "Select a node to see its runs."])]]))
