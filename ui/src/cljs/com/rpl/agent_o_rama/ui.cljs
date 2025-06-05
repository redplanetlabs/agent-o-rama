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
    ($ :div.w-64.h-screen.flex.flex-col.bg-gray-100
       ;; Navigation links
       ($ :nav.flex-1.p-4
          ($ :div.space-y-2
             ;; Home/Overview
             ($ Link
                {:href "/"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (= location "/")
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Overview")
             
             ;; Agents section
             ($ Link
                {:href "/agents"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (or (= location "/agents") 
                                         (.startsWith location "/agents/"))
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Agents")
             
             ;; Datasets section  
             ($ Link
                {:href "/datasets"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (or (= location "/datasets")
                                         (.startsWith location "/datasets/"))
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Datasets")))
       
       ;; Footer
       ($ :div.p-4.border-t.border-gray-700.text-gray-400.text-sm
          ($ :div "v1.0.0-beta")
          ($ :div "Built with Rama")))))

;; Breadcrumb for sub-navigation within sections
(defui breadcrumb []
  (let [[location _] (useLocation)]
    
    ($ :div.bg-gray-100.px-4.py-2.border-b.text-sm.text-gray-600
       ;; TODO make clickable/dynamic
       ($ :div location))))

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
