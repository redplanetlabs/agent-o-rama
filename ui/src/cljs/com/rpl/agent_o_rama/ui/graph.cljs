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

(defn graph []
  (let [data @(re-frame/subscribe [:com.rpl.agent-o-rama.ui.agents/selected-agent])
        nodes [{:id "1" :position {:x 0 :y 0} :data {:label "first node"}}
               {:id "2" :position {:x 0 :y 100} :data {:label "second node" :arbitrary 3}}]
        edges [{:id "1->2" :source "1" :target "2"}]]
    [:<>
     [:div.border.border-gray-300.rounded.p-4
      [:h1.text-xl.font-semibold.mb-2 "agent graph render"]
      [:div {:style {:width "400px" :height "400px"}}
       [:> ReactFlow {:nodes nodes :edges edges
                      :proOptions {:hideAttribution true}
                      :onNodeClick (fn [_ node] (println (pr-str (js->clj node))))}
        [:> Background]
        [:> Controls]]]]
     [:div.border.border-gray-300.rounded.p-4
      [:h1.text-xl.font-semibold.mb-2 "nodes"]
      [:ol.list-decimal.list-inside
       (for [data [{:root-invoke-id 3}]]
         [:li.mb-2 {:key (:root-invoke-id data)}
          [:div.p-2.bg-gray-50.rounded
           [:a.text-blue-600.hover:underline.mr-2 {:href (rfe/href ::invoke {:module-id 3
                                                                             :agent-id 4
                                                                             :invoke-id (:root-invoke-id data)})}
            "explore"]
           [:pre.text-sm.overflow-x-auto (common/pp data)]]])]]]))
