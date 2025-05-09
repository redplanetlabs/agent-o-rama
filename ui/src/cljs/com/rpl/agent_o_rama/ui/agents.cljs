(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [re-frame.core :as re-frame]
   [ajax.core :as ajax]))

(defn index []
  (let [loading @(re-frame/subscribe [::loading])]
    [:div (str "all agents " (pr-str loading))]))

(re-frame/reg-sub ::loading (fn [db _] (::loading db)))

(re-frame/reg-event-fx
 ::load
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading true)
    :http-xhrio
    {:method :get
     :uri "/api/users/3"
     :response-format (ajax/json-response-format {:keywords? true})
     :on-success [::loaded]}}))

(re-frame/reg-event-fx
 ::loaded
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading false)}))

(def routes
  ["agents"
   [""
    {:name      ::index
     :view      index
     :link-text "agent index"
     :controllers
     [{:start (fn [& params] (re-frame/dispatch [::load]))}]}]])
