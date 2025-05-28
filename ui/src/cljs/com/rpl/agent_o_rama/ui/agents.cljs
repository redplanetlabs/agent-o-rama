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
        [pagination-state set-pagination-state] (uix/use-state {:depth 3 :start-node-id nil})
        {:keys [data isLoading]}
        (common/use-query {:query-key (if use-pagination?
                                        ["invoke-paginated" module-id agent-id invoke-id 
                                         (:depth pagination-state) 
                                         (:start-node-id pagination-state)]
                                        ["invoke" module-id agent-id invoke-id])
                           :query-url (if use-pagination?
                                        (str "/api/agents/" module-id "/" agent-id "/" invoke-id "/paginated"
                                             "?depth=" (:depth pagination-state)
                                             (when (:start-node-id pagination-state)
                                               (str "&start-node-id=" (:start-node-id pagination-state))))
                                        (str "/api/agents/" module-id "/" agent-id "/" invoke-id))})
        
        handle-paginate-node (uix/use-callback
                              (fn [node-id]
                                (set-pagination-state {:depth 3 :start-node-id node-id}))
                              [])]
    (cond
      isLoading ($ :div "loading...")
      (not data) ($ :div "no data")
      :else 
      ($ :div
         ($ :div.bg-white.p-6.rounded-lg.shadow
            ($ :div.flex.justify-between.items-center.mb-4
               ($ :h2.text-2xl.font-semibold.text-gray-700 "Invocation Details")
               ($ :div.flex.items-center.gap-2
                  ($ :label.text-sm.text-gray-600 "Pagination:")
                  ($ :input.mr-2 {:type "checkbox"
                                  :checked use-pagination?
                                  :onChange #(set-use-pagination (not use-pagination?))})
                  (when (and use-pagination? 
                             (:start-node-id pagination-state) 
                             (not= (:start-node-id pagination-state) "root"))
                    ($ :button.ml-4.px-4.py-2.bg-gray-200.text-gray-700.rounded.hover:bg-gray-300
                       {:onClick #(set-pagination-state {:depth 3 :start-node-id nil})}
                       "← Back to root"))))
            ($ graph/graph {:data (:invokes-map data)
                            :on-paginate-node (when use-pagination? handle-paginate-node)}))))))


