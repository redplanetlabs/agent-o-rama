(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [re-frame.core :as re-frame]
   [ajax.core :as ajax]))

(defn index []
  (let [loading @(re-frame/subscribe [::loading])
        agents @(re-frame/subscribe [::index])]
    [:div
     [:div (case loading
             :loading "🔄"
             :done    "✅"
             :failed  "❌"
             nil "")]
     [:ol
      (for [agent agents]
        [:li (pr-str agent)])]]))

(re-frame/reg-sub ::loading (fn [db _] (::loading db)))
(re-frame/reg-sub ::index (fn [db _] (::index db)))

(re-frame/reg-event-fx
 ::load
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading :loading)
    :http-xhrio
    {:method :get
     :uri "/api/agents"
     :response-format (ajax/transit-response-format)
     :on-success [::loaded]
     :on-failure [::failed]}}))

(re-frame/reg-event-db
 ::loaded
 (fn [db [_ result]]
   (-> db
       (assoc ::index result)
       (assoc ::loading :done))))

(re-frame/reg-event-fx
 ::failed
 (fn [{:keys [db]} _]
   {:db (assoc db ::loading :failed)}))

(def routes
  ["agents"
   [""
    {:name      ::index
     :view      index
     :link-text "agent index"
     :controllers
     [{:start (fn [& params] (re-frame/dispatch [::load]))}]}]])
