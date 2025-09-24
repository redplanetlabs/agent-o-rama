(ns com.rpl.agent-o-rama.ui.experiments.index
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon PlusIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.experiments.evaluators :as evaluators]
   [clojure.string :as str]
   [reitit.frontend.easy :as rfe]))

;; Helper component for displaying statistics in table cells
(defui StatCell [{:keys [value tooltip]}]
  ($ :td.px-4.py-3.text-sm.text-gray-700.text-right.font-mono.whitespace-nowrap
     {:title tooltip}
     (if (some? value) (str value) "N/A"))) ;; Require events to register handlers

(defui index [{:keys [module-id dataset-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id :regular]
          :sente-event [:experiments/get-all-for-dataset
                        {:module-id module-id
                         :dataset-id dataset-id
                         :filters {:type :regular}}]
          :enabled? (boolean (and module-id dataset-id))
          :refresh-interval-ms 1000})

        experiments
        (get data :items)
        {:keys [columns ambiguous-metrics] :as evaluator-metadata}
        (evaluators/collect-column-metadata
         (map :eval-number-stats experiments))]

    ($ :div.p-6
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h2.text-2xl.font-bold "Experiments")
          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick #(state/dispatch [:modal/show-form :create-experiment
                                         {:module-id module-id
                                          :dataset-id dataset-id
                                          :spec {:type :regular}}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Run New Experiment"))

       (cond
         loading? ($ :div.text-center.py-12 ($ common/spinner {:size :large}))
         error ($ :div.text-red-500.text-center.py-8 "Error loading experiments: " error)
         (empty? experiments)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No experiments run yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Run your first experiment to evaluate agent performance."))
         :else
         ($ :div {:className (common/cn (:container common/table-classes) "overflow-x-auto")}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Experiment Name")
                     ($ :th {:className (:th common/table-classes)} "Status")
                     ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "# Examples")
                     ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "Avg Latency (ms)")
                     ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "P99 Latency (ms)")
                     ($ :th {:className (common/cn (:th common/table-classes) "text-right")} "Avg Total Tokens")
                     (for [{:keys [column-key label]} columns]
                       ($ :th {:key column-key
                               :className (common/cn (:th common/table-classes) "text-right")}
                          ($ :div.truncate {:title label}
                             label)))))
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
                           ($ StatCell {:key column-key
                                        :value display-value
                                        :tooltip tooltip}))))))))))))

