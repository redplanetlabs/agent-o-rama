(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation useRoute]]
   ["@tanstack/react-query" :refer [QueryClient QueryClientProvider]]
   
   [com.rpl.agent-o-rama.ui.datasets :as datasets]))

(def query-client (QueryClient.))

(defui home []
  ($ :div "home"))

(defui breadcrumb-item [{:keys [pattern href title get-title get-href is-last]}]
  (let [[location _] (useLocation)
        [match params] (useRoute pattern)
        ;; Also check if we're on a deeper route that starts with this pattern
        [deeper-match deeper-params] (useRoute (str pattern "/*"))
        params (when (or params deeper-params) (js->clj (or params deeper-params) {:keywordize-keys true}))
        should-show (cond
                      pattern (or match deeper-match)
                      href true
                      :else false)
        actual-href (cond
                      get-href (get-href params)
                      href href
                      :else nil)
        actual-title (cond
                       get-title (get-title params)
                       title title
                       :else "")
        is-active (= location actual-href)]
    
    (when should-show
      ($ :div.flex.items-center
         (if actual-href
           ($ Link {:href actual-href 
                   :class (if is-active "bg-purple-200 px-2 py-1 rounded" "px-2 py-1 hover:bg-purple-50 rounded")} 
              actual-title)
           ($ :span.px-2.py-1.text-gray-600 actual-title))
         (when-not is-last
           ($ :span.mx-2.text-gray-400 ">"))))))

(defui nav []
  ($ :div.bg-purple-100.flex.p-2.items-center
     ;; Home breadcrumb
     ($ breadcrumb-item {:href "/" :title "home"})
     
     ;; Agent breadcrumb
     ($ breadcrumb-item {:pattern "/agents/:module-id/:agent-id"
                        :get-href (fn [params] (str "/agents/" (:module-id params) "/" (:agent-id params)))
                        :get-title (fn [params] (str (:module-id params) "/" (:agent-id params)))})
     
     ;; Tab breadcrumbs
     ($ breadcrumb-item {:pattern "/agents/:module-id/:agent-id/invocations"
                        :get-href (fn [params] (str "/agents/" (:module-id params) "/" (:agent-id params) "/invocations"))
                        :get-title (fn [params] "invocations")})
     
     ($ breadcrumb-item {:pattern "/agents/:module-id/:agent-id/datasets"
                        :get-href (fn [params] (str "/agents/" (:module-id params) "/" (:agent-id params) "/datasets"))
                        :get-title (fn [params] "datasets")})
     
     ($ breadcrumb-item {:pattern "/agents/:module-id/:agent-id/evaluations"
                        :get-href (fn [params] (str "/agents/" (:module-id params) "/" (:agent-id params) "/evaluations"))
                        :get-title (fn [params] "evaluations")})
     
     ;; Invoke breadcrumb  
     ($ breadcrumb-item {:pattern "/agents/:module-id/:agent-id/invocations/:invoke-id"
                        :get-href (fn [params] (str "/agents/" (:module-id params) "/" (:agent-id params) "/" (:invoke-id params)))
                        :get-title (fn [params] (str (:invoke-id params)))
                        :is-last true})))

(defui app []
  ($ :div
     ($ nav)
     ($ Router
        ($ Route {:path "/agents/:module-id/:agent-id/invocations" :component agents/invocations})
        ($ Route {:path "/agents/:module-id/:agent-id/invocations/:invoke-id" :component agents/invoke})
        ($ Route {:path "/agents/:module-id/:agent-id/datasets" :component datasets/datasets})
        ($ Route {:path "/agents/:module-id/:agent-id/evaluations" :component agents/evaluations})
        ($ Route {:path "/agents/:module-id/:agent-id" :component agents/agent})
        ($ Route {:path "/" :component agents/index}))))


(defn init []
  (uix.dom/render-root
   ($ QueryClientProvider {:client query-client}
      ($ app))
   (uix.dom/create-root
    (.getElementById js/document "root"))))
