(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [glimt.core :as http]
   [re-frame.core :as re-frame]
   [reagent.core :as reagent]
   [ajax.core :as ajax]
   [com.rpl.agent-o-rama.ui.common :as common]))

(def fsm {:id          ::index
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
       (for [agent agents]
         [:li {:key (str (:module-id agent) (:agent-id agent))}
          (pr-str agent)])]]]))

(def routes
  ["agents"
   [""
    {:name      ::index
     :view      index
     :link-text "agent index"
     :controllers
     [{:start (fn [& params] (re-frame/dispatch [::http/start fsm]))}]}]])
