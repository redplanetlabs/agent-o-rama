(ns com.rpl.agent-o-rama.ui.agents
  (:require
   [com.rpl.agent-o-rama.ui.invocation-page :as invocation-page]
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]

   [uix.core :as uix :refer [defui defhook $]]
   [reitit.frontend.easy :as rfe]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]))

(defui result-badge [{:keys [result human-request?]}]
  (cond
    human-request?
    ($ :span.px-2.py-1.bg-amber-100.text-amber-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
       "🙋 Needs input")
    (nil? result)
    ($ :span.px-2.py-1.bg-blue-100.text-blue-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
       ($ common/spinner {:size :small})
       "Pending")
    (:failure? result)
    ($ :span.px-2.py-1.bg-red-100.text-red-800.rounded-full.text-xs.font-medium "Failed")
    :else
    ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Success")))

(defui invocation-row [{:keys [invoke module-id agent-name on-click]}]
  (let [task-id (:task-id invoke)
        agent-id (:agent-id invoke)
        start-time (:start-time-millis invoke)
        href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" task-id "-" agent-id)
        invoke-id (str task-id "-" agent-id)
        args-json (common/to-json (:invoke-args invoke))]
    ($ :tr.hover:bg-gray-50.transition-colors.duration-150
       {:key href}
       ($ :td.px-4.py-3
          ($ :button.px-3.py-1.bg-blue-100.text-blue-700.rounded.text-xs.font-medium.hover:bg-blue-200.transition-colors.duration-150.cursor-pointer
             {:onClick (fn [e]
                         (. e stopPropagation)
                         (rfe/push-state :agent/invocation-detail {:module-id module-id :agent-name agent-name :invoke-id invoke-id}))}
             "View trace"))
       ($ :td.px-4.py-3.text-sm.text-gray-600.font-mono
          {:title (common/format-timestamp start-time)}
          (common/format-relative-time start-time))
       ($ :td.px-4.py-3.max-w-md.cursor-pointer.hover:bg-gray-100.rounded
          {:onClick (fn [e]
                      (. e stopPropagation)
                      (state/dispatch [:modal/show :arguments-detail
                                       {:title "Invocation Arguments"
                                        :component ($ common/ContentDetailModal
                                                      {:title "Invocation Arguments"
                                                       :content args-json})}]))}
          ($ :div.truncate.text-gray-900.font-mono
             args-json))
       ($ :td.px-4.py-3.font-mono.text-gray-600 (:graph-version invoke))
       ($ :td.px-4.py-3.text-sm
          ($ result-badge {:result (:result invoke)
                           :human-request? (:human-request? invoke)})))))

(defui index []
  (let [{:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:agents]
                                  :sente-event [:agents/get-all]
                                  :refetch-interval-ms 2000})]

    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading agents via Sente..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading agents: " error))
      (empty? data) ($ :div.flex.justify-center.items-center.py-8
                       ($ :div.text-gray-500 "No agents found"))
      :else ($ :div.p-4
               ($ :div {:className "inline-block bg-white shadow sm:rounded-md"}
                  ($ :table {:className "divide-y divide-gray-200"}
                     ($ :thead {:className (:thead common/table-classes)}
                        ($ :tr
                           ($ :th {:className (:th common/table-classes)} "Module")
                           ($ :th {:className (:th common/table-classes)} "Agent")))
                     ($ :tbody
                        (let [sorted-agents (sort-by
                                             (fn [agent]
                                               (let [module-name (:module-id agent)
                                                     decoded-module (common/url-decode module-name)
                                                     agent-name (:agent-name agent)
                                                     decoded-agent (common/url-decode agent-name)]
                                                  ;; Sort by: 1) module name, 2) underscore-prefixed agents last, 3) agent name
                                                 [decoded-module (str/starts-with? decoded-agent "_") decoded-agent]))
                                             data)]
                          (into []
                                (for [agent sorted-agents
                                      :let [module (common/url-decode (:module-id agent))
                                            agent-name (common/url-decode (:agent-name agent))
                                            href (str "/agents/" (common/url-encode (:module-id agent)) "/agent/" (common/url-encode (:agent-name agent)))]]
                                  ($ :tr {:key href :className "hover:bg-gray-50 cursor-pointer"
                                          :onClick (fn [_]
                                                     (rfe/push-state :agent/detail
                                                                     {:module-id (:module-id agent)
                                                                      :agent-name (:agent-name agent)}))}
                                     ($ :td {:className (:td common/table-classes)} module)
                                     ($ :td {:className (:td common/table-classes)} agent-name))))))))))))

(defui invocations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])

        ;; Subscribe to invocations state from app-db
        all-invokes (state/use-sub [:invocations :all-invokes])
        pagination-params (state/use-sub [:invocations :pagination-params])
        has-more? (state/use-sub [:invocations :has-more?])
        loading? (state/use-sub [:invocations :loading?])
        connected? (state/use-sub [:sente :connected?])

        ;; Fetch function that handles the entire flow - memoized with use-callback
        fetch-invocations (uix/use-callback
                           (fn [pagination append?]
                             (state/dispatch [:invocations/set-loading true])
                             (sente/request!
                              [:invocations/get-page {:module-id module-id
                                                      :agent-name agent-name
                                                      :pagination pagination}]
                              5000
                              (fn [reply]
                                (state/dispatch [:invocations/set-loading false])
                                (when (:success reply)
                                  (let [data (:data reply)
                                        new-invokes (:agent-invokes data)
                                        new-pagination (:pagination-params data)
                                        has-more? (and new-pagination
                                                       (not (empty? new-pagination))
                                                       (some (fn [[_ item-id]] (not (nil? item-id)))
                                                             new-pagination))]
                                    (if append?
                                      (state/dispatch [:invocations/append new-invokes])
                                      (state/dispatch [:db/set-value [:invocations :all-invokes] new-invokes]))
                                    (state/dispatch [:invocations/set-pagination
                                                     {:pagination-params (when has-more? new-pagination)
                                                      :has-more? has-more?}]))))))
                            ;; Dependencies for use-callback - only recreate when module-id or agent-name changes
                           [module-id agent-name])

        ;; Initial load - fetch first page when connected
        _ (uix/use-effect
           (fn []
             (when connected?
               (println "🔄 Initial load for invocations (connected)")
               (state/dispatch [:invocations/reset])
               (fetch-invocations {} false))
             (constantly nil))
           ;; Simplified dependencies - fetch-invocations is now stable and will change when module-id/agent-name change
           [fetch-invocations connected?])

        load-more (fn []
                    (when (and has-more? (not loading?) pagination-params)
                      (println "🔄 Loading more with params:" pagination-params)
                      (fetch-invocations pagination-params true)))]

    (cond
      ;; Still loading initial data
      (and loading? (empty? all-invokes)) ($ :div.flex.justify-center.items-center.py-8
                                             ($ :div.text-gray-500 "Loading invocations via Sente..."))
      ;; No data returned
      (and (not loading?) (empty? all-invokes)) ($ :div.flex.justify-center.items-center.py-8
                                                   ($ :div.text-gray-500 "No invocations found"))
      :else
      ($ :div.p-4
         ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
            ($ :table.w-full.text-sm
               ($ :thead.bg-gray-50.border-b.border-gray-200
                  ($ :tr
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Trace")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Start Time")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")))
               ($ :tbody.divide-y.divide-gray-200
                  (for [invoke all-invokes]
                    ($ invocation-row {:key (str (:task-id invoke) "-" (:agent-id invoke))
                                       :invoke invoke
                                       :module-id module-id
                                       :agent-name agent-name
                                       :on-click (fn [url] (set! (.-href (.-location js/window)) url))})))

            ;; Load More button
               (when has-more?
                 ($ :tfoot.bg-gray-50.border-t.border-gray-200
                    ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                       {:onClick (when (not loading?) load-more)}
                       ($ :td.px-4.py-3.cursor-pointer {:colSpan 5}
                          ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                             ($ :span.mr-2.text-sm.font-medium (if loading? "Loading..." "Load More"))
                             (when (not loading?)
                               ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                                  ($ :path {:fillRule "evenodd"
                                            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                            :clipRule "evenodd"}))))))))))))))

(defui mini-invocations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        {:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:mini-invocations module-id agent-name]
                                  :sente-event [:invocations/get-page {:module-id module-id
                                                                       :agent-name agent-name
                                                                       :pagination {}}]
                                  :refetch-interval-ms 2000})]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading invocations via Sente..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading invocations: " error))
      (not data) ($ :div.flex.justify-center.items-center.py-8
                    ($ :div.text-gray-500 "No invocations found"))
      :else
      ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-hidden.shadow-sm
         ($ :table.w-full.text-sm
            ($ :thead.bg-gray-50.border-b.border-gray-200
               ($ :tr
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Trace")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Start Time")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                  ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")))
            ($ :tbody.divide-y.divide-gray-200
               (for [invoke (:agent-invokes data)]
                 ($ invocation-row {:key (str (:task-id invoke) "-" (:agent-id invoke))
                                    :invoke invoke
                                    :module-id module-id
                                    :agent-name agent-name
                                    :on-click (fn [url] (set! (.-href (.-location js/window)) url))})))
            ($ :tfoot.bg-gray-50.border-t.border-gray-200
               ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                  {:onClick (fn [_]
                              (set! (.-href (.-location js/window))
                                    (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations")))}
                  ($ :td.px-4.py-3.cursor-pointer {:colSpan 5}
                     ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                        ($ :span.mr-2.text-sm.font-medium "View all invocations")
                        ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                           ($ :path {:fillRule "evenodd"
                                     :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                     :clipRule "evenodd"})))))))))))

(defui evaluations []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])]
    ($ :div
       ($ :h2.text-xl.font-semibold.mb-4 "Evaluations")
       ($ :div.text-gray-500 "Evaluations functionality coming soon..."))))

(defui agent-graph []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])
        {:keys [data loading? error]}
        (queries/use-sente-query {:query-key [:graph module-id agent-name]
                                  :sente-event [:invocations/get-graph {:module-id module-id
                                                                        :agent-name agent-name}]
                                  :refetch-interval-ms 2000})]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading graph via Sente..."))
      error ($ :div.flex.justify-center.items-center.py-8
               ($ :div.text-red-500 "Error loading graph: " error))
      :else ($ agent-graph/graph {:initial-data data
                                  :height "500px"
                                  :selected-node nil
                                  :set-selected-node (fn [_])}))))

(defui stats-summary [{:keys [module-id agent-name]}]
  ($ :div.p-4.flex.gap-1
     ($ :a
        {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/stats")
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
       ($ :a
          {:href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/alerts")
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
  (let [;; Subscribe to state from app-db
        manual-run-state (state/use-sub [:ui :manual-run module-id agent-name])
        args (or (:args manual-run-state) "")
        metadata-args (or (:metadata-args manual-run-state) "")
        loading? (or (:loading? manual-run-state) false)
        error-msg (:error-msg manual-run-state)

        ;; State update helper
        update-field (fn [field value]
                       (state/dispatch [:db/set-value [:ui :manual-run module-id agent-name field] value]))

        handle-submit (fn [e]
                        (.preventDefault e)
                        (update-field :error-msg nil)
                        (update-field :loading? true)

                        ;; Parse both arguments and metadata
                        (let [parsed-args (try (js->clj (js/JSON.parse args)) (catch js/Error e nil))
                              parsed-metadata (try (if (str/blank? metadata-args) {} (js->clj (js/JSON.parse metadata-args))) (catch js/Error e nil))]
                          (cond
                            (not parsed-args)
                            (do (update-field :loading? false)
                                (update-field :error-msg "Error: Invalid JSON format for arguments"))

                            (not parsed-metadata)
                            (do (update-field :loading? false)
                                (update-field :error-msg "Error: Invalid JSON format for metadata"))

                            :else
                            ;; Make Sente request with new metadata field
                            (sente/request!
                             [:invocations/run-agent {:module-id module-id
                                                      :agent-name agent-name
                                                      :args parsed-args
                                                      :metadata parsed-metadata}]
                             5000
                             (fn [reply]
                               (update-field :loading? false)
                               (if (:success reply)
                                 (let [data (:data reply)
                                       trace-url (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" (:task-id data) "-" (:invoke-id data))]
                                   (update-field :args "") ;; Clear args on success
                                   (update-field :metadata-args "") ;; Clear metadata on success
                                   (rfe/push-state :agent/invocation-detail {:module-id module-id :agent-name agent-name :invoke-id (str (:task-id data) "-" (:invoke-id data))}))
                                 (update-field :error-msg (str "Error: " (or (:error reply) "Unknown error")))))))))]

    ($ :div.bg-white.rounded-md.border.border-gray-200.shadow-sm.flex-1.p-6
       ($ :form {:onSubmit handle-submit}
          ($ :div.text-sm.font-medium.text-gray-600.mb-4 "Manually Run Agent")
          ($ :div.flex.gap-3.justify-between
             ($ :textarea.flex-1.p-3.border.border-gray-300.rounded-md.text-sm.focus:ring-2.focus:ring-blue-500.focus:border-blue-500.transition-colors.duration-150
                {:placeholder "[arg1, arg2, arg3, ...] (json)"
                 :value args
                 :onChange #(update-field :args (.. % -target -value))
                 :rows 3
                 :disabled loading?})
             ($ :textarea.flex-1.p-3.border.border-gray-300.rounded-md.text-sm.focus:ring-2.focus:ring-blue-500.focus:border-blue-500.transition-colors.duration-150
                {:placeholder "Metadata (JSON map, optional)"
                 :value metadata-args
                 :onChange #(update-field :metadata-args (.. % -target -value))
                 :rows 3
                 :disabled loading?})
             ($ :button
                {:type "submit"
                 :disabled loading?
                 :className (if loading?
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-not-allowed transition-colors duration-150 bg-gray-400"
                              "w-32 h-20 text-white px-4 rounded-md focus:ring-2 focus:ring-blue-500 focus:ring-offset-2 text-sm font-semibold cursor-pointer transition-colors duration-150 bg-blue-600 hover:bg-blue-700")}
                (if loading? "Running..." "Submit"))))

       ;; Show errors only (success navigates to trace)
       (when error-msg
         ($ :div.mt-4.p-3.rounded-md.bg-red-50.border.border-red-200
            ($ :div.text-red-700.text-sm error-msg))))))

(defui agent []
  (let [{:keys [module-id agent-name]} (state/use-sub [:route :path-params])]

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
  (let [{:keys [module-id agent-name invoke-id]} (state/use-sub [:route :path-params])]

    ($ :div
       ;; Sticky header with all controls
       ($ :div.sticky.top-0.z-50.bg-white.border-b.border-gray-200.shadow-sm.p-6
          ($ :div.flex.justify-between.items-center
             ($ :h2.text-2xl.font-semibold.text-gray-700 "Agent Invocation Graph")))

       ;; Graph content
       ($ :div.bg-white.p-6.rounded-lg.shadow.mt-4
          ($ invocation-page/invocation-page)))))