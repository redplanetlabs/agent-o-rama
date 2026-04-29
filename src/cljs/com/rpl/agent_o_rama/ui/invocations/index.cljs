(ns com.rpl.agent-o-rama.ui.invocations.index
  (:require
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [reitit.frontend.easy :as rfe]
   [re-frame.query :as rfq]
   [re-frame.core :as rf]

   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as rpc-invocations]
   [com.rpl.agent-o-rama.ui.invocations.filters :as inv-filters]
   [clojure.string :as str]))

(defui result-badge
  [{:keys [status human-request?]}]
  (cond
    human-request?
    ($
     :span.px-2.py-1.bg-amber-100.text-amber-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
     "🙋 Needs input")

    (= status :pending)
    ($
     :span.px-2.py-1.bg-blue-100.text-blue-800.rounded-full.text-xs.font-medium.inline-flex.items-center.gap-1
     ($ common/spinner {:size :small})
     "Pending")

    (= status :failure)
    ($ :span.px-2.py-1.bg-red-100.text-red-800.rounded-full.text-xs.font-medium "Failed")

    :else
    ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Success")))

(defn- feedback-cell-value [invoke metric-name]
  (let [m (:feedback-metric-values invoke)
        v (or (get m metric-name)
              (when m (get m (keyword metric-name))))]
    (if (nil? v) "-" (str v))))

(defui invocation-row [{:keys [invoke module-id agent-name on-click feedback-metric-names]}]
  (let [task-id (:task-id invoke)
        agent-id (:agent-id invoke)
        start-time (:start-time-millis invoke)
        href (str "/agents/" (common/url-encode module-id) "/agent/" (common/url-encode agent-name) "/invocations/" task-id "-" agent-id)
        args-json (common/to-json (:invoke-args invoke))
        metric-names (or feedback-metric-names [])]
    ($ :tr.hover:bg-gray-50.transition-colors.duration-150
       {:key href}
       ($ :td.px-3.py-3.whitespace-nowrap
          ($ :a.inline-block.px-2.py-1.sm:px-3.bg-blue-100.text-blue-700.rounded.text-xs.font-medium.hover:bg-blue-200.transition-colors.duration-150.cursor-pointer
             {:href href}
             "View trace"))
       ($ :td.px-4.py-3.text-sm.text-gray-600.font-mono
          {:title (common/format-timestamp start-time)}
          (common/format-relative-time start-time))
       ($ :td.px-4.py-3.max-w-md.cursor-pointer.hover:bg-gray-100.rounded
          {:onClick (fn [e]
                      (. e stopPropagation)
                      (rf/dispatch [:modal/show :arguments-detail
                                    {:title "Invocation Arguments"
                                     :component ($ common/ContentDetailModal
                                                    {:title "Invocation Arguments"
                                                     :content args-json})}]))}
          ($ :div.truncate.text-gray-900.font-mono
             args-json))
       ($ :td.px-4.py-3.font-mono.text-gray-600 (:graph-version invoke))
       ($ :td.px-4.py-3.text-sm
          ($ result-badge {:status (:status invoke)
                           :human-request? (:human-request? invoke)}))
       (for [mn metric-names]
         ($ :td.px-4.py-3.text-sm.text-gray-700.font-mono
            {:key (str "fb-" mn)}
            (feedback-cell-value invoke mn))))))

(defui invocations []
  (let [{:keys [module-id agent-name]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        query-params (or (use-subscribe [::aor-rf/get-in [:route :parameters :query]]) {})
        filters-query-param (or (:filters query-params)
                                (get query-params "filters"))
        applied-filters (inv-filters/applied-filters-from-url filters-query-param)
        {filter-options-data :data}
        (use-subscribe [::rfq/query ::rpc-invocations/get-filter-options!!
                        {:module-id module-id :agent-name agent-name}])
        node-options (or (:nodes filter-options-data) [])
        raw-feedback-metric-options (or (:feedback-metrics filter-options-data) [])
        feedback-metric-options
        (->> raw-feedback-metric-options
             (keep (fn [item]
                     (let [name-value (cond
                                         (string? item) item
                                         :else (or (:name item)
                                                   (get item "name")
                                                   (:metric-name item)
                                                   (get item "metric-name")))
                           metric-value (when-not (string? item)
                                          (or (:metric item)
                                              (get item "metric")))
                           normalized-name (some-> name-value str str/trim)]
                       (when (and normalized-name (not (str/blank? normalized-name)))
                         {:name normalized-name
                          :metric metric-value}))))
             vec)
        feedback-metric-options-by-name (into {}
                                              (map (fn [metric] [(:name metric) metric]))
                                              feedback-metric-options)
        feedback-metric-option-names (mapv :name feedback-metric-options)
        applied-feedback-metrics (or (:feedback-metrics applied-filters) [])
        feedback-metric-names (mapv :metric-name applied-feedback-metrics)
        n-feedback-cols (count feedback-metric-names)

        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-infinite-rpc-query
         {:rfq-key ::rpc-invocations/get-page-inf!!
          :params {:module-id module-id
                   :agent-name agent-name
                   :filters applied-filters}
          :page-size 20
          :enabled? (boolean (and module-id agent-name))})]

    ($ :div.p-4.space-y-4.h-screen
       ($ inv-filters/filter-bar
          {:module-id module-id
           :agent-name agent-name
           :applied applied-filters
           :node-options node-options
           :feedback-metric-options-by-name feedback-metric-options-by-name
           :feedback-metric-option-names feedback-metric-option-names})
       (cond
         (and isLoading (empty? data))
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "Loading invocations..."))

         error
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-red-500 "Error loading invocations: " error))

         (empty? data)
         ($ :div.flex.justify-center.items-center.py-8
            ($ :div.text-gray-500 "No invocations found"))

         :else
         ($ :div.bg-white.rounded-md.border.border-gray-200.overflow-x-auto.shadow-sm
            ($ :table.w-full.text-sm
               ($ :thead.bg-gray-50.border-b.border-gray-200
                  ($ :tr
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Trace")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Start Time")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Arguments")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Version")
                     ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide "Result")
                     (for [mn feedback-metric-names]
                       ($ :th.px-4.py-3.text-left.font-semibold.text-gray-700.text-xs.uppercase.tracking-wide
                          {:key (str "h-" mn)}
                          (str mn)))))
               ($ :tbody.divide-y.divide-gray-200
                  (for [invoke data]
                    ($ invocation-row {:key (str (:task-id invoke) "-" (:agent-id invoke))
                                       :invoke invoke
                                       :module-id module-id
                                       :agent-name agent-name
                                       :feedback-metric-names feedback-metric-names
                                       :on-click (fn [url] (set! (.-href (.-location js/window)) url))})))

               (when hasMore
                 ($ :tfoot.bg-gray-50.border-t.border-gray-200
                    ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                       {:data-testid "invocations-load-more"
                        :onClick (when-not isFetchingMore loadMore)}
                       ($ :td.px-4.py-3.cursor-pointer {:colSpan (+ 5 n-feedback-cols)}
                          ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                             ($ :span.mr-2.text-sm.font-medium (if isFetchingMore "Loading..." "Load More"))
                             (when-not isFetchingMore
                               ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                                  ($ :path {:fillRule "evenodd"
                                            :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                            :clipRule "evenodd"}))))))))))))))

(defui mini-invocations []
  (let [{:keys [module-id agent-name]} (use-subscribe [::aor-rf/get-in [:route :path-params]])
        {:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-invocations/get-page!!
                        {:module-id module-id
                         :agent-name agent-name
                         :pagination {}}])
        loading? (#{:loading :idle} query-status)]
    (cond
      loading? ($ :div.flex.justify-center.items-center.py-8
                  ($ :div.text-gray-500 "Loading invocations..."))
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
                                    :feedback-metric-names []
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
