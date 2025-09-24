(ns com.rpl.agent-o-rama.ui.experiments.regular-detail
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [ArrowLeftIcon PlayIcon ChevronDownIcon ChevronUpIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.experiments.evaluators :as evaluators]
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

;; Removed old StatCard component - replaced with table-based summary

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

;; NEW: Table-based summary components for compact data display
(defui StatCell [{:keys [label value tooltip]}]
  ($ :td.px-4.py-3.border-b.border-gray-200.whitespace-nowrap
     {:title tooltip}
     ($ :div.text-xs.font-medium.text-gray-500.uppercase.tracking-wider label)
     ($ :div.text-xl.font-semibold.text-gray-900.mt-1 value)))

(defui SummaryEvaluatorCell [{:keys [eval-name metrics columns]}]
  (let [eval-label (evaluators/identifier-title eval-name)
        columns (or (seq columns)
                    (evaluators/columns-for-evaluator
                     (evaluators/collect-column-metadata {eval-name metrics})
                     eval-name))]
    ($ :td.px-4.py-3.border-b.border-gray-200
       ($ :div.text-xs.font-medium.text-gray-500.uppercase.tracking-wider
          (or eval-label (str eval-name)))
       ($ :div.mt-1.space-y-1
          (for [{:keys [metric-key label metric-label]} columns
                :let [metric-value (get metrics metric-key)
                      display-label (or label metric-label (evaluators/metric-title metric-key))]]
            ($ :div.flex.justify-between.text-sm {:key (str eval-name "-" metric-key)}
               ($ :span.text-gray-600 display-label)
               ($ :span.font-semibold.text-gray-900.ml-2 (format-metric-value metric-value))))))))

(defui SummaryStatsTable [{:keys [data]}]
  (let [;; --- Destructure all necessary data from the main `data` prop ---
        results (vals (:results data))
        latency-stats (:latency-number-stats data)
        token-stats (:total-token-number-stats data)
        summary-evals (:summary-evals data)
        summary-metadata (evaluators/collect-column-metadata summary-evals)

        ;; --- Perform calculations for each required metric ---
        num-examples (or (:count latency-stats) 0)

        success-rate (let [passed-count (count (filter #(not-any? :failure? (vals (:agent-results %))) results))]
                       (if (pos? num-examples)
                         (str (int (* 100 (/ passed-count num-examples))) "%")
                         "N/A"))

        avg-latency (let [total (:total latency-stats 0)
                          count (:count latency-stats 0)]
                      (if (pos? count)
                        (str (int (/ total count)) " ms")
                        "N/A"))

        p99-latency (if-let [p99 (get-in latency-stats [:percentiles 0.99])]
                      (str p99 " ms")
                      "N/A")

        avg-total-tokens (let [total (:total token-stats 0)
                               count (:count token-stats 0)]
                           (if (pos? count)
                             (.toLocaleString (int (/ total count)))
                             "N/A"))]

    ($ :div.mb-6
       ($ :div.overflow-x-auto.bg-white.rounded-lg.border.border-gray-200.shadow-sm
          ($ :table.min-w-full
             ($ :tbody
                ($ :tr
                   ($ StatCell {:label "# Examples" :value num-examples})
                   ($ StatCell {:label "Success Rate" :value success-rate})
                   ($ StatCell {:label "Avg Latency" :value avg-latency})
                   ($ StatCell {:label "P99 Latency"
                                :value p99-latency
                                :tooltip "99% of runs completed faster than this time."})
                   ($ StatCell {:label "Avg Total Tokens"
                                :value avg-total-tokens
                                :tooltip "Average total tokens (input + output) per example."})

                   ;; Dynamically create a column for each summary evaluator TODO move this below
                   (for [[eval-name metrics] summary-evals
                         :let [columns (evaluators/columns-for-evaluator summary-metadata eval-name)]]
                     ($ SummaryEvaluatorCell {:key (str eval-name)
                                              :eval-name eval-name
                                              :metrics metrics
                                              :columns columns})))))))))

(defn format-metric-value [value]
  (cond
    (true? value) ($ :span.text-green-700 "True")
    (false? value) ($ :span.text-red-700 "False")
    (and (number? value) (<= 0 value) (<= value 1)) (str (int (* 100 value)) "/100")
    (number? value) (str value)
    (string? value) (if (> (count value) 20) (str (subs value 0 17) "…") value)
    (nil? value) ($ :span.italic.text-gray-400 "nil")
    :else ($ :span.italic.text-gray-400 "…")))

;; NEW COMPONENTS: Evaluator capsules for compact display within Output column
(defui EvaluatorCapsule [{:keys [eval-name metric-key metric-value eval-failure eval-invoke module-id columns-metadata]}]
  (let [eval-label (or (evaluators/identifier-title eval-name) (str eval-name))
        metric-info (when (and columns-metadata metric-key)
                      (evaluators/label-for columns-metadata eval-name metric-key))
        label (cond
                eval-failure eval-label
                metric-info (:label metric-info)
                metric-key (str eval-label "/" (evaluators/metric-title metric-key))
                :else eval-label)
        tooltip-label (or (:label metric-info) label)
        href (when eval-invoke
               (rfe/href :agent/invocation-detail
                         {:module-id module-id
                          :agent-name "_aor-evaluator"
                          :invoke-id (str (:task-id eval-invoke) "-" (:agent-invoke-id eval-invoke))}))
        [badge-style content-text title-text]
        (cond
          eval-failure
          ["bg-red-100 text-red-800" "Failed"
           (str tooltip-label ": " (if (string? eval-failure)
                                     eval-failure
                                     (common/pp eval-failure)))]

          (true? metric-value)
          ["bg-green-100 text-green-800" "✓T" (str tooltip-label ": True")]

          (false? metric-value)
          ["bg-yellow-100 text-yellow-800" "✗F" (str tooltip-label ": False")]

          (number? metric-value)
          ["bg-blue-100 text-blue-800" (format-metric-value metric-value)
           (str tooltip-label ": " metric-value)]

          :else
          ["bg-gray-100 text-gray-800" (format-metric-value metric-value)
           (str tooltip-label ": " (pr-str metric-value))])]

    ($ :a.inline-flex.items-center.px-2.py-1.rounded-full.text-xs.font-medium.transition-colors.hover:shadow-md
       {:className (common/cn "p-1 rounded-sm" badge-style)
        :href href
        :target "_blank"
        :title title-text
        :onClick (fn [e] (.stopPropagation e))} ;; Prevent row click
       ($ :span.font-semibold.mr-1.truncate {:style {:maxWidth "10rem"}}
          label)
       ($ :span.font-mono content-text))))

(defui EvaluatorCapsulesContainer [{:keys [run module-id columns-metadata]}]
  ($ :div.mt-2.flex.flex-wrap.gap-1
     ;; Render capsules for successful evaluations
     (for [[eval-name metrics] (:evals run)
           [metric-key metric-value] metrics]
       ($ EvaluatorCapsule {:key (str eval-name metric-key)
                            :eval-name eval-name
                            :metric-key metric-key
                            :metric-value metric-value
                            :eval-invoke (get-in run [:eval-initiates eval-name])
                            :module-id module-id
                            :columns-metadata columns-metadata}))
     ;; Render capsules for failed evaluations
     (for [[eval-name failure-info] (:eval-failures run)]
       ($ EvaluatorCapsule {:key (str eval-name "-failure")
                            :eval-name eval-name
                            :eval-failure failure-info
                            :eval-invoke (get-in run [:eval-initiates eval-name])
                            :module-id module-id
                            :columns-metadata columns-metadata}))))

(defui CellContent [{:keys [content truncated? on-expand]}]
  (let [content-str (common/pp content)
        is-long? (> (count content-str) 100)]
    ($ :div.relative.group
       ($ :div {:className (if truncated?
                             "max-w-xs truncate"
                             "max-w-xl whitespace-pre-wrap break-words")}
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

(defui FilterButton [{:keys [label is-active? on-click]}]
  ($ :button
     {:onClick on-click
      :className (common/cn
                  "px-3 py-1 text-xs font-medium rounded-full border transition-colors"
                  (if is-active?
                    "bg-blue-600 text-white border-blue-600"
                    "bg-white text-gray-600 border-gray-300 hover:bg-gray-100"))}
     label))

(defui FilterButtons [{:keys [active-filter on-change]}]
  ($ :div.flex.items-center.gap-2
     ($ FilterButton {:label "All"
                      :is-active? (= active-filter :all)
                      :on-click #(on-change :all)})
     ($ FilterButton {:label "Success"
                      :is-active? (= active-filter :success)
                      :on-click #(on-change :success)})
     ($ FilterButton {:label "Failure"
                      :is-active? (= active-filter :failure)
                      :on-click #(on-change :failure)})))

(defui ResultsTable [{:keys [results target module-id]}]
  (let [[show-full-text? set-show-full-text] (uix/use-state false)
        [active-filter set-active-filter] (uix/use-state :all)

        ;; NEW: Filtering logic
        is-failure? (fn [run]
                      (let [agent-failed? (get-in run [:agent-results 0 :result :failure?])
                            eval-failed? (some (fn [[_eval-name metrics]]
                                                 (some false? (vals metrics)))
                                               (:evals run))]
                        (or agent-failed? eval-failed?)))
        is-success? (fn [run] (not (is-failure? run)))

        filtered-results (case active-filter
                           :all results
                           :success (filter is-success? results)
                           :failure (filter is-failure? results))]
    ($ :div
       ($ :div.flex.justify-between.items-center.mb-4
          ($ :h3.text-xl.font-bold "Detailed Results")
          ($ :div.flex.items-center.gap-4
             ($ FilterButtons {:active-filter active-filter
                               :on-change set-active-filter})
             ($ :label.flex.items-center.gap-2.text-sm.text-gray-600
                ($ :input {:type "checkbox"
                           :checked show-full-text?
                           :onChange #(set-show-full-text (not show-full-text?))
                           :className "rounded"})
                "Show full text")))
       (if (empty? filtered-results)
         ($ :div.text-center.py-8.text-gray-500.bg-gray-50.rounded-lg
            "No results match the current filter.")
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Input")
                     ($ :th {:className (:th common/table-classes)} "Reference Output")
                     ($ :th {:className (common/cn (:th common/table-classes) "w-1/3")} "Output & Evaluations")))
               ($ :tbody
                  (for [[idx run] (map-indexed vector filtered-results)
                        :let [evaluator-metadata (evaluators/collect-column-metadata (:evals run))]]
                    ($ :tr.border-b {:key (str (:example-id run) "-" idx)}
                       ;; Input Cell
                       ($ :td {:className (:td common/table-classes)}
                          ($ CellContent {:content (:input run)
                                          :truncated? (not show-full-text?)
                                          :on-expand #(state/dispatch [:modal/show :content-detail
                                                                       {:title "Input"
                                                                        :component ($ ContentModal {:content % :title "Input"})}])}))
                       ;; Reference Output Cell
                       ($ :td {:className (:td common/table-classes)}
                          ($ CellContent {:content (:reference-output run)
                                          :truncated? (not show-full-text?)
                                          :on-expand #(state/dispatch [:modal/show :content-detail
                                                                       {:title "Reference Output"
                                                                        :component ($ ContentModal {:content % :title "Reference Output"})}])}))
                       ;; Output Cell with evaluator capsules
                       (let [agent-result (get-in run [:agent-results 0])]
                         ($ :td {:key "output-cell" :className (:td common/table-classes)}
                            ($ :div {:className "flex flex-col gap-2"}
                               ($ :div {:className (if show-full-text?
                                                     "max-w-xl whitespace-pre-wrap break-words"
                                                     "max-w-xs")}
                                  ($ :div {:className (if show-full-text?
                                                        "whitespace-pre-wrap break-words"
                                                        "truncate")}
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
                                                 "↗")))))))
                               ($ EvaluatorCapsulesContainer {:run run
                                                              :module-id module-id
                                                              :columns-metadata evaluator-metadata})))))))))))))
                       ;; Trace Column placeholder (if needed later)

(defui regular-experiment-detail-page [{:keys [module-id dataset-id experiment-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiment-results module-id dataset-id experiment-id]
          :sente-event [:experiments/get-results {:module-id module-id
                                                  :dataset-id dataset-id
                                                  :experiment-id experiment-id}]
          :refetch-interval-ms 2000})
        ;; NEW: State for the details panel visibility
        [show-info? set-show-info] (uix/use-state false)
        inv-error (:invocation-error data)
        status (cond
                 inv-error :failed
                 (:finish-time-millis data) :completed
                 :else :running)]

    (cond
      loading? ($ :div.p-6.text-center.py-12 ($ common/spinner {:size :large}))
      error ($ :div.p-6.text-red-500.text-center.py-8 "Error loading experiment results: " error)

      data ($ :div.p-6.space-y-6
              ($ ExperimentHeader {:info (:experiment-info data)
                                   :status status
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

              ;; NEW: Show error inline without hiding rest of page
              (when inv-error
                ($ ExperimentErrorPanel {:error-info inv-error}))

              ($ SummaryStatsTable {:data data})
              ($ ResultsTable {:results (vals (:results data))
                               :target (get-in data [:experiment-info :spec :target])
                               :module-id module-id}))
      :else ($ :div.p-6.text-center.py-12 "No experiment data found"))))
