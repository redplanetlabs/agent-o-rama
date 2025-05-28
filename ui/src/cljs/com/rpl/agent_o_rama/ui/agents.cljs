(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.graph :as graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter]


   
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


(defui agent []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data isLoading]}
        (common/use-query {:query-key ["agent" module-id agent-id]
                           :query-url (str "/api/agents/" module-id "/" agent-id)})]
    
    (cond
      isLoading ($ :div "loading...")
      (not data) ($ :div "no data")
      :else 
      (let [invokes (:invokes data)]
          ($ :div
             (for [invoke invokes
                     :let [url (str "/agents/" module-id "/" agent-id "/" (:root-invoke-id invoke))]]
             ($ wouter/Link {:href url :key url}
               ($ :div.bg-white.p-6.rounded-lg.shadow {:class "hover:bg-gray-100"}
                  ($ :div.flex.justify-between.items-center.mb-2
                     ($ :div.text-indigo-600.font-medium.text-sm
                        "Explore Invocation"))
                  ($ :pre.text-xs.bg-gray-100.p-2.rounded.overflow-x-auto (common/pp invoke))))))))))

(defui invoke []
  (let [{:strs [module-id agent-id invoke-id]} (js->clj (wouter/useParams))
        [use-pagination? set-use-pagination] (uix/use-state true)
        ;; Only fetch initial data - no need to refetch on pagination state changes
        {:keys [data isLoading]}
        (common/use-query {:query-key ["invoke-initial" module-id agent-id invoke-id use-pagination?]
                           :query-url (if use-pagination?
                                       (str "/api/agents/" module-id "/" agent-id "/" invoke-id "/paginated?depth=3")
                                       (str "/api/agents/" module-id "/" agent-id "/" invoke-id))})]
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
                                     (str "/api/agents/" module-id "/" agent-id "/" invoke-id "/paginated"))
                           :module-id module-id
                           :agent-id agent-id
                           :invoke-id invoke-id}))))))


