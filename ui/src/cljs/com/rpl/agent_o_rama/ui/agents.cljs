(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [glimt.core :as http]
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
    [:div
     [:h1 "agents"]
     [common/http-loader-view ::index
      [:ol
       (for [agent agents
             :let [url (rfe/href ::agent agent)]]
         [:li {:key url}
          [:a {:href url} 
           (pr-str agent)]])]]]))

;; ========== agent ==========
(defn agent-fsm [params]
  (println "PARAMS" params))

(defn agent []
  [:div "agent"])

(def routes
  ["agents"
   [""
    {:name      ::index
     :view      index
     :link-text "agent index"
     :controllers
     [{:start (fn [& params] (re-frame/dispatch [::http/start index-fsm]))}]}]
   ["/:module-id/:agent-id"
    {:name      ::agent
     :view      agent
     :link-text "agent"
     :controllers
     [{:start (fn [& params]
                #_(re-frame/dispatch [::http/start fsm])
                (agent-fsm params))}]}]])
