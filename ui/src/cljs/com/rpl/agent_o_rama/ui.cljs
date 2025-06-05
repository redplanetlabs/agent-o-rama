(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation useRoute]]
   ["@tanstack/react-query" :refer [QueryClient QueryClientProvider]]
   
   [com.rpl.agent-o-rama.ui.datasets :as datasets]))

(def query-client (QueryClient.))

;; Sidebar navigation component
(defui sidebar-nav []
  (let [[location _] (useLocation)]
    ($ :div.w-64.bg-gray-900.text-white.h-screen.flex.flex-col
       ;; Header
       ($ :div.p-4.border-b.border-gray-700
          ($ :h1.text-xl.font-bold "Agent-o-rama")
          ($ :p.text-gray-400.text-sm "AI Agent Platform"))
       
       ;; Navigation links
       ($ :nav.flex-1.p-4
          ($ :div.space-y-2
             ;; Home/Overview
             ($ Link
                {:href "/"
                 :className (str "flex items-center px-3 py-2 rounded-lg transition-colors "
                               (if (= location "/")
                                 "bg-blue-600 text-white"
                                 "text-gray-300 hover:bg-gray-800 hover:text-white"))}
                ($ :span.mr-3 "🏠")
                "Overview")
             
             ;; Agents section
             ($ Link
                {:href "/agents"
                 :className (str "flex items-center px-3 py-2 rounded-lg transition-colors "
                               (if (or (= location "/agents") 
                                      (.startsWith location "/agents/"))
                                 "bg-blue-600 text-white"
                                 "text-gray-300 hover:bg-gray-800 hover:text-white"))}
                ($ :span.mr-3 "🤖")
                "Agents")
             
             ;; Datasets section  
             ($ Link
                {:href "/datasets"
                 :className (str "flex items-center px-3 py-2 rounded-lg transition-colors "
                               (if (or (= location "/datasets")
                                      (.startsWith location "/datasets/"))
                                 "bg-blue-600 text-white"
                                 "text-gray-300 hover:bg-gray-800 hover:text-white"))}
                ($ :span.mr-3 "📊")
                "Datasets")
             
             ;; Future: Evaluations
             ($ :div.flex.items-center.px-3.py-2.text-gray-500.cursor-not-allowed
                ($ :span.mr-3 "📈")
                "Evaluations"
                ($ :span.ml-auto.text-xs.bg-gray-700.px-2.py-1.rounded "Soon"))
             
             ;; Future: Settings
             ($ :div.flex.items-center.px-3.py-2.text-gray-500.cursor-not-allowed
                ($ :span.mr-3 "⚙️")
                "Settings"
                ($ :span.ml-auto.text-xs.bg-gray-700.px-2.py-1.rounded "Soon"))))
       
       ;; Footer
       ($ :div.p-4.border-t.border-gray-700.text-gray-400.text-sm
          ($ :div "v1.0.0-beta")
          ($ :div "Built with Rama")))))

;; Breadcrumb for sub-navigation within sections
(defui breadcrumb []
  (let [[location _] (useLocation)
        [agent-match agent-params] (useRoute "/agents/:module-id/:agent-id/*")
        [dataset-match dataset-params] (useRoute "/datasets/:dataset-id")
        agent-params (when agent-params (js->clj agent-params {:keywordize-keys true}))
        dataset-params (when dataset-params (js->clj dataset-params {:keywordize-keys true}))]
    
    ($ :div.bg-gray-100.px-4.py-2.border-b.text-sm.text-gray-600
       (cond
         ;; Agent sub-navigation
         agent-match
         ($ :div.flex.items-center.space-x-2
            ($ Link {:href "/agents" :className "hover:text-blue-600"} "Agents")
            ($ :span "›")
            ($ Link {:href (str "/agents/" (:module-id agent-params) "/" (:agent-id agent-params))
                    :className "hover:text-blue-600"}
               (str (:module-id agent-params) "/" (:agent-id agent-params)))
            (when (.includes location "/invocations")
              ($ :span
                 ($ :span "›")
                 ($ :span.ml-2 "Invocations"))))
         
         ;; Dataset sub-navigation  
         dataset-match
         ($ :div.flex.items-center.space-x-2
            ($ Link {:href "/datasets" :className "hover:text-blue-600"} "Datasets")
            ($ :span "›")
            ($ :span (:dataset-id dataset-params)))
         
         ;; Default breadcrumb
         :else
         ($ :div.flex.items-center.space-x-2
            ($ :span "Welcome to Agent-o-rama"))))))

;; Main content area wrapper
(defui main-content []
  ($ :div.flex-1.flex.flex-col.min-h-0
     ($ breadcrumb)
     ($ :div.flex-1.overflow-auto
        ($ Router
           ;; Agent routes
           ($ Route {:path "/agents/:module-id/:agent-id/invocations" :component agents/invocations})
           ($ Route {:path "/agents/:module-id/:agent-id/invocations/:invoke-id" :component agents/invoke})
           ($ Route {:path "/agents/:module-id/:agent-id/evaluations" :component agents/evaluations})
           ($ Route {:path "/agents/:module-id/:agent-id" :component agents/agent})
           ($ Route {:path "/agents" :component agents/index})
           
           ;; Dataset routes
           ($ Route {:path "/datasets/:dataset-id" :component datasets/datasets})
           ($ Route {:path "/datasets" :component datasets/datasets})
           
           ;; Home route
           ($ Route {:path "/" :component agents/index})))))

;; Main app component
(defui app []
  ($ :div.flex.h-screen.bg-gray-50
     ($ sidebar-nav)
     ($ main-content)))

(defn init []
  (uix.dom/render-root
   ($ QueryClientProvider {:client query-client}
      ($ app))
   (uix.dom/create-root
    (.getElementById js/document "root"))))
