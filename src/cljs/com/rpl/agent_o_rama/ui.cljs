(ns com.rpl.agent-o-rama.ui
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [uix.dom]
   [clojure.string :as str]
   
   [com.rpl.agent-o-rama.ui.agents :as agents]
   ["wouter" :refer [Link Route Switch Router useLocation useRoute]]
   ["@tanstack/react-query" :refer [QueryClient QueryClientProvider]]
   ["@heroicons/react/24/outline" :refer [HomeIcon CpuChipIcon CircleStackIcon Bars3Icon XMarkIcon]]
   
   [com.rpl.agent-o-rama.ui.datasets :as datasets]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.stats :as stats]))

(def query-client (QueryClient.))

;; Sidebar navigation component
(defui sidebar-nav []
  (let [[location _] (useLocation)
        [collapsed set-collapsed] (common/use-local-storage "sidebar-collapsed" false)
        toggle-collapsed #(set-collapsed not)]
    ($ :div {:className (str "h-screen flex flex-col bg-gray-100 transition-all duration-300 "
                             (if collapsed "w-16" "w-64"))}
       ;; Header with toggle button
       ($ :div.flex.items-center.justify-between.p-4.border-b.border-gray-200
          (when-not collapsed
            ($ :h1.text-lg.font-semibold.text-gray-800 "Agent-O-Rama"))
          ($ :button
             {:onClick toggle-collapsed
              :className "p-2 rounded-md hover:bg-gray-200 transition-colors"
              :title (if collapsed "Expand sidebar" "Collapse sidebar")}
             (if collapsed
               ($ Bars3Icon {:className "h-5 w-5"})
               ($ XMarkIcon {:className "h-5 w-5"}))))
       
       ;; Navigation
       ($ :nav.flex-1.p-3
          ($ :div.space-y-2
             ;; Overview link
             ($ Link
                {:href "/"
                 :className (str "flex items-center px-3 py-2 rounded-md transition-colors "
                                 (if collapsed "justify-center" "")
                                 (if (= location "/")
                                   "bg-gray-300 text-gray-900"
                                   "hover:bg-gray-200 text-gray-700"))
                 :title (when collapsed "Overview")}
                ($ HomeIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed
                  ($ :span.ml-3 "Overview")))
             
             ;; Agents link
             ($ Link
                {:href "/agents"
                 :className (str "flex items-center px-3 py-2 rounded-md transition-colors "
                                 (if collapsed "justify-center" "")
                                 (if (or (= location "/agents") 
                                         (.startsWith location "/agents/"))
                                   "bg-gray-300 text-gray-900"
                                   "hover:bg-gray-200 text-gray-700"))
                 :title (when collapsed "Agents")}
                ($ CpuChipIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed
                  ($ :span.ml-3 "Agents")))
             
             ;; Datasets link
             ($ Link
                {:href "/datasets"
                 :className (str "flex items-center px-3 py-2 rounded-md transition-colors "
                                 (if collapsed "justify-center" "")
                                 (if (or (= location "/datasets")
                                         (.startsWith location "/datasets/"))
                                   "bg-gray-300 text-gray-900"
                                   "hover:bg-gray-200 text-gray-700"))
                 :title (when collapsed "Datasets")}
                ($ CircleStackIcon {:className "h-5 w-5 flex-shrink-0"})
                (when-not collapsed
                  ($ :span.ml-3 "Datasets"))))))))

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
                                      ;; Check if this is an agent module/agent-name pair
                                      is-agent-pair? (and (= (get segments 0) "agents")
                                                          (= (count result) 1)
                                                          next-segment)
                                      ;; Build the item
                                      item (if is-agent-pair?
                                             ;; Merge module/agent into one breadcrumb
                                             {:label (str segment ":" next-segment)
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
    
    ($ :div.bg-gray-100.px-4.py-2.text-sm.text-gray-600
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
                       (common/url-decode (:label item)))
                    ;; Clickable link
                    ($ Link {:key (str "crumb-" idx)
                            :href (:path item)
                            :className "text-blue-600 hover:text-blue-800"}
                       (common/url-decode (:label item)))))))
             breadcrumb-items))))))

;; Main content area wrapper
(defui main-content []
  ($ :div.flex-1.flex.flex-col.min-h-0
     ($ breadcrumb)
     ($ :div.flex-1.overflow-auto
        ($ Router
           ;; Agent routes
           ($ Route {:path "/agents/:module-id/:agent-name/invocations" :component agents/invocations})
           ($ Route {:path "/agents/:module-id/:agent-name/invocations/:invoke-id" :component agents/invoke})
           ($ Route {:path "/agents/:module-id/:agent-name/evaluations" :component agents/evaluations})
           ($ Route {:path "/agents/:module-id/:agent-name/stats" :component stats/stats})
           ($ Route {:path "/agents/:module-id/:agent-name" :component agents/agent})
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
