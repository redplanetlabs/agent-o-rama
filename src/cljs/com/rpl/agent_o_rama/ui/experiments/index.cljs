(ns com.rpl.agent-o-rama.ui.experiments.index
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon PlusIcon TrashIcon MagnifyingGlassIcon]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.experiments.evaluators :as evaluators]
   [com.rpl.agent-o-rama.ui.chart :as chart]
   [clojure.string :as str]
   [reitit.frontend.easy :as rfe]))

;; Helper component for displaying statistics in table cells
(defui StatCell [{:keys [value tooltip]}]
  ($ :td.px-4.py-3.text-sm.text-gray-700.text-right.font-mono.whitespace-nowrap
     {:title tooltip}
     (if (some? value) (str value) "N/A")))

(defn prepare-chart-data
  "Transform experiment data into uPlot time-series format.
  
  Returns a map with:
  - :data - [[experiment-numbers] [series1] [series2] ...] in uPlot format
  - :series - [{:label :stroke :width} ...] for each data series
  - :selected-metrics - set of metric keys that are being displayed"
  [experiments columns]
  (when (seq experiments)
    (let [;; Sort experiments by start time for chronological order
          sorted-experiments (sort-by :start-time-millis experiments)

          ;; Generate experiment numbers: 1, 2, 3, ...
          experiment-numbers (mapv inc (range (count sorted-experiments)))

          ;; Build series data for each evaluator metric
          series-data (atom [])
          series-config (atom [])

          ;; Color palette for different series
          colors ["#3b82f6" "#ef4444" "#10b981" "#f59e0b" "#8b5cf6"
                  "#ec4899" "#14b8a6" "#f97316" "#06b6d4" "#84cc16"]]

      ;; Process each column/metric
      (doseq [[idx {:keys [column-key label eval-name metric-key]}] (map-indexed vector columns)]
        (let [;; Extract values for this metric across all experiments
              values (mapv (fn [exp]
                             (let [eval-stats (:eval-number-stats exp)
                                   metric-data (get-in eval-stats [eval-name metric-key])
                                   num-examples (get-in exp [:latency-number-stats :count] 0)]
                               ;; Calculate average: total / count
                               (when (and metric-data
                                          (pos? num-examples)
                                          (some? (:total metric-data))
                                          (pos? (:count metric-data)))
                                 (/ (:total metric-data) (:count metric-data)))))
                           sorted-experiments)
              color (get colors (mod idx (count colors)))]

          ;; Only add series if it has at least some non-nil values
          (when (some some? values)
            (swap! series-data conj values)
            (swap! series-config conj {:label label
                                       :stroke color
                                       :width 2
                                       :points {:show false}}))))

      {:data (into [experiment-numbers] @series-data)
       :series @series-config
       :selected-metrics (set (map :column-key columns))})))

(defui index [{:keys [module-id dataset-id]}]
  (let [;; Add state for search term and debounce it
        [search-term set-search-term] (uix/use-state "")
        [debounced-search-term] (useDebounce search-term 300)

        ;; Update the query hook to use the debounced search term
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id :regular debounced-search-term]
          :sente-event [:experiments/get-all-for-dataset
                        {:module-id module-id
                         :dataset-id dataset-id
                         :filters {:type :regular
                                   :search-string (when-not (str/blank? debounced-search-term)
                                                    debounced-search-term)}}]
          :enabled? (boolean (and module-id dataset-id))
          :refresh-interval-ms 1000})

        experiments (get data :items)
        {:keys [columns ambiguous-metrics] :as evaluator-metadata}
        (evaluators/collect-column-metadata
         (map :eval-number-stats experiments))

        ;; Create a map from experiment ID to experiment number (based on chronological order)
        experiment-number-map (into {}
                                    (map-indexed
                                     (fn [idx exp]
                                       [(:id (:experiment-info exp)) (inc idx)])
                                     (sort-by :start-time-millis experiments)))]

    ($ :div.p-6
       ;; Header with search and create button
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex-1
             ($ :div.relative.mt-2.rounded-md.shadow-sm.max-w-md
                ($ :input
                   {:type "text"
                    :value search-term
                    :onChange #(set-search-term (.. % -target -value))
                    :className "block w-full rounded-md border-0 py-1.5 pl-2 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    :placeholder "Search by name or ID..."})))

          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick #(state/dispatch [:modal/show-form :create-experiment
                                         {:module-id module-id
                                          :dataset-id dataset-id
                                          :spec {:type :regular}}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Run New Experiment"))

       ;; Main content
       (cond
         loading? ($ :div.text-center.py-12 ($ common/spinner {:size :large}))
         error ($ :div.text-red-500.text-center.py-8 "Error loading experiments: " error)
         (empty? experiments)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No experiments run yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Run your first experiment to evaluate agent performance."))

         ;; When we have experiments, show chart and table
         :else
         ($ :<>
            ;; Performance chart (only show if we have 2+ experiments)
            (when (and (seq experiments) (>= (count experiments) 2))
              (let [chart-data (prepare-chart-data experiments columns)]
                (when (and chart-data (> (count (:data chart-data)) 1))
                  ($ :div.bg-white.rounded-lg.shadow-sm.p-6.mb-6
                     ($ :h3.text-lg.font-semibold.text-gray-800.mb-4
                        "Evaluator Performance Over Time")
                     ($ chart/time-series-chart
                        {:data (:data chart-data)
                         :series (:series chart-data)
                         :width 1000
                         :height 400})))))

            ;; Experiments table
            ($ :div {:className (common/cn (:container common/table-classes) "overflow-x-auto")}
               ($ :table {:className (:table common/table-classes)}
                  ($ :thead {:className (:thead common/table-classes)}
                     ($ :tr
                        ($ :th {:className (:th common/table-classes)} "#")
                        ($ :th {:className (:th common/table-classes)} "Experiment Name")
                        ($ :th {:className (:th common/table-classes)} "Status")
                        ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "# Examples")
                        ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "Avg Latency (ms)")
                        ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "P99 Latency (ms)")
                        ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "Avg Total Tokens")
                        (for [{:keys [column-key label]} columns]
                          ($ :th {:key column-key, :className (common/cn (:th common/table-classes) "text-right")}
                             ($ :div.truncate {:title label} label)))
                        ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "Actions")))
                  ($ :tbody
                     (for [exp experiments
                           :let [info (:experiment-info exp)
                                 latency-stats (:latency-number-stats exp)
                                 token-stats (:total-token-number-stats exp)
                                 eval-stats (:eval-number-stats exp)
                                 num-examples (or (:count latency-stats) 0)]]
                       ($ :tr {:key (:id info)
                               :className "hover:bg-gray-50 cursor-pointer"
                               :onClick (fn [_]
                                          (rfe/push-state :module/dataset-detail.experiment-detail
                                                          {:module-id module-id
                                                           :dataset-id dataset-id
                                                           :experiment-id (:id info)}))}
                          ;; Experiment Number
                          ($ :td {:className (:td common/table-classes)}
                             ($ :div.font-mono.text-gray-600
                                (str "#" (get experiment-number-map (:id info)))))

                          ;; Experiment Name
                          ($ :td {:className (:td common/table-classes)}
                             ($ :div.font-medium.text-gray-900.truncate {:title (:name info)} (:name info)))

                          ;; Status
                          ($ :td {:className (:td common/table-classes)}
                             (if (:finish-time-millis exp)
                               ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs.font-medium "Completed")
                               ($ :span.px-2.py-1.bg-blue-100.text-blue-800.rounded-full.text-xs.font-medium "Running")))

                          ;; # Examples
                          ($ StatCell {:value num-examples})

                          ;; Avg Latency
                          ($ StatCell {:value (when (and latency-stats (pos? num-examples))
                                                (int (/ (:total latency-stats) num-examples)))})

                          ;; P99 Latency
                          ($ StatCell {:value (get-in latency-stats [:percentiles 0.99])})

                          ;; Avg Total Tokens
                          ($ StatCell {:value (when (and token-stats (pos? num-examples))
                                                (int (/ (:total token-stats) num-examples)))})

                          ;; Dynamic columns for each evaluator
                          (for [{:keys [column-key eval-name metric-key ambiguous? metric-label]} columns]
                            (let [metric-data (get-in eval-stats [eval-name metric-key])
                                  count (:count metric-data)
                                  total (:total metric-data)
                                  display-value (when (and metric-data
                                                           (pos? (or count 0))
                                                           (some? total))
                                                  (.toFixed (/ total count) 2))
                                  tooltip (if ambiguous?
                                            (str "Avg. " metric-label " (" eval-name ")")
                                            (str "Avg. " metric-label))]
                              ($ StatCell {:key column-key, :value display-value, :tooltip tooltip})))

                          ;; Delete action
                          ($ :td {:className (:td-right common/table-classes)}
                             ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                {:onClick (fn [e]
                                            (.stopPropagation e)
                                            (when (js/confirm (str "Are you sure you want to delete experiment '" (:name info) "'?"))
                                              (sente/request!
                                               [:experiments/delete {:module-id module-id
                                                                     :dataset-id dataset-id
                                                                     :experiment-id (:id info)}]
                                               10000
                                               (fn [reply]
                                                 (if (:success reply)
                                                   (state/dispatch [:query/invalidate {:query-key-pattern [:experiments module-id dataset-id]}])
                                                   (js/alert (str "Failed to delete experiment: " (:error reply))))))))}
                                ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                "Delete"))))))))))))
