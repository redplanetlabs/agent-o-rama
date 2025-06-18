(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation useRoute]]
   ["@tanstack/react-query" :refer [QueryClient QueryClientProvider]]
   
   [com.rpl.agent-o-rama.ui.datasets :as datasets]))

(def query-client (QueryClient.))

;; Sidebar navigation component
(defui sidebar-nav []
  (let [[location _] (useLocation)]
    ($ :div.w-64.h-screen.flex.flex-col.bg-gray-100
       ($ :nav.flex-1.p-4
          ($ :div.space-y-2
             ($ Link
                {:href "/"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (= location "/")
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Overview")
             ($ Link
                {:href "/agents"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (or (= location "/agents") 
                                         (.startsWith location "/agents/"))
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Agents")
             ($ Link
                {:href "/datasets"
                 :className (str "flex items-center px-3 py-2 transition-colors "
                                 (if (or (= location "/datasets")
                                         (.startsWith location "/datasets/"))
                                   "bg-gray-300"
                                   "hover:bg-gray-200"))}
                "Datasets"))))))

;; Breadcrumb for sub-navigation within sections
(defui breadcrumb []
  (let [[location _] (useLocation)
        segments (-> location
                     (str/replace #"^/" "")
                     (str/split #"/")
                     vec)
        
        ;; Build breadcrumb items with proper merging for module/agent
        build-breadcrumbs (fn [segments]
                            (loop [remaining segments
                                   result []
                                   path ""]
                              (if (empty? remaining)
                                result
                                (let [segment (first remaining)
                                      next-segment (second remaining)
                                      ;; Check if this is an agent module/agent-id pair
                                      is-agent-pair? (and (= (get segments 0) "agents")
                                                          (= (count result) 1)
                                                          next-segment)
                                      ;; Build the item
                                      item (if is-agent-pair?
                                             ;; Merge module/agent into one breadcrumb
                                             {:label (str segment "/" next-segment)
                                              :path (str path "/" segment "/" next-segment)
                                              :segments-consumed 2}
                                             ;; Regular breadcrumb
                                             {:label (str/capitalize segment)
                                              :path (str path "/" segment)
                                              :segments-consumed 1})]
                                  (recur (drop (:segments-consumed item) remaining)
                                         (conj result item)
                                         (:path item))))))
        
        breadcrumb-items (when (seq segments)
                           (build-breadcrumbs segments))]
    
    ($ :div.bg-gray-100.px-4.py-2.border-b.text-sm.text-gray-600
       ($ :div.flex.items-center.space-x-2
          ;; Home link (always present)
          ($ Link {:href "/" :className "text-blue-600 hover:text-blue-800"} "Home")
          
          ;; Build breadcrumbs from segments
          (when breadcrumb-items
            (map-indexed
             (fn [idx item]
               (let [is-last? (= idx (dec (count breadcrumb-items)))]
                 (list
                  ;; Separator
                  ($ :span {:key (str "sep-" idx)} " › ")
                  ;; Link or text
                  (if is-last?
                    ;; Current page - not clickable
                    ($ :span {:key (str "crumb-" idx) :className "text-gray-500"} 
                       (:label item))
                    ;; Clickable link
                    ($ Link {:key (str "crumb-" idx)
                            :href (:path item)
                            :className "text-blue-600 hover:text-blue-800"}
                       (:label item))))))
             breadcrumb-items))))))

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
