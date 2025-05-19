(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.http :as http]
   [com.rpl.agent-o-rama.ui.graph :as graph]
   
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [com.rpl.agent-o-rama.ui.common :as common]
   [reitit.frontend.easy :as rfe]))


;; ========== index ==========
(def index-fsm {:http-xhrio  {:uri             "/api/agents"
                              :method          :get
                              :response-format (ajax/transit-response-format)}
                :max-retries 5
                :path        [::index]})


(re-frame/reg-sub ::index (fn [db _] (::index db)))

(defn index []
  (let [agents @(re-frame/subscribe [::index])]
    [:div.container.mx-auto.p-6.max-w-3xl
     [:h1.text-3xl.font-bold.mb-6.text-gray-800 "Agents"]
     [common/http-loader-view [::index]
      [:ol.space-y-3
       (for [agent agents
             :let [url (rfe/href ::agent agent)]]
         [:li.bg-white.p-4.rounded-lg.shadow.hover:shadow-md.transition-shadow.duration-200 {:key url}
          [:a.text-indigo-600.hover:text-indigo-800.font-medium {:href url}
           (pr-str agent)]])]]]))

;; ========== agent ==========
(defn agent-fsm [{{:keys [module-id agent-id]} :path}]
  {:http-xhrio  {:uri             (str "/api/agents/" module-id "/" agent-id)
                 :method          :get
                 :response-format (ajax/transit-response-format)}
   :max-retries 5
   :path        [::agent module-id agent-id]})

(re-frame/reg-sub
 ::selected-agent
 (fn [db _]
   (let [{:keys [module-id agent-id]} (:path-params (:current-route db))]
     (get-in db [::agent module-id agent-id]))))

(re-frame/reg-sub :route-params (fn [db _] (:path-params (:current-route db))))

(defn agent []
  (let [agent-data @(re-frame/subscribe [::selected-agent])
        {:keys [module-id agent-id]} @(re-frame/subscribe [:route-params])
        view-path [::agent module-id agent-id]]
    [common/http-loader-view view-path
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
           [:li.p-3.bg-gray-50.rounded-md.border.border-gray-200 {:key (:root-invoke-id data)}
            [:div.flex.justify-between.items-center.mb-2
             [:a.text-indigo-600.hover:text-indigo-800.font-medium.text-sm
              {:href (rfe/href ::invoke {:module-id module-id
                                          :agent-id agent-id
                                          :invoke-id (:root-invoke-id data)})} 
              "Explore Invocation"]
             ]
            [:pre.text-xs.bg-gray-100.p-2.rounded.overflow-x-auto (common/pp data)]])]]]]]))

;; ============ invoke ==============
(defn invoke-fsm [{{:keys [module-id agent-id invoke-id]} :path}]
  {:http-xhrio  {:uri             (str "/api/agents/" module-id "/" agent-id "/" invoke-id)
                 :method          :get
                 :response-format (ajax/transit-response-format)}
   :max-retries 5
   :path        [::invoke module-id agent-id invoke-id]})

(re-frame/reg-sub
 ::selected-invoke
 (fn [db _]
   (let [{:keys [module-id agent-id invoke-id]} (:path-params (:current-route db))]
     (get-in db [::invoke module-id agent-id invoke-id]))))

(defn invoke []
  (let [invoke-data @(re-frame/subscribe [::selected-invoke])]
    [:div.container.mx-auto.p-6.max-w-full
     [:div.bg-white.p-6.rounded-lg.shadow.mb-6
      [graph/graph]]
     [:div.bg-white.p-6.rounded-lg.shadow
      [:h2.text-2xl.font-semibold.mb-4.text-gray-700 "Invocation Details"]
      [:pre.bg-gray-50.p-4.rounded-md.text-sm.overflow-x-auto.border.border-gray-200 
       (common/pp invoke-data)]]]))

(def routes
  ["agents"
   [""
    {:name      ::index
     :view      index
     :link-text "agent index"
     :controllers
     [{:start (fn [_] (re-frame/dispatch [::http/start index-fsm]))}]}]
   ["/:module-id/:agent-id"
    {:name      ::agent
     :view      agent
     :link-text "agent"
     :controllers
     [{:parameters {:path [:module-id :agent-id]}
       :start (fn [params]
                (re-frame/dispatch [::http/start (agent-fsm params)]))}]}]
   
   ["/:module-id/:agent-id/:invoke-id"
    {:name      ::invoke
     :view      invoke
     :link-text "invoke"
     :controllers
     [{:parameters {:path [:module-id :agent-id :invoke-id]}
       :start (fn [params]
                (re-frame/dispatch [::http/start (invoke-fsm params)]))}]}]])
