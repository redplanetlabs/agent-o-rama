(ns com.rpl.agent-o-rama.ui.experiments.index
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon PlusIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str])) ;; Require events to register handlers

(defui index [{:keys [module-id dataset-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiments module-id dataset-id]
          :sente-event [:experiments/get-all-for-dataset {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        experiments (get data :items)]

    ($ :div.p-6
       ;; Header
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :h2.text-2xl.font-bold "Experiments")
          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.cursor-pointer
             {:onClick #(state/dispatch [:modal/show-form :create-experiment {:module-id module-id :dataset-id dataset-id}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Run New Experiment"))

       ;; Content
       (cond
         loading? ($ :div.text-center.py-12 ($ common/spinner {:size :large}))
         error ($ :div.text-red-500.text-center.py-8 "Error loading experiments: " error)
         (empty? experiments)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No experiments run yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Run your first experiment to evaluate agent performance."))
         :else
         ($ :div {:className common/table-classes.container}
            ($ :table {:className common/table-classes.table}
               ($ :thead {:className common/table-classes.thead}
                  ($ :tr
                     ($ :th {:className common/table-classes.th} "Experiment Name")
                     ($ :th {:className common/table-classes.th} "Type")
                     ($ :th {:className common/table-classes.th} "Status")
                     ($ :th {:className common/table-classes.th} "Started")
                     ($ :th {:className common/table-classes.th} "Actions")))
               ($ :tbody
                  (for [exp experiments
                        :let [info (get exp :experiment-info)
                              spec (get info :spec)]]
                    ($ :tr {:key (:id info)}
                       ($ :td {:className common/table-classes.td}
                          ($ :div.font-medium.text-gray-900 (:name info)))
                       ($ :td {:className common/table-classes.td}
                          (if (str/ends-with? (get spec :type) "RegularExperiment")
                            "Regular"
                            "Comparative"))
                       ($ :td {:className common/table-classes.td}
                          (if (:finish-time-millis exp)
                            ($ :span.px-2.py-1.bg-green-100.text-green-800.rounded-full.text-xs "Completed")
                            ($ :span.px-2.py-1.bg-blue-100.text-blue-800.rounded-full.text-xs "Running")))
                       ($ :td {:className common/table-classes.td}
                          (common/format-relative-time (:start-time-millis exp)))
                       ($ :td {:className common/table-classes.td}
                          ($ :button.text-indigo-600.hover:text-indigo-900 "View Results")))))))))))