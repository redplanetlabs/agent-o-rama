(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation]]
   ["@tanstack/react-query" :refer [QueryClient QueryClientProvider]]))

(def query-client (QueryClient.))

(defui home []
  ($ :div "home"))

(defui nav-row [{:keys [location href title]}]
  ($ Link {:href href :class (if (= href location) "bg-purple-200 px-2" "px-2")} title))

(defui nav []
  (let [[location _] (useLocation)]
    ($ :div.bg-purple-100.flex.p-2
       ($ nav-row {:location location :href "/" :title "home"})
       ($ nav-row {:location location :href "/agents" :title "agents"}))))

(defui app []
  ($ :div
     ($ nav)
     ($ Router
        ($ Route {:path "/agents" :component agents/index})
        ($ Route {:path "/agents/:module-id/:agent-id" :component agents/agent})
        ($ Route {:path "/" :component home}))))


(defn init []
  (uix.dom/render-root
   ($ QueryClientProvider {:client query-client}
      ($ app))
   (uix.dom/create-root
    (.getElementById js/document "root"))))
