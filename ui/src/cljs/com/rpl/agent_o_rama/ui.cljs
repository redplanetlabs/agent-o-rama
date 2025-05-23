(ns com.rpl.agent-o-rama.ui
  (:require
   [reagent.dom :as rd]
   [reitit.core :as r]
   [reitit.coercion.spec :as rss]
   [reitit.frontend :as rf]
   [reitit.frontend.controllers :as rfc]
   [reitit.frontend.easy :as rfe]
   
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router]]))


(defui button [{:keys [on-click children]}]
  ($ :button.btn {:on-click on-click}
    children))

(defui counter []
  (let [[state set-state!] (uix.core/use-state 0)]
    ($ :div
      ($ button {:on-click #(set-state! dec)} "-")
      ($ :span state)
      ($ button {:on-click #(set-state! inc)} "+"))))

(defui agents-viewer []
  ($ :div "agents"))

(defui nav []
  ($ :div
     ($ Link {:href "/counter" :class "bg-red-500"} "counter")
     ($ Link {:href "/"} "home")))

(defui app []
  ($ :div
     ($ nav)
     ($ Router
        ($ Route {:path "/counter" :component counter})
        ($ Route {:path "/" :component agents-viewer}))))

(defn init []
  (uix.dom/render-root
   ($ app)
   (uix.dom/create-root
    (.getElementById js/document "root"))))
