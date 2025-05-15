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
(def index-fsm {:id          ::index
                :http-xhrio  {:uri             "/api/agents"
                              :method          :get
                              :response-format (ajax/transit-response-format)}
                :max-retries 5
                :path        [::index]})

(re-frame/reg-sub ::index (fn [db _] (::index db)))

(defn index []
  (let [agents @(re-frame/subscribe [::index])]
    [:div.p-4
     [:h1.text-2xl.font-bold.mb-4 "agents"]
     [common/http-loader-view ::index
      [:ol.list-decimal.list-inside
       (for [agent agents
             :let [url (rfe/href ::agent agent)]]
         [:li.mb-2 {:key url}
          [:a.text-blue-600.hover:underline {:href url}
           (pr-str agent)]])]]]))

;; ========== agent ==========
(defn agent-fsm-id [module-id agent-id]
  (keyword (str "agent-" module-id "-" agent-id)))

(defn agent-fsm [{{:keys [module-id agent-id]} :path}]
  {:id          (agent-fsm-id module-id agent-id)
   :http-xhrio  {:uri             (str "/api/agents/" module-id "/" agent-id)
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
        {:keys [module-id agent-id]} @(re-frame/subscribe [:route-params])]
    [common/http-loader-view (agent-fsm-id module-id agent-id)
     [:div.p-4.grid.grid-cols-1.md:grid-cols-2.gap-4
      [:div.border.border-gray-300.rounded.p-4.md:col-span-2
       [:h1.text-xl.font-semibold.mb-2 "stats"]
       [:ul.list-disc.list-inside
        [:li "run count 30"]
        [:li "total tokens 3707"]
        [:li "median tokens 621"]
        [:li "error rate"]
        [:li "latency"]
        [:ul.list-circle.list-inside.ml-4
         [:li "p50 1.58s"]
         [:li "p99 3.23s"]]]]
      [:div.border.border-gray-300.rounded.p-4.md:col-span-2
       [:h1.text-xl.font-semibold.mb-2 "invokes"]
       [:ol.list-decimal.list-inside
        (for [data (:invokes agent-data)]
          [:li.mb-2 {:key (:root-invoke-id data)}
           [:div.p-2.bg-gray-50.rounded
            [:a.text-blue-600.hover:underline.mr-2 {:href (rfe/href ::invoke {:module-id module-id
                                                                              :agent-id agent-id
                                                                              :invoke-id (:root-invoke-id data)})}
             "explore"]
            [:pre.text-sm.overflow-x-auto (common/pp data)]]])]]]]))

;; ============ invoke ==============


(re-frame/reg-sub
 ::selected-invoke
 (fn [db _]
   (let [{:keys [module-id agent-id invoke-id]} (:path-params (:current-route db))]
     (get-in db [::invoke module-id agent-id invoke-id]))))

(defn invoke-fsm-id [module-id agent-id invoke-id]
  (keyword (str "invoke-" module-id "-" agent-id "-" invoke-id)))

(defn invoke-fsm [{{:keys [module-id agent-id invoke-id]} :path}]
  {:id          (invoke-fsm-id module-id agent-id invoke-id)
   :http-xhrio  {:uri             (str "/api/agents/" module-id "/" agent-id "/" invoke-id)
                 :method          :get
                 :response-format (ajax/transit-response-format)}
   :max-retries 5
   :path        [::invoke module-id agent-id invoke-id]})

(defn invoke []
  (let [invoke-data @(re-frame/subscribe [::selected-invoke])]
    [:div.p-4
     [graph/graph]
     [:h2.text-xl.font-semibold.mb-2 "invoke details"]
     [:pre.bg-gray-100.p-3.rounded.text-sm.overflow-x-auto (common/pp invoke-data)]]))

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
