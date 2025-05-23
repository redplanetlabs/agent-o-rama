(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation]]))


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

(defui nav-row [{:keys [location href title]}]
  ($ Link {:href href :class (if (= href location) "bg-purple-200 px-2" "px-2")} title))

(defui nav []
  (let [[location _] (useLocation)]
    ($ :div.bg-purple-100.flex.p-2
       ($ nav-row {:location location :href "/" :title "home"})
       ($ nav-row {:location location :href "/counter" :title "counter"}))))

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
