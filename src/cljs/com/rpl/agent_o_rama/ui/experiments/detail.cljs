(ns com.rpl.agent-o-rama.ui.experiments.detail
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [ArrowLeftIcon PlayIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]))

(defui StatCard [{:keys [label value]}]
  ($ :div.bg-gray-50.p-4.rounded-lg.border
     ($ :div.text-sm.text-gray-600 label)
     ($ :div.text-2xl.font-bold.text-gray-900 value)))

(defui ExperimentHeader [{:keys [info status on-rerun]}]
  ($ :div.flex.justify-between.items-center
     ($ :div.flex.items-center.gap-4
        ($ :button.inline-flex.items-center.text-gray-600.hover:text-gray-900
           {:onClick #(state/dispatch [:navigate/back])}
           ($ ArrowLeftIcon {:className "h-5 w-5 mr-2"})
           "Back")
        ($ :h2.text-2xl.font-bold (:name info)))
     ($ :div.flex.items-center.gap-4
        ($ :span.px-3.py-1.rounded-full.text-sm.font-medium
           {:className (if (= status :completed)
                         "bg-green-100 text-green-800"
                         "bg-blue-100 text-blue-800")}
           (if (= status :completed)
             "✅ Completed"
             "🔄 Running"))
        ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
           {:onClick on-rerun}
           ($ PlayIcon {:className "h-5 w-5 mr-2"})
           "Re-run Experiment"))))

(defui SummaryPanel [{:keys [summary-evals results]}]
  (let [total-examples (count results)
        ;; Calculate success rate based on whether any agent failed
        passed-count (count (filter #(not-any? :failure? (vals (:agent-results %))) results))
        pass-rate (if (pos? total-examples)
                    (str (int (* 100 (/ passed-count total-examples))) "%")
                    "N/A")]
    ($ :div.grid.grid-cols-1.md:grid-cols-3.gap-4
       ($ StatCard {:label "Total Examples" :value total-examples})
       ($ StatCard {:label "Success Rate" :value pass-rate})
       ;; Dynamic cards for each summary evaluator metric
       (for [[eval-name eval-result] summary-evals
             [metric value] eval-result]
         ($ StatCard {:key (str eval-name metric)
                      :label (str (name eval-name) " - " (name metric))
                      :value (if (float? value)
                               (str (Math/round (* 100 value)) "/100")
                               (str value))})))))

(defui ScoreBadge [{:keys [evals failures]}]
  (let [total (count evals)
        failed-count (count failures)
        passed-count (- total failed-count)
        color-class (cond
                      (pos? failed-count) "bg-red-100 text-red-800"
                      (pos? passed-count) "bg-green-100 text-green-800"
                      :else "bg-gray-100 text-gray-800")]
    ($ :span.px-2.py-1.rounded-full.text-xs.font-medium {:className color-class}
       (if (and (zero? total) (zero? failed-count))
         "N/A"
         (str passed-count "/" total " OK")))))

(defui ResultsTable [{:keys [results targets module-id]}]
  ($ :div
     ($ :h3.text-xl.font-bold.mb-4 "Detailed Results")
     ($ :div {:className (:container common/table-classes)}
        ($ :table {:className (:table common/table-classes)}
           ($ :thead {:className (:thead common/table-classes)}
              ($ :tr
                 ($ :th {:className (:th common/table-classes)} "Input")
                 ($ :th {:className (:th common/table-classes)} "Reference Output")
                 ;; Dynamically create a column for each agent target
                 (for [[i target] (map-indexed vector targets)]
                   ($ :th {:key i :className (:th common/table-classes)}
                      (str "Output " (or (get-in target [:target-spec :agent-name]) (str "Target " (inc i))))))
                 ($ :th {:className (:th common/table-classes)} "Scores")
                 ($ :th {:className (:th common/table-classes)} "Actions")))
           ($ :tbody
              (for [run results]
                ($ :tr.border-b {:key (:example-id run)}
                   ($ :td {:className (:td common/table-classes)}
                      ($ :div.max-w-xs.truncate (common/pp (:input run))))
                   ($ :td {:className (:td common/table-classes)}
                      ($ :div.max-w-xs.truncate (common/pp (:reference-output run))))
                   ;; Render each agent's output
                   (for [i (range (count targets))]
                     (let [agent-result (get-in run [:agent-results i])]
                       ($ :td {:key i :className (:td common/table-classes)}
                          ($ :div.max-w-xs.truncate
                             (if (:failure? agent-result)
                               ($ :span.text-red-500 "FAIL")
                               (common/pp (:val agent-result)))))))
                   ($ :td {:className (:td common/table-classes)}
                      ($ ScoreBadge {:evals (:evals run) :failures (:eval-failures run)}))
                   ($ :td {:className (:td common/table-classes)}
                      (let [first-invoke (get-in run [:agent-initiates 0 :agent-invoke])]
                        (if first-invoke
                          ($ :a.text-indigo-600.hover:text-indigo-900
                             {:href (str "/agents/" module-id "/"
                                         (get-in run [:agent-initiates 0 :agent-name])
                                         "/invocation/"
                                         (:task-id first-invoke) "-" (:agent-invoke-id first-invoke))
                              :target "_blank"}
                             "View Trace")
                          ($ :span.text-gray-400 "No trace")))))))))))

(defui experiment-detail-page [{:keys [module-id dataset-id experiment-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiment-results module-id dataset-id experiment-id]
          :sente-event [:experiments/get-results {:module-id module-id
                                                  :dataset-id dataset-id
                                                  :experiment-id experiment-id}]})]
    (cond
      loading? ($ :div.p-6.text-center.py-12 ($ common/spinner {:size :large}))
      error ($ :div.p-6.text-red-500.text-center.py-8 "Error loading experiment results: " error)
      data ($ :div.p-6.space-y-6
              ($ ExperimentHeader {:info (:experiment-info data)
                                   :status (if (:finish-time-millis data) :completed :running)
                                   :on-rerun #(state/dispatch [:modal/show-form :create-experiment
                                                               {:module-id module-id
                                                                :dataset-id dataset-id}])})
              ($ SummaryPanel {:summary-evals (:summary-evals data)
                               :results (vals (:results data))})
              ($ ResultsTable {:results (vals (:results data))
                               :targets (get-in data [:experiment-info :spec :targets])
                               :module-id module-id}))
      :else ($ :div.p-6.text-center.py-12 "No experiment data found"))))