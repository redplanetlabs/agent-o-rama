(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.graph :as graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]


   
   [com.rpl.agent-o-rama.ui.common :as common]))

(defui index []
  (let [{:keys [data isLoading]}
        (common/use-query {:query-key ["agents"]
                           :query-url "/api/agents"})]
    (cond
      isLoading ($ :div "loading...")
      (not data) ($ :div "no data")
      :else ($ :div
              (for [agent data
                    :let [url (str "/agents/" (:module-id agent) "/" (:agent-id agent))]]
                ($ :div.py-4.transition-colors.duration-150.hover:bg-gray-50  {:key url}
                  ($ wouter/Link {:href url}
                     ($ :div.flex.items-center.group 
                      ($ :div.flex-1
                          ($ :div.text-lg.font-medium.text-indigo-600.group-hover:text-indigo-800
                            ($ :div (:module-id agent) "/" (:agent-id agent)))
                          ($ :div.mt-1.text-sm.text-gray-500.group-hover:text-gray-700
                            "View agent details"))))))))))


(defui invocations []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data isLoading]}
        (common/use-query {:query-key ["agent" module-id agent-id]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/invocations")})]
    (cond
      isLoading ($ :div "loading...")
      (not data) ($ :div "no data")
      :else 
      (for [invoke (:invokes data)
            :let [url (str "/agents/" module-id "/" agent-id "/invocations/" (:root-invoke-id invoke))]]
        ($ wouter/Link {:href url :key url}
           ($ :div.bg-white.p-6 {:class "hover:bg-gray-100"}
              ($ :div.flex.justify-between.items-center.mb-2
                 ($ :div.text-indigo-600.font-medium.text-sm
                    "Explore Invocation"))
              ($ :pre.text-xs.bg-gray-100.p-2.rounded.overflow-x-auto (common/pp invoke))))))))

(defui evaluations []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Evaluations")
       ($ :div.text-gray-500 "Evaluations functionality coming soon..."))))

(defui agent []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        [location navigate] (useLocation)]
    
    ($ :div.p-4
       ($ :div.text-xl.font-semibold.mb-4 "Agent Details")
       ($ :div.p-4.flex.gap-1
          ($ wouter/Link
             {:href (str "/agents/" module-id "/" agent-id "/run")
              :key tab-label
              :style {:flex-grow "1"}}
             ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer
                "manually run agent")))
       
       ($ :div.p-4.flex.gap-1
          ($ wouter/Link
             {:href (str "/agents/" module-id "/" agent-id "/invocations")
              :key tab-label
              :style {:flex-grow "1"}}
             ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer
                "invocations")))
       
       ($ :div.p-4.flex.gap-1
          ($ wouter/Link
             {:href (str "/agents/" module-id "/" agent-id "/stats")
              :key tab-label
              :style {:flex-grow "1"}}
             ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer
                "stats summary")))
       
       ($ :div.p-4.flex.gap-1
          ($ wouter/Link
             {:href (str "/agents/" module-id "/" agent-id "/alerts")
              :key tab-label
              :style {:flex-grow "1"}}
             ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer
                "alerts"))))))

(defui invoke []
  (let [{:strs [module-id agent-id invoke-id]} (js->clj (wouter/useParams))
        [use-pagination? set-use-pagination] (uix/use-state true)
        ;; Only fetch initial data - no need to refetch on pagination state changes
        {:keys [data isLoading]}
        (common/use-query {:query-key ["invoke-initial" module-id agent-id invoke-id use-pagination?]
                           :query-url (if use-pagination?
                                        (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id "/paginated?depth=1")
                                        (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id))})]
    (cond
      isLoading ($ :div "loading...")
      (not data) ($ :div "no data")
      :else 
      ($ :div
         ($ :div.bg-white.p-6.rounded-lg.shadow
            ($ :div.flex.justify-between.items-center.mb-4
               ($ :h2.text-2xl.font-semibold.text-gray-700 "Invocation Details")
               ($ :div.flex.items-center.gap-2
                  ($ :label.text-sm.text-gray-600 "Pagination")
                  ($ :input.mr-2 {:type "checkbox"
                                  :checked use-pagination?
                                  :onChange #(set-use-pagination (not use-pagination?))})))
            ($ graph/graph {:initial-data (:invokes-map data)
                            :api-url (when use-pagination? 
                                       (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id "/paginated"))
                            :module-id module-id
                            :agent-id agent-id
                            :invoke-id invoke-id}))))))


