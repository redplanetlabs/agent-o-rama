(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [com.rpl.agent-o-rama.ui.common :as common]
   [reitit.frontend.easy :as rfe]

   ["@xyflow/react" :refer [ReactFlow Background Controls]]
   ["react" :refer [useState useCallback]]))

(defn graph []
  (let [data @(re-frame/subscribe [:com.rpl.agent-o-rama.ui.agents/selected-agent])
        nodes [{:id "1" :position {:x 0 :y 0} :data {:label "first node"}}
               {:id "2" :position {:x 0 :y 100} :data {:label "second node"}}]
        edges [{:id "1->2" :source "1" :target "2"}]]
    [:div
     (pr-str (:invokes data))
     [:div {:style {:height "300px" :width "300px"}}
      [:> ReactFlow {:nodes nodes :edges edges :proOptions {:hideAttribution true}}
       [:> Background]
       [:> Controls]]]]))
