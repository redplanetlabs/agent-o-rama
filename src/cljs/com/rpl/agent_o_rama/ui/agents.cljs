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
      ;; Still loading initial data
      loading? ($ :div.flex.justify-center.items-center.py-8
                     ($ :div.text-gray-500 "Loading agents..."))
      ;; Request errored or returned nil
      (not data) ($ :div.flex.justify-center.items-center.py-8
                    ($ :div.text-gray-500 "Unable to retrieve agents"))
      ;; No agents returned from the API
      (empty? data) ($ :div.flex.justify-center.items-center.py-8
                      ($ :div.text-gray-500 "No agents found"))
      :else ($ :div.p-4
              (for [agent data
                    :let [url (str "/agents/" (:module-id agent) "/" (:agent-name agent))]]
                ($ :div.p-4.transition-colors.duration-150.hover:bg-gray-200.bg-gray-100.m-4  {:key url}
                  ($ wouter/Link {:href url}
                     ($ :div.flex.items-center.group 
                      ($ :div.flex-1
                          ($ :div.text-lg.font-medium.text-indigo-600.group-hover:text-indigo-800
                            ($ :div (common/url-decode (:module-id agent)) ":" (common/url-decode (:agent-name agent))))
                          ($ :div.mt-1.text-sm.text-gray-500.group-hover:text-gray-700
                            "View agent details"))))))))))


(defui invocations []
  (let [{:strs [module-id agent-name]} (js->clj (wouter/useParams))
        [requested-pagination-params set-requested-pagination-params] (uix/use-state {})
        [all-invokes set-all-invokes] (uix/use-state [])
        [has-more? set-has-more?] (uix/use-state true)
        [next-pagination-params set-next-pagination-params] (uix/use-state {})
        
        ;; Build query URL with pagination params
        query-url (let [base-url (str "/api/agents/" module-id "/" agent-name "/invocations")]
                    (if (empty? requested-pagination-params)
                      base-url
                      (let [valid-params (filter (fn [[task-id item-id]] (not (nil? item-id))) requested-pagination-params)
                            params-str (->> valid-params
                                           (map (fn [[task-id item-id]] (str task-id "=" item-id)))
                                           (clojure.string/join "&"))]
                        (if (empty? params-str)
                          base-url
                          (str base-url "?" params-str)))))
        
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-name requested-pagination-params]
                           :query-url query-url})
        
        [location navigate] (useLocation)
        
        ;; Update accumulated data when new data arrives
        _ (uix/use-effect
           (fn []
             (when data
               (let [new-invokes (:agent-invokes data)
                     new-pagination (:pagination-params data)]
                 (if (empty? requested-pagination-params)
                   ;; First load - replace all data
                   (set-all-invokes new-invokes)
                   ;; Subsequent loads - append new data
                   (set-all-invokes (fn [current] (concat current new-invokes))))
                 
                 ;; Store next pagination params for the "Load More" button
                 (let [has-valid-pagination? (and new-pagination 
                                                  (not (empty? new-pagination))
                                                  (some (fn [[_ item-id]] (not (nil? item-id))) new-pagination))]
                   (if has-valid-pagination?
                     (do
                       (set-next-pagination-params new-pagination)
                       (set-has-more? true))
                     (set-has-more? false))))))
           [data requested-pagination-params])
        
        load-more (fn []
                    (when (and has-more? (not loading?))
                      ;; Trigger next page by updating requested pagination params
                      (set-requested-pagination-params next-pagination-params)))]
    
    (cond
      (and loading? (empty? all-invokes)) ($ :div.flex.justify-center.items-center.py-8
                                            ($ :div.text-gray-500 "Loading invocations..."))
      (and (not data) (empty? all-invokes)) ($ :div.flex.justify-center.items-center.py-8
                                              ($ :div.text-gray-500 "No invocations found"))
      :else
      ($ :div.p-4
         ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
            ($ :table.w-full.text-sm
               ($ :thead.bg-gray-50.border-b.border-gray-200
                  ($ :tr
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Invoke ID")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")))
               ($ :tbody.divide-y.divide-gray-200
                  (for [invoke all-invokes
                        :let [task-id (:task-id invoke)
                              agent-id (:agent-id invoke)
                              url (str "/agents/" module-id "/" agent-name "/invocations/" task-id "-" agent-id)]]
                    ($ :tr.hover:bg-gray-50.transition-colors.duration-150.cursor-pointer
                       {:key url
                        :onClick (fn [e]
                                   (println e)
                                   (. e stopPropagation)
                                   (navigate url))}
                       ($ :td.px-4.py-3.font-mono.text-blue-600.font-medium (str task-id "-" agent-id))
                       ($ :td.px-4.py-3.max-w-xs
                          ($ :div.truncate.text-gray-900
                             (common/pp (:invoke-args invoke))))
                       ($ :td.px-4.py-3.font-mono.text-gray-600 (:graph-version invoke))
                       ($ :td.px-4.py-3.text-sm
                          (let [result (:result invoke)]
                            (if (:failure? result)
                              ($ :span.px-2.py-1.bg-red-100.text-red-800.rounded-full.text-xs.font-medium "Failed")
                              ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Success")))))))
            
            ;; Load More button
            (when has-more?
              ($ :tfoot.bg-gray-50.border-t.border-gray-200
                 ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                    {:onClick (when (not loading?) load-more)}
                    ($ :td.px-4.py-3.cursor-pointer {:colSpan 4}
                       ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                          ($ :span.mr-2.text-sm.font-medium (if loading? "Loading..." "Load More"))
                          (when (not loading?)
                            ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                               ($ :path {:fillRule "evenodd"
                                         :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                         :clipRule "evenodd"}))))))))))))))

(defui mini-invocations []
  (let [{:strs [module-id agent-name]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-name]
                           :query-url (str "/api/agents/" module-id "/" agent-name "/invocations")})

        [location navigate] (useLocation)]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                 ($ :div.text-gray-500 "Loading invocations..."))
      (not data) ($ :div.flex.justify-center.items-center.py-8
                   ($ :div.text-gray-500 "No invocations found"))
      :else
      ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
         ($ :table.w-full.text-sm
            ($ :thead.bg-gray-50.border-b.border-gray-200
               ($ :tr
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Invoke ID")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")))
            ($ :tbody.divide-y.divide-gray-200
               (for [invoke (:agent-invokes data)
                     :let [task-id (:task-id invoke)
                           agent-id (:agent-id invoke)
                           url (str "/agents/" module-id "/" agent-name "/invocations/" task-id "-" agent-id)]]
                 ($ :tr.hover:bg-gray-50.transition-colors.duration-150.cursor-pointer
                    {:key url
                     :onClick (fn [_] (navigate url))}
                    ($ :td.px-4.py-3.font-mono.text-blue-600.font-medium (str task-id "-" agent-id))
                    ($ :td.px-4.py-3.max-w-xs
                       ($ :div.truncate.text-gray-900 
                          (common/pp (:invoke-args invoke))))
                    ($ :td.px-4.py-3.font-mono.text-gray-600 (:graph-version invoke))
                    ($ :td.px-4.py-3.text-sm
                       (let [result (:result invoke)]
                         (if (:failure? result)
                           ($ :span.px-2.py-1.bg-red-100.text-red-800.rounded-full.text-xs.font-medium "Failed")
                           ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Success")))))))
            ($ :tfoot.bg-gray-50.border-t.border-gray-200
               ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                  {:onClick (fn [_] (navigate (str "/agents/" module-id "/" agent-name "/invocations")))}
                  ($ :td.px-4.py-3.cursor-pointer {:colSpan 4}
                     ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                        ($ :span.mr-2.text-sm.font-medium "View all invocations")
                        ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                           ($ :path {:fillRule "evenodd"
                                     :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                     :clipRule "evenodd"})))))))))))

(defui evaluations []
  (let [{:strs [module-id agent-name]} (js->clj (wouter/useParams))]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Evaluations")
       ($ :div.text-gray-500 "Evaluations functionality coming soon..."))))

(defui agent-graph []
  (let [{:strs [module-id agent-name]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-name "graph"]
                           :query-url (str "/api/agents/" module-id "/" agent-name "/graph")})]
    (if loading?
      "...loading"
      ($ agent-graph/graph {:initial-data data
                            :height "500px"
                            :selected-node nil
                            :set-selected-node (fn [_])}))))

(defui stats-summary [{:keys [module-id agent-name]}]
  ($ :div.p-4.flex.gap-1
     ($ wouter/Link
        {:href (str "/agents/" module-id "/" agent-name "/stats")
         :style {:flex-grow "1"}}
        ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6.hover:shadow-md.transition-shadow.duration-150.cursor-pointer.relative
           ($ :div.flex.justify-between.items-start
              ($ :div
                 ($ :div.text-sm.font-medium.text-gray-600.mb-3 "Last 10,000 runs")
                 ($ :div.flex.flex-row.gap-4
                    ($ :div.flex.flex-col
                       ($ :span.text-xs.text-gray-500.uppercase.tracking-wide "Avg Tokens")
                       ($ :span.text-lg.font-semibold.text-gray-900 "1,247.3"))
                    ($ :div.flex.flex-col
                       ($ :span.text-xs.text-gray-500.uppercase.tracking-wide "Avg Latency")
                       ($ :span.text-lg.font-semibold.text-gray-900 "342ms"))))
              ($ :div.text-gray-400.hover:text-gray-600.transition-colors.duration-150
                 ($ :svg.w-5.h-5 {:viewBox "0 0 20 20" :fill "currentColor"}
                    ($ :path {:fillRule "evenodd"
                              :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                              :clipRule "evenodd"}))))))))

(defui alerts [{:keys [module-id agent-name]}]
  (let [dummy-alerts [{:metric "Error Rate" :value "2.3%" :threshold "< 5%" :time-ago "2h ago"}
                      {:metric "Latency" :value "847ms" :threshold "< 500ms" :time-ago "4h ago"}
                      {:metric "Error Rate" :value "8.1%" :threshold "< 5%" :time-ago "1d ago"}]]
    ($ :div.p-4.flex.gap-1
       ($ wouter/Link
          {:href (str "/agents/" module-id "/" agent-name "/alerts")
           :style {:flex-grow "1"}}
          ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6.hover:shadow-md.transition-shadow.duration-150.cursor-pointer.relative
             ($ :div.flex.justify-between.items-start
                ($ :div.w-full
                   ($ :div.text-sm.font-medium.text-gray-600.mb-3 "Recent Alerts")
                   ($ :div.space-y-3
                      (for [alert dummy-alerts]
                        ($ :div.flex.justify-between.items-center.text-sm.pb-2.border-b.border-gray-100.last:border-b-0.last:pb-0 {:key (str (:metric alert) (:time-ago alert))}
                           ($ :div.flex-1
                              ($ :div.font-semibold.text-red-600 (:metric alert))
                              ($ :div.text-xs.text-gray-500.mt-1 (str (:value alert) " (threshold: " (:threshold alert) ")")))
                           ($ :div.text-xs.text-gray-400.text-right.ml-3 (:time-ago alert))))))
                ($ :div.text-gray-400.hover:text-gray-600.transition-colors.duration-150.ml-2
                   ($ :svg.w-5.h-5 {:viewBox "0 0 20 20" :fill "currentColor"}
                      ($ :path {:fillRule "evenodd"
                                :d "M7.293 14.707a1 1 0 010-1.414L10.586 10 7.293 6.707a1 1 0 011.414-1.414l4 4a1 1 0 010 1.414l-4 4a1 1 0 01-1.414 0z"
                                :clipRule "evenodd"})))))))))

(defui manual-run [{:keys [module-id agent-name]}]
  (let [[args set-args] (uix/use-state "")
        [result set-result] (uix/use-state nil)
        [error-msg set-error-msg] (uix/use-state nil)
        [location navigate] (useLocation)
        
        run-agent (common/use-mutation 
                   {:mutation-fn (fn [variables]
                                   (let [parsed-args (try
                                                       (js/JSON.parse variables)
                                                       (catch js/Error e
                                                         (throw (js/Error. "Invalid JSON format"))))]
                                     (common/post (str "/api/agents/" module-id "/" agent-name "/invocations")
                                                  {:args parsed-args})))
                    :on-success (fn [data]
                                  (set-error-msg nil)
                                  ;; Navigate to the trace instead of showing result
                                  (let [trace-url (str "/agents/" module-id "/" agent-name "/invocations/" 
                                                       (:task-id data) "-" (:invoke-id data))]
                                    (navigate trace-url)))
                    :on-error (fn [error]
                                (set-error-msg (str "Error: " (or (.-message error) "Unknown error")))
                                (set-result nil))})
        
        handle-submit (fn [e]
                        (.preventDefault e)
                        (set-error-msg nil)
                        (set-result nil)
                        ((:mutate run-agent) args))]
    
    ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6
       ($ :form {:onSubmit handle-submit}
          ($ :div.text-sm.font-medium.text-gray-600.mb-4 "Manually Run Agent")
          ($ :div.flex.gap-3.justify-between
             ($ :textarea.flex-1.p-3.border.border-gray-300.rounded-md.text-sm.focus:ring-2.focus:ring-blue-500.focus:border-blue-500.transition-colors.duration-150
                {:placeholder "[arg1, arg2, arg3, ...] (json)"
                 :value args
                 :onChange #(set-args (.. % -target -value))
                 :rows 3
                 :disabled (:loading? run-agent)})
             ($ :button
                {:type "submit"
                 :disabled (:loading? run-agent)
                 :className (if (:loading? run-agent)
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-not-allowed transition-colors duration-150 bg-gray-400"
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-pointer transition-colors duration-150 bg-blue-600 hover:bg-blue-700")}
                (if (:loading? run-agent) "Running..." "Submit"))))
       
       ;; Show errors only (success navigates to trace)
       (when error-msg
         ($ :div.mt-4.p-3.rounded-md.bg-red-50.border.border-red-200
            ($ :div.text-red-700.text-sm error-msg))))))

(defui agent []
  (let [{:strs [module-id agent-name]} (js->clj (wouter/useParams))
        [location navigate] (useLocation)]

    ($ :div.p-4
       ($ :div.text-xl.font-semibold.mb-4 "Agent Details")
       ($ :div.flex
          ($ :div {:className "w-1/2"} ($ agent-graph))
          ($ :div {:className "w-1/2"}
             ($ stats-summary {:module-id module-id :agent-name agent-name})
             ($ alerts {:module-id module-id :agent-name agent-name})))
       ($ :div.p-4.flex.gap-1
          ($ :div
             {:style {:flex-grow "1"}}
             ($ manual-run {:module-id module-id :agent-name agent-name})))
       
       ($ :div.p-4
          ($ mini-invocations)))))

(defui invoke []
  (let [{:strs [module-id agent-name invoke-id]} (js->clj (wouter/useParams))]
    
    ($ :div
       ;; Sticky header with all controls
       ($ :div.sticky.top-0.z-50.bg-white.border-b.border-gray-200.shadow-sm.p-6
          ($ :div.flex.justify-between.items-center
             ($ :h2.text-2xl.font-semibold.text-gray-700 "Agent Invocation Graph")))
       
       ;; Graph content
       ($ :div.bg-white.p-6.rounded-lg.shadow.mt-4
          ($ invocation-graph/graph {:module-id module-id
                                     :agent-name agent-name
                                     :invoke-id invoke-id})))))


