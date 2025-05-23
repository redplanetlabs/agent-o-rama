(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.query :as query]
   [com.rpl.agent-o-rama.ui.graph :as graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.agent-o-rama.ui.common :as common]))

;; ========== index ==========
#_(defn index []
  [:div
   [:div.flex.items-center.justify-between.mb-8
    [:h1.text-3xl.font-bold.text-gray-800 "Agents"]
    [:div.text-sm.text-gray-500 (str (count agents) " total")]]
   [:div.divide-y.divide-gray-100
    (for [agent agents
          :let [url (rfe/href ::agent agent)]]
      [:div.py-4.transition-colors.duration-150.hover:bg-gray-50 {:key url}
       [:a.flex.items-center.group {:href url}
        [:div.flex-1
         [:div.text-lg.font-medium.text-indigo-600.group-hover:text-indigo-800
          (let [{:keys [module-id agent-id]} agent]
            [:div module-id "/" agent-id])]
         [:div.mt-1.text-sm.text-gray-500.group-hover:text-gray-700
          "View agent details"]]]])]])

(defui index []
  ($ :div "agent index"))

;; ========== agent ==========
#_(defn agent []
  (let [{:keys [module-id agent-id]} @(re-frame/subscribe [:route-params])]
    [query/query-view 
     [:agent module-id agent-id]
     (fn [agent-data]
       [:div.container.mx-auto.p-6.max-w-4xl
        [:div.grid.grid-cols-1.md:grid-cols-3.gap-6
         [:div.md:col-span-1.bg-white.p-6.rounded-lg.shadow
          [:h2.text-2xl.font-semibold.mb-4.text-gray-700 "Stats"]
          [:ul.space-y-2.text-gray-600
           [:li [:span.font-medium "Run Count:"] " 30"]
           [:li [:span.font-medium "Total Tokens:"] " 3707"]
           [:li [:span.font-medium "Median Tokens:"] " 621"]
           [:li [:span.font-medium "Error Rate:"] " N/A"] ; Placeholder
           [:li [:span.font-medium "Latency:"]
            [:ul.list-disc.list-inside.ml-4.mt-1.space-y-1
             [:li "p50: 1.58s"]
             [:li "p99: 3.23s"]]]]]
         [:div.md:col-span-2.bg-white.p-6.rounded-lg.shadow
          [:h2.text-2xl.font-semibold.mb-4.text-gray-700 "Invokes"]
          [:ol.space-y-3
           (for [data (:invokes agent-data)]
             [:a.p-3.bg-gray-50.rounded-md.border.border-gray-200.block.hover:bg-gray-100.transition-colors
              {:key (:root-invoke-id data)
               :href (rfe/href ::invoke {:module-id module-id
                                        :agent-id agent-id
                                        :invoke-id (:root-invoke-id data)})}
              
              [:div.flex.justify-between.items-center.mb-2
               [:div.text-indigo-600.font-medium.text-sm
                "Explore Invocation"]
               ]
              [:pre.text-xs.bg-gray-100.p-2.rounded.overflow-x-auto (common/pp data)]])]]]])]))

;; ============ invoke ==============
#_(defn invoke []
  (let [{:keys [module-id agent-id invoke-id]} @(re-frame/subscribe [:route-params])]
    [query/query-view 
     [:invoke module-id agent-id invoke-id]
     (fn [invoke-data]
       [:div.container.mx-auto.p-6.max-w-full
        [:div.bg-white.p-6.rounded-lg.shadow.mb-6
         [graph/graph]]
        [:div.bg-white.p-6.rounded-lg.shadow
         [:h2.text-2xl.font-semibold.mb-4.text-gray-700 "Invocation Details"]
         [:pre.bg-gray-50.p-4.rounded-md.text-sm.overflow-x-auto.border.border-gray-200 
          (common/pp invoke-data)]]])]))


#_(def routes
  ["agents"
   [""
    {:name ::index
     :view index
     :link-text "agent index"
     :controllers [{:parameters {}
                    :start (fn [_]
                             (re-frame/dispatch
                              [:query/fetch
                               {:key [:agent-index]
                                :uri "/api/agents"
                                :opts {:method :get
                                       :response-format (ajax/transit-response-format)
                                       :retries 5}}]))}]}]
   
   ["/:module-id/:agent-id"
    {:name ::agent
     :view agent
     :link-text "agent"
     :controllers [{:parameters {:path [:module-id :agent-id]}
                    :start (fn [{{:keys [module-id agent-id]} :path}]
                             (re-frame/dispatch
                              [:query/fetch
                               {:key [:agent module-id agent-id]
                                :uri (str "/api/agents/" module-id "/" agent-id)
                                :opts {:method :get
                                       :response-format (ajax/transit-response-format)
                                       :retries 5}}]))}]}]
   
   ["/:module-id/:agent-id/:invoke-id"
    {:name ::invoke
     :view invoke
     :link-text "invoke"
     :controllers [{:parameters {:path [:module-id :agent-id :invoke-id]}
                    :start (fn [{{:keys [module-id agent-id invoke-id]} :path}]
                             (re-frame/dispatch
                              [:query/fetch
                               {:key [:invoke module-id agent-id invoke-id]
                                :uri (str "/api/agents/" module-id "/" agent-id "/" invoke-id)
                                :opts {:method :get
                                       :response-format (ajax/transit-response-format)
                                       :retries 5}}]))}]}]])
