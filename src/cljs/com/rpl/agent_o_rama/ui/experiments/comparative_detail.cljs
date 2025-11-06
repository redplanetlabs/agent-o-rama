(ns com.rpl.agent-o-rama.ui.experiments.comparative-detail
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [BeakerIcon ArrowLeftIcon ChevronDownIcon ChevronUpIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.experiments.forms :as forms]
   [com.rpl.agent-o-rama.ui.experiments.evaluators :as evaluators]
   [com.rpl.agent-o-rama.ui.experiments.regular-detail :as regular-detail]
   [reitit.frontend.easy :as rfe]))

(defn find-all-selector-evaluators
  "Find all evaluators that return an 'index' key.
   Returns a map of evaluator-name -> index value.
   Checks both keyword and string keys since data may come from JSON."
  [evals]
  (into {}
        (keep (fn [[eval-name metrics]]
                (when-let [idx (or (get metrics "index")
                                   (get metrics :index))]
                  [eval-name idx]))
              evals)))

(defn find-winner-index
  "Find the winning target index from a specific evaluator's results.
   If evaluator-name is provided, returns the index from that evaluator.
   Otherwise, returns the first evaluator result that contains an 'index' key."
  ([evals] (find-winner-index evals nil))
  ([evals evaluator-name]
   (if evaluator-name
     (let [metrics (get evals evaluator-name)]
       (or (get metrics "index")
           (get metrics :index)))
     (some (fn [[_eval-name metrics]]
             (or (get metrics "index")
                 (get metrics :index)))
           evals))))

(defn filter-non-selector-evals
  "Filter out evaluators that have an 'index' key (selector evaluators).
   Checks both keyword and string keys since data may come from JSON."
  [evals]
  (into {}
        (remove (fn [[_eval-name metrics]]
                  (or (contains? metrics "index")
                      (contains? metrics :index)))
                evals)))

(defui ComparativeResultsTable [{:keys [data module-id show-full-text? on-toggle-full-text]}]
  (let [experiment-info (:experiment-info data)
        targets (get-in experiment-info [:spec :targets])
        num-targets (count targets)
        results (vals (:results data))

        ;; Find all selector evaluators across all results
        all-selector-evals (reduce (fn [acc run]
                                     (merge acc (find-all-selector-evaluators (:evals run))))
                                   {}
                                   results)
        selector-eval-names (keys all-selector-evals)

        ;; State for selected selector evaluator
        [selected-selector set-selected-selector] (uix/use-state nil)
        active-selector (or selected-selector (first selector-eval-names))

        ;; Update selected selector when data loads or changes
        _ (uix/use-effect
           (fn []
             (if (seq selector-eval-names)
               (when (or (nil? selected-selector)
                         (not (contains? (set selector-eval-names) selected-selector)))
                 (set-selected-selector (first selector-eval-names)))
               (when (some? selected-selector)
                 (set-selected-selector nil))))
           #js [(count selector-eval-names) (pr-str (sort selector-eval-names)) selected-selector])

        ;; Collect all evaluator metrics for metadata
        all-evals (reduce (fn [acc run]
                            (merge-with merge acc (filter-non-selector-evals (:evals run))))
                          {}
                          results)
        columns-metadata (evaluators/collect-column-metadata all-evals)]

    ($ :div
       ($ :div.flex.justify-between.items-center.mb-4
          ($ :h3.text-xl.font-bold "Comparative Results")
          ($ :div.flex.items-center.gap-4
             ;; Selector evaluator dropdown (show when any selector evaluator exists)
             (when (seq selector-eval-names)
               ($ :div.flex.items-center.gap-2
                  ($ :label.text-sm.font-medium.text-gray-700 "Highlighting:")
                  ($ :div.w-48
                     ($ common/Dropdown
                        {:label "Selector Evaluator"
                         :display-text (or active-selector "None")
                         :data-testid "selector-evaluator-dropdown"
                         :items (for [eval-name selector-eval-names]
                                  {:key eval-name
                                   :label eval-name
                                   :selected? (= eval-name active-selector)
                                   :on-select #(set-selected-selector eval-name)})}))))
             ;; Show full text checkbox
             ($ :label.flex.items-center.gap-2.text-sm.text-gray-600
                ($ :input {:type "checkbox"
                           :checked show-full-text?
                           :onChange on-toggle-full-text
                           :className "rounded"})
                "Show full text")))

       (if (empty? results)
         ($ :div.text-center.py-8.text-gray-500.bg-gray-50.rounded-lg
            "No results yet.")
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Input")
                     ($ :th {:className (:th common/table-classes)} "Reference Output")
                     ;; Dynamic output columns
                     (for [i (range num-targets)]
                       ($ :th {:key (str "output-" i)
                               :className (:th common/table-classes)}
                          (str "Output " (inc i))))
                     ($ :th {:className (:th common/table-classes)} "Evals")))
               ($ :tbody
                  (for [[idx run] (map-indexed vector results)]
                    (let [winning-index (find-winner-index (:evals run) active-selector)
                          non-selector-evals (filter-non-selector-evals (:evals run))]
                      ($ :tr.border-b {:key (str (:example-id run) "-" idx)}
                         ;; Input Cell
                         ($ :td {:className (:td common/table-classes)}
                            ($ regular-detail/CellContent
                               {:content (:input run)
                                :truncated? (not show-full-text?)
                                :on-expand #(state/dispatch [:modal/show :content-detail
                                                             {:title "Input"
                                                              :component ($ regular-detail/ContentModal {:content % :title "Input"})}])}))
                         ;; Reference Output Cell
                         ($ :td {:className (:td common/table-classes)}
                            ($ regular-detail/CellContent
                               {:content (:reference-output run)
                                :truncated? (not show-full-text?)
                                :on-expand #(state/dispatch [:modal/show :content-detail
                                                             {:title "Reference Output"
                                                              :component ($ regular-detail/ContentModal {:content % :title "Reference Output"})}])}))
                         ;; Dynamic Output Columns
                         (for [i (range num-targets)]
                           (let [agent-result (get-in run [:agent-results i])
                                 is-winner? (and winning-index (= i winning-index))
                                 token-info (when agent-result
                                              {:input-token-count (:input-token-count agent-result)
                                               :output-token-count (:output-token-count agent-result)
                                               :total-token-count (:total-token-count agent-result)})
                                 duration-ms (when agent-result
                                               (when (and (:start-time-millis agent-result)
                                                          (:finish-time-millis agent-result))
                                                 (unchecked-subtract (:finish-time-millis agent-result)
                                                                     (:start-time-millis agent-result))))]
                             ($ :td {:key (str "output-" i)
                                     :className (common/cn (:td common/table-classes)
                                                           {"bg-green-50" is-winner?})}
                                (if agent-result
                                  ($ :div.flex.flex-col.items-start.gap-2
                                     (if (:failure? (:result agent-result))
                                       ($ :div.space-y-2
                                          (if-let [throwable (get-in agent-result [:result :val :throwable])]
                                            ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-red-700.bg-red-50.border.border-red-200.rounded.hover:bg-red-100.cursor-pointer
                                               {:onClick #(state/dispatch [:modal/show :exception-detail
                                                                           {:title "Error Details"
                                                                            :component ($ regular-detail/ExceptionModal {:throwable throwable})}])}
                                               "View Error")
                                            ($ :span.text-red-500.font-semibold "FAIL")))
                                       ($ regular-detail/CellContent
                                          {:content (get-in agent-result [:result :val])
                                           :truncated? (not show-full-text?)
                                           :on-expand #(state/dispatch [:modal/show :content-detail
                                                                        {:title (str "Output " (inc i))
                                                                         :component ($ regular-detail/ContentModal {:content % :title (str "Output " (inc i))})}])}))
                                     (when (or duration-ms
                                               (and token-info
                                                    (or (:total-token-count token-info)
                                                        (:input-token-count token-info)
                                                        (:output-token-count token-info))))
                                       ($ :div.flex.flex-wrap.gap-1
                                          (when duration-ms
                                            ($ regular-detail/TimeCapsule {:key "time"
                                                                           :duration-ms duration-ms}))
                                          (when (and token-info
                                                     (or (:total-token-count token-info)
                                                         (:input-token-count token-info)
                                                         (:output-token-count token-info)))
                                            ($ regular-detail/TokenCountCapsule
                                               {:key "tokens"
                                                :input-token-count (:input-token-count token-info)
                                                :output-token-count (:output-token-count token-info)
                                                :total-token-count (:total-token-count token-info)})))))
                                  ($ :div.flex.items-center.justify-center.h-full.text-gray-500.text-sm
                                     ($ common/spinner {:size :small})
                                     ($ :span.ml-2 "Running..."))))))
                         ;; Evals Column - only non-selector evaluators
                         ($ :td {:className (:td common/table-classes)}
                            ($ :div.flex.flex-wrap.gap-1
                               (for [[eval-name metrics] non-selector-evals
                                     [metric-key metric-value] metrics]
                                 ($ regular-detail/EvaluatorCapsule
                                    {:key (str eval-name metric-key)
                                     :eval-name eval-name
                                     :metric-key metric-key
                                     :metric-value metric-value
                                     :eval-invoke (get-in run [:eval-initiates eval-name])
                                     :module-id module-id
                                     :columns-metadata columns-metadata}))
                               (for [[eval-name failure-info] (:eval-failures run)]
                                 ($ regular-detail/EvaluatorCapsule
                                    {:key (str eval-name "-failure")
                                     :eval-name eval-name
                                     :eval-failure failure-info
                                     :eval-invoke (get-in run [:eval-initiates eval-name])
                                     :module-id module-id
                                     :columns-metadata columns-metadata}))))))))))))))

(defui detail-page [{:keys [module-id dataset-id experiment-id]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:experiment-results module-id dataset-id experiment-id]
          :sente-event [:experiments/get-results {:module-id module-id
                                                  :dataset-id dataset-id
                                                  :experiment-id experiment-id}]
          :refetch-interval-ms 2000})
        [show-info? set-show-info] (uix/use-state false)
        [show-full-text? set-show-full-text] (uix/use-state false)
        inv-error (:invocation-error data)
        status (cond
                 inv-error :failed
                 (:finish-time-millis data) :completed
                 :else :running)]

    (cond
      loading? ($ :div.p-6.text-center.py-12 ($ common/spinner {:size :large}))
      error ($ :div.p-6.text-red-500.text-center.py-8 "Error loading experiment results: " error)

      data ($ :div.p-6.space-y-6
              ;; Header - reuse from regular-detail
              ($ regular-detail/ExperimentHeader
                 {:info (:experiment-info data)
                  :status status
                  :on-rerun #(let [form-props (-> (:experiment-info data)
                                                  forms/experiment-info->form-state
                                                  (assoc :module-id module-id
                                                         :dataset-id dataset-id))]
                               (state/dispatch [:modal/show-form :create-experiment form-props]))
                  :module-id module-id
                  :dataset-id dataset-id
                  :show-info? show-info?
                  :on-toggle-info #(set-show-info (not show-info?))})

              ;; Info panel
              (when show-info?
                ($ regular-detail/ExperimentInfoPanel {:info (:experiment-info data)}))

              ;; Error panel
              (when inv-error
                ($ regular-detail/ExperimentErrorPanel {:error-info inv-error}))

              ;; Comparative Results Table
              ($ ComparativeResultsTable {:data data
                                          :module-id module-id
                                          :show-full-text? show-full-text?
                                          :on-toggle-full-text #(set-show-full-text (not show-full-text?))}))
      :else ($ :div.p-6.text-center.py-12 "No experiment data found"))))