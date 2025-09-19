(ns com.rpl.agent-o-rama.ui.experiments.regular-detail
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [ArrowLeftIcon PlayIcon ChevronDownIcon ChevronUpIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]
   [reitit.frontend.easy :as rfe]
   ;; NEW: Require the forms namespace to access the transformation function
   [com.rpl.agent-o-rama.ui.experiments.forms :as forms]))

(defui ExperimentErrorPanel [{:keys [error-info]}]
  (let [has-exception? (or (:via error-info) (:trace error-info))
        [show-details? set-show-details] (uix/use-state false)
        [show-trace? set-show-trace] (uix/use-state false)]
    ($ :div.bg-red-50.p-6.rounded-lg.border.border-red-200
       ($ :h3.text-lg.font-semibold.text-red-800.mb-2
          (if has-exception? "Experiment Failed with Exception" "Experiment Failed to Start"))

       ;; Handle the new exception structure
       (if has-exception?
         ($ :div.space-y-4
            ;; Root cause message
            (when-let [cause (:cause error-info)]
              ($ :div
                 ($ :h4.text-sm.font-medium.text-red-800.mb-2 "Error:")
                 ($ :p.text-sm.text-red-700.bg-red-100.p-3.rounded.font-mono cause))))

         ;; Handle the old error structure (fallback)
         ($ :div
            ($ :p.text-sm.text-red-700.mb-4 (:error error-info))
            (when-let [problems (:problems error-info)]
              ($ :div
                 ($ :h4.text-sm.font-medium.text-red-800.mb-2 "Details:")
                 ($ :ul.list-disc.list-inside.space-y-2.pl-2
                    (for [[idx problem] (map-indexed vector problems)]
                      ($ :li.text-sm.text-red-700 {:key idx}
                         ($ :span.font-mono.text-xs.bg-red-100.p-1.rounded.mt-1
                            (pr-str problem))))))))))))

 ;; NEW: Simple modal content to display exception details
(defui ExceptionModal [{:keys [throwable]}]
  ($ :div.p-6.space-y-4
     ($ :div
        ($ :h4.text-sm.font-medium.text-gray-900.mb-2 "Exception Details")
        ($ :pre.text-xs.bg-gray-50.p-3.rounded.border.overflow-auto.max-h-80.font-mono
           (common/pp throwable)))))

(defui StatCard [{:keys [label value]}]
  ($ :div.bg-gray-50.p-4.rounded-lg.border
     ($ :div.text-sm.text-gray-600 label)
     ($ :div.text-2xl.font-bold.text-gray-900 value)))

 ;; NEW: Added DetailItem component for rendering key-value pairs in the info panel.
(defui DetailItem [{:keys [label children]}]
  ($ :div.py-2.sm:grid.sm:grid-cols-3.sm:gap-4.sm:px-0
     ($ :dt.text-sm.font-medium.leading-6.text-gray-900 label)
     ($ :dd.mt-1.text-sm.leading-6.text-gray-700.sm:col-span-2.sm:mt-0
        children)))

 ;; NEW: Created the ExperimentInfoPanel to display the experiment-info data.
(defui ExperimentInfoPanel [{:keys [info]}]
  (let [spec (:spec info)
        exp-type (name (or (:type spec) (if (:target spec) :regular :comparative)))
        targets (if (:target spec)
                  [(:target spec)]
                  (:targets spec))]
    ($ :div.bg-blue-50.border-y.border-blue-200.px-6.py-4
       ($ :dl.divide-y.divide-gray-200
          (when-let [id (:id info)]
            ($ DetailItem {:label "ID"}
               ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-48.font-mono (str id))))
          ($ DetailItem {:label "experiment-type"}
             ($ :span.text-sm.text-gray-800 exp-type))
          (for [[idx t] (map-indexed vector (or targets []))]
            (let [label (if (= 1 (count targets))
                          "target"
                          (str "target" (inc idx)))]
              ($ DetailItem {:key (str "target-" idx) :label label}
                 ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-48.font-mono
                    (common/pp t)))))
          ;; Always show snapshot and selector, even when nil
          ($ DetailItem {:label "snapshot"}
             ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-48.font-mono (common/pp (:snapshot info))))
          ($ DetailItem {:label "selector"}
             ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-48.font-mono (common/pp (:selector info))))
          (when-let [evaluators (:evaluators info)]
            ($ DetailItem {:label "evaluators"}
               ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-48.font-mono (common/pp evaluators))))
          (when-let [nr (:num-repetitions info)]
            ($ DetailItem {:label "num-repetitions"}
               ($ :span.text-sm.text-gray-800 (str nr))))
          (when-let [conc (:concurrency info)]
            ($ DetailItem {:label "concurrency"}
               ($ :span.text-sm.text-gray-800 (str conc))))))))

(defui ExperimentHeader [{:keys [info status on-rerun module-id dataset-id show-info? on-toggle-info]}]
  ($ :div.flex.justify-between.items-center
     ($ :div.flex.items-center.gap-4
        ($ :a.inline-flex.items-center.text-gray-600.hover:text-gray-900
           {:href (rfe/href :module/dataset-detail.experiments {:module-id module-id :dataset-id dataset-id})}
           ($ ArrowLeftIcon {:className "h-5 w-5 mr-2"})
           "Back")
        ($ :h2.text-2xl.font-bold (:name info))
        ;; NEW: Added "Details" button that toggles the info panel
        ($ :button.inline-flex.items-center.px-3.py-1.text-sm.text-gray-600.hover:text-gray-800.rounded-md.hover:bg-gray-100.cursor-pointer
           {:onClick on-toggle-info}
           ($ :span.mr-1 "Details")
           (if show-info?
             ($ ChevronUpIcon {:className "h-4 w-4"})
             ($ ChevronDownIcon {:className "h-4 w-4"}))))
     ($ :div.flex.items-center.gap-4
        ($ :span.px-3.py-1.rounded-full.text-sm.font-medium
           {:className (case status
                         :completed "bg-green-100 text-green-800"
                         :failed "bg-red-100 text-red-800"
                         "bg-blue-100 text-blue-800")}
           (case status
             :completed "✅ Completed"
             :failed "❌ Failed"
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

(defn format-metric-value [value]
  (cond
    (true? value) ($ :span.text-green-700 "T")
    (false? value) ($ :span.text-red-700 "F")
    (and (number? value) (<= 0 value) (<= value 1)) (str (int (* 100 value)) "/100")
    (number? value) (str value)
    (string? value) (if (> (count value) 20) (str (subs value 0 17) "…") value)
    (nil? value) ($ :span.italic.text-gray-400 "nil")
    :else ($ :span.italic.text-gray-400 "…")))

(defui EvaluatorScores [{:keys [evals failures duplicate-metric-keys eval-initiates module-id]}]
  ($ :div.flex.flex-wrap.gap-1.items-center
     (for [[eval-name error-str] (sort-by key failures)]
       (let [eval-invoke (get eval-initiates eval-name)]
         ($ :a.px-2.py-1.rounded-md.text-xs.font-medium.bg-red-100.text-red-800.hover:bg-red-200.cursor-pointer.transition-colors.no-underline
            {:key (str "fail-" (name eval-name))
             :href (when eval-invoke
                     (rfe/href :agent/invocation-detail
                               {:module-id module-id
                                :agent-name "_aor-evaluator"
                                :invoke-id (str (:task-id eval-invoke) "-" (:agent-invoke-id eval-invoke))}))
             :target "_blank"}
            (str (name eval-name) ": Failed"))))
     (for [[eval-name eval-result] (sort-by key evals)
           [metric-key metric-value] (sort-by key eval-result)
           :let [label (if (contains? duplicate-metric-keys metric-key)
                         (str (name eval-name) "/" (name metric-key))
                         (name metric-key))
                 eval-invoke (get eval-initiates eval-name)]]
       ($ :a.flex.items-center.gap-1.5.px-2.py-1.rounded-md.text-xs.bg-gray-100.text-gray-800.border.border-gray-200.hover:bg-gray-200.cursor-pointer.transition-colors.no-underline
          {:key (str (name eval-name) "-" (name metric-key))
           :href (when eval-invoke
                   (rfe/href :agent/invocation-detail
                             {:module-id module-id
                              :agent-name "_aor-evaluator"
                              :invoke-id (str (:task-id eval-invoke) "-" (:agent-invoke-id eval-invoke))}))
           :target "_blank"}
          ($ :span.font-medium.text-gray-600 label)
          ($ :span.font-semibold (format-metric-value metric-value))))))

(defui CellContent [{:keys [content truncated? on-expand]}]
  (let [content-str (common/pp content)
        is-long? (> (count content-str) 100)]
    ($ :div.relative.group
       ($ :div {:className (if truncated? "max-w-xs truncate" "max-w-lg")}
          content-str)
       (when (and is-long? truncated?)
         ($ :button.absolute.top-0.right-0.opacity-0.group-hover:opacity-100.transition-opacity.bg-blue-500.text-white.rounded.text-xs.px-2.py-1.hover:bg-blue-600
            {:onClick #(on-expand content-str)}
            "↗")))))

(defui ContentModal [{:keys [content title]}]
  ($ :div.p-6.space-y-4
     ($ :h4.text-lg.font-medium.text-gray-900.mb-4 (or title "Content"))
     ($ :div.bg-gray-50.p-4.rounded.border.max-h-96.overflow-auto
        ($ :pre.text-sm.font-mono.whitespace-pre-wrap content))))

(defui ResultsTable [{:keys [results target module-id]}]
  (let [[show-full-text? set-show-full-text] (uix/use-state false)]
    ($ :div
       ($ :div.flex.justify-between.items-center.mb-4
          ($ :h3.text-xl.font-bold "Detailed Results")
          ($ :label.flex.items-center.gap-2.text-sm.text-gray-600
             ($ :input {:type "checkbox"
                        :checked show-full-text?
                        :onChange #(set-show-full-text (not show-full-text?))
                        :className "rounded"})
             "Show full text"))
       ($ :div {:className (:container common/table-classes)}
          ($ :table {:className (:table common/table-classes)}
             ($ :thead {:className (:thead common/table-classes)}
                ($ :tr
                   ($ :th {:className (:th common/table-classes)} "Input")
                   ($ :th {:className (:th common/table-classes)} "Reference Output")
                   ($ :th {:className (:th common/table-classes)} "Output")))
             ($ :tbody
                (for [run results
                      :let [evals (:evals run)
                            all-metric-keys (mapcat keys (vals evals))
                            metric-frequencies (frequencies all-metric-keys)
                            duplicate-keys (->> metric-frequencies (filter (fn [[_ v]] (> v 1))) (map first) set)]]
                  ($ :tr.border-b {:key (:example-id run)}
                     ($ :td {:className (:td common/table-classes)}
                        ($ CellContent {:content (:input run)
                                        :truncated? (not show-full-text?)
                                        :on-expand #(state/dispatch [:modal/show :content-detail
                                                                     {:title "Input"
                                                                      :component ($ ContentModal {:content % :title "Input"})}])})
                        (let [first-invoke (get-in run [:agent-initiates 0 :agent-invoke])]
                          (if first-invoke
                            ($ :div.mt-2
                               ($ :a.text-indigo-600.hover:text-indigo-900
                                  {:href (rfe/href :agent/invocation-detail
                                                   {:module-id module-id
                                                    :agent-name (get-in run [:agent-initiates 0 :agent-name])
                                                    :invoke-id (str (:task-id first-invoke) "-" (:agent-invoke-id first-invoke))})
                                   :target "_blank"}
                                  "View Trace"))
                            ($ :div.mt-2 ($ :span.text-gray-400 "No trace")))))
                     ($ :td {:className (:td common/table-classes)}
                        ($ CellContent {:content (:reference-output run)
                                        :truncated? (not show-full-text?)
                                        :on-expand #(state/dispatch [:modal/show :content-detail
                                                                     {:title "Reference Output"
                                                                      :component ($ ContentModal {:content % :title "Reference Output"})}])}))
                     ;; 0 is hardcoded, because this component only works for REGULAR experiments, not comparative ones
                     (let [agent-result (get-in run [:agent-results 0])]
                       ($ :td {:key i :className (:td common/table-classes)}
                          ($ :div.max-w-xs
                             ($ :div {:className (if show-full-text? "" "truncate")}
                                (if (:failure? (:result agent-result))
                                  ($ :div.space-y-2
                                     (if-let [throwable (get-in agent-result [:result :val :throwable])]
                                       ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-red-700.bg-red-50.border.border-red-200.rounded.hover:bg-red-100.cursor-pointer
                                          {:onClick #(state/dispatch [:modal/show :exception-detail
                                                                      {:title "Error Details"
                                                                       :component ($ ExceptionModal {:throwable throwable})}])}
                                          "View Error")
                                       ($ :span.text-red-500.font-semibold "FAIL")))
                                  (let [output-content (common/pp (:val (:result agent-result)))
                                        is-long? (> (count output-content) 100)]
                                    ($ :div.relative.group
                                       output-content
                                       (when (and is-long? (not show-full-text?))
                                         ($ :button.absolute.top-0.right-0.opacity-0.group-hover:opacity-100.transition-opacity.bg-blue-500.text-white.rounded.text-xs.px-2.py-1.hover:bg-blue-600
                                            {:onClick #(state/dispatch [:modal/show :content-detail
                                                                        {:title "Output"
                                                                         :component ($ ContentModal {:content output-content :title "Output"})}])}
                                            "↗"))))))
                             ($ :div.mt-2
                                ($ EvaluatorScores {:evals evals
                                                    :failures (:eval-failures run)
                                                    :duplicate-metric-keys duplicate-keys
                                                    :eval-initiates (:eval-initiates run)
                                                    :module-id module-id})))))))))))))

(defui regular-experiment-detail-page [{:keys [module-id dataset-id experiment-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiment-results module-id dataset-id experiment-id]
          :sente-event [:experiments/get-results {:module-id module-id
                                                  :dataset-id dataset-id
                                                  :experiment-id experiment-id}]
          :refetch-interval-ms 2000})
        ;; NEW: State for the details panel visibility
        [show-info? set-show-info] (uix/use-state false)]

    (cond
      loading? ($ :div.p-6.text-center.py-12 ($ common/spinner {:size :large}))
      error ($ :div.p-6.text-red-500.text-center.py-8 "Error loading experiment results: " error)

      (and data (:invocation-error data))
      ($ :div.p-6.space-y-6
         ($ ExperimentHeader {:info (:experiment-info data)
                              :status :failed
                                                            ;; NEW: Implement the on-rerun handler
                              :on-rerun #(let [form-props (-> (:experiment-info data)
                                                              forms/experiment-info->form-state
                                                              (assoc :module-id module-id
                                                                     :dataset-id dataset-id))]
                                           (state/dispatch [:modal/show-form :create-experiment form-props]))
                              :module-id module-id
                              :dataset-id dataset-id
                              ;; NEW: Pass state and handler to header
                              :show-info? show-info?
                              :on-toggle-info #(set-show-info (not show-info?))})
         ;; NEW: Conditionally render the info panel
         (when show-info?
           ($ ExperimentInfoPanel {:info (:experiment-info data)}))
         ($ ExperimentErrorPanel {:error-info (:invocation-error data)}))

      data ($ :div.p-6.space-y-6
              ($ ExperimentHeader {:info (:experiment-info data)
                                   :status (if (:finish-time-millis data) :completed :running)
                                                                      ;; NEW: Implement the on-rerun handler
                                   :on-rerun #(let [form-props (-> (:experiment-info data)
                                                                   forms/experiment-info->form-state
                                                                   (assoc :module-id module-id
                                                                          :dataset-id dataset-id))]
                                                (state/dispatch [:modal/show-form :create-experiment form-props]))
                                   :module-id module-id
                                   :dataset-id dataset-id
                                   ;; NEW: Pass state and handler to header
                                   :show-info? show-info?
                                   :on-toggle-info #(set-show-info (not show-info?))})
              ;; NEW: Conditionally render the info panel
              (when show-info?
                ($ ExperimentInfoPanel {:info (:experiment-info data)}))

              ($ SummaryPanel {:summary-evals (:summary-evals data)
                               :results (vals (:results data))})
              ($ ResultsTable {:results (vals (:results data))
                               :target (get-in data [:experiment-info :spec :target])
                               :module-id module-id}))
      :else ($ :div.p-6.text-center.py-12 "No experiment data found"))))
