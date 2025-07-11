(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.invocation-graph :as invocation-graph]
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]


   
   [com.rpl.agent-o-rama.ui.common :as common]))

(defui index []
  (let [{:keys [data loading?]}
        (common/use-query {:query-key ["agents"]
                           :query-url "/api/agents"})]
    (cond
      loading? ($ :div "loading...")
      (not data) ($ :div "no data")
      :else ($ :div.p-4
              (for [agent data
                    :let [url (str "/agents/" (:module-id agent) "/" (:agent-id agent))]]
                ($ :div.p-4.transition-colors.duration-150.hover:bg-gray-200.bg-gray-100.m-4  {:key url}
                  ($ wouter/Link {:href url}
                     ($ :div.flex.items-center.group 
                      ($ :div.flex-1
                          ($ :div.text-lg.font-medium.text-indigo-600.group-hover:text-indigo-800
                            ($ :div (:module-id agent) "/" (:agent-id agent)))
                          ($ :div.mt-1.text-sm.text-gray-500.group-hover:text-gray-700
                            "View agent details"))))))))))


(defui invocations []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-id]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/invocations")})
        [location navigate] (useLocation)]
    (cond
      loading? ($ :div "loading...")
      (not data) ($ :div "no data")
      :else
      ($ :div.p-4
         ($ :table.w-full
            ($ :thead.text-left ($ :tr ($ :th "invoke id") ($ :th "args") ($ :th "version") ($ :th "result")))
            ($ :tbody
               (for [invoke (:invokes data)
                     :let [url (str "/agents/" module-id "/" agent-id "/invocations/" (:root-invoke-id invoke))]]
                 ($ :tr.bg-gray-200.hover:bg-gray-300.cursor-pointer
                    {:key url
                     :onClick (fn [e]
                                (println e)
                                (. e stopPropagation)
                                (navigate url))}
                    ($ :td (:root-invoke-id invoke))
                    ($ :td (common/pp (:invoke-args invoke)))
                    ($ :td (:graph-version invoke))
                    ($ :td (common/pp (:result invoke)))))))))))

(defui mini-invocations []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-id]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/invocations")})

        [location navigate] (useLocation)]
    (cond
      loading? ($ :div "loading...")
      (not data) ($ :div "no data")
      :else
      ($ :table.w-full
         ($ :thead.text-left ($ :tr ($ :th "invoke id") ($ :th "args") ($ :th "version") ($ :th "result")))
         ($ :tbody
            (for [invoke (:invokes data)
                  :let [url (str "/agents/" module-id "/" agent-id "/invocations/" (:root-invoke-id invoke))]]
              ($ :tr.bg-gray-200.hover:bg-gray-300.cursor-pointer
                 {:key url
                  :onClick (fn [_] (navigate url))}
                 ($ :td (:root-invoke-id invoke))
                 ($ :td (common/pp (:invoke-args invoke)))
                 ($ :td (:graph-version invoke))
                 ($ :td (common/pp (:result invoke))))))
         ($ :tfoot 
            ($ :tr
               {:onClick (fn [_] (navigate (str "/agents/" module-id "/" agent-id "/invocations")))}
               ($ :th.hover:bg-gray-200.cursor-pointer {:colspan 4} 
                  "See all invocations")))))))

(defui evaluations []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Evaluations")
       ($ :div.text-gray-500 "Evaluations functionality coming soon..."))))

(defui agent-graph []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-id "graph"]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/graph")})]
    (if loading?
      "...loading"
      ($ agent-graph/graph {:initial-data data
                            :height "200px"
                            :selected-node nil
                            :set-selected-node (fn [_])
                            :fitView true}))))

(defui stats-summary [{:keys [module-id agent-id]}]
  ($ :div.p-4.flex.gap-1
     ($ wouter/Link
        {:href (str "/agents/" module-id "/" agent-id "/stats")
         :style {:flex-grow "1"}}
        ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer.relative
           ($ :div.flex.justify-between.items-start
              ($ :div
                 ($ :div.text-sm.text-gray-600.mb-2 "Last 10,000 runs")
                 ($ :div.flex.flex-row.gap-2
                    ($ :div.flex.justify-between
                       ($ :span.text-sm.text-gray-700 "Avg Tokens:")
                       ($ :span.text-sm.font-medium.text-gray-900 "1,247.3"))
                    ($ :div.flex.justify-between
                       ($ :span.text-sm.text-gray-700 "Avg Latency:")
                       ($ :span.text-sm.font-medium.text-gray-900 "342ms"))))
              ($ :div.text-gray-400.hover:text-gray-600
                 ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                    ($ :path {:fillRule "evenodd"
                              :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                              :clipRule "evenodd"}))))))))

(defui alerts [{:keys [module-id agent-id]}]
  (let [dummy-alerts [{:metric "Error Rate" :value "2.3%" :threshold "< 5%" :time-ago "2h ago"}
                      {:metric "Latency" :value "847ms" :threshold "< 500ms" :time-ago "4h ago"}
                      {:metric "Error Rate" :value "8.1%" :threshold "< 5%" :time-ago "1d ago"}]]
    ($ :div.p-4.flex.gap-1
       ($ wouter/Link
          {:href (str "/agents/" module-id "/" agent-id "/alerts")
           :style {:flex-grow "1"}}
          ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer.relative
             ($ :div.flex.justify-between.items-start
                ($ :div.w-full
                   ($ :div.text-sm.text-gray-600.mb-2 "Recent Alerts")
                   ($ :div.space-y-2
                      (for [alert dummy-alerts]
                        ($ :div.flex.justify-between.items-center.text-xs {:key (str (:metric alert) (:time-ago alert))}
                           ($ :div.flex-1
                              ($ :div.font-medium.text-red-600 (:metric alert))
                              ($ :div.text-gray-500 (str (:value alert) " (threshold: " (:threshold alert) ")")))
                           ($ :div.text-gray-400.text-right (:time-ago alert))))))
                ($ :div.text-gray-400.hover:text-gray-600.ml-2
                   ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                      ($ :path {:fillRule "evenodd"
                                :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                :clipRule "evenodd"})))))))))

(defui agent []
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        [location navigate] (useLocation)]

    ($ :div.p-4
       ($ :div.text-xl.font-semibold.mb-4 "Agent Details")
       ($ :div.flex
          ($ :div {:className "w-1/2"} ($ agent-graph))
          ($ :div {:className "w-1/2"}
             ($ stats-summary {:module-id module-id :agent-id agent-id})
             ($ alerts {:module-id module-id :agent-id agent-id})))
       ($ :div.p-4.flex.gap-1
          ($ wouter/Link
             {:href (str "/agents/" module-id "/" agent-id "/run")
              :style {:flex-grow "1"}}
             ($ :div.bg-gray-100.flex-1.p-4.hover:bg-gray-200.cursor-pointer
                "manually run agent")))
       
       ($ :div.p-4.flex.gap-1
          ($ :div
             {:style {:flex-grow "1"} }
             ($ :div.bg-gray-100.flex-1.p-4
                "invocations"
                ($ mini-invocations)))))))

(defui invoke []
  (let [{:strs [module-id agent-id invoke-id]} (js->clj (wouter/useParams))
        [use-pagination? set-use-pagination] (uix/use-state true)
        [forking-mode? set-forking-mode?] (uix/use-state false)
        ;; Only fetch initial data - no need to refetch on pagination state changes
        {:keys [data loading?]}
        (common/use-query {:query-key ["invoke-initial" module-id agent-id invoke-id use-pagination?]
                           :query-url (if use-pagination?
                                        (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id "/paginated?depth=1")
                                        (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id))})]
    (cond
      loading? ($ :div "loading...")
      (not data) ($ :div "no data")
      :else 
      ($ :div
         ;; Sticky header with all controls
         ($ :div.sticky.top-0.z-50.bg-white.border-b.border-gray-200.shadow-sm.p-6
            ($ :div.flex.justify-between.items-center
               ($ :h2.text-2xl.font-semibold.text-gray-700 "Agent Invocation Graph")
               ($ :div.flex.items-center.gap-4
                  ($ :div.flex.items-center.gap-2
                     ($ :label.text-sm.text-gray-600 "Pagination")
                     ($ :input.mr-2 {:type "checkbox"
                                     :checked use-pagination?
                                     :onChange #(set-use-pagination (not use-pagination?))})))))
         
         ;; Graph content
         ($ :div.bg-white.p-6.rounded-lg.shadow.mt-4
            ($ invocation-graph/graph {:initial-data (:invokes-map data)
                                      :api-url (when use-pagination? 
                                                 (str "/api/agents/" module-id "/" agent-id "/invocations/" invoke-id "/paginated"))
                                      :module-id module-id
                                      :agent-id agent-id
                                      :invoke-id invoke-id
                                      :forking-mode? forking-mode?
                                      :set-forking-mode? set-forking-mode?}))))))


