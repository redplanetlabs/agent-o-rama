(ns com.rpl.agent-o-rama.ui.human-feedback.manual-feedback
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [com.rpl.agent-o-rama.ui.human-feedback.metric-input :as metric-input]
   [clojure.string :as str]))

;; Separate component for each metric input field in the manual feedback form
;; This is necessary because React hooks can't be called inside loops
(defui MetricInputField [{:keys [form-id idx metric-data editing? on-remove]}]
  (let [metric (:metric metric-data)
        value-field (forms/use-form-field form-id [:metrics idx :value])]
    ($ :div.p-3.bg-gray-50.rounded-md.border.border-gray-200
       {:data-testid (str "metric-field-" idx)}
       ;; Header with name and remove button
       ($ :div.flex.items-center.justify-between.mb-2
          ($ :span.text-sm.font-medium.text-gray-700
             (:name metric)
             (when (:required metric-data)
               ($ :span.text-red-500.ml-1 "*")))
          (when-not editing?
            ($ :button.text-red-600.hover:text-red-800.text-sm
               {:type "button"
                :data-testid (str "remove-metric-" idx)
                :onClick on-remove}
               "Remove")))

       ;; Use shared metric input component (without its own label since we show it above)
       ($ metric-input/MetricInput
          {:metric metric
           :label false  ;; Don't show label, we have our own header
           :value (:value value-field)
           :on-change (:on-change value-field)
           :error (:error value-field)
           :data-testid (str "metric-value-" idx)}))))

(defui ManualFeedbackForm [{:keys [form-id]}]
  (let [props (state/use-sub [:forms form-id])
        {:keys [module-id editing?]} props

        ;; Form fields
        metrics-field (forms/use-form-field form-id :metrics)
        reviewer-name-field (forms/use-form-field form-id :reviewer-name)
        comment-field (forms/use-form-field form-id :comment)]

    ($ :div.space-y-4
       ;; Reviewer name
       ($ forms/form-field
          {:label "Reviewer Name"
           :value (:value reviewer-name-field)
           :on-change (:on-change reviewer-name-field)
           :error (:error reviewer-name-field)
           :required? true
           :placeholder "Your name"
           :data-testid "reviewer-name-input"})

       ;; Selected metrics
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
             "Metrics"
             ($ :span.text-red-500.ml-1 "*"))

          ;; Display selected metrics with their input fields
          (if (and (:value metrics-field) (seq (:value metrics-field)))
            ($ :div.space-y-3
               (for [[idx metric-data] (map-indexed vector (:value metrics-field))]
                 ($ MetricInputField
                    {:key idx
                     :form-id form-id
                     :idx idx
                     :metric-data metric-data
                     :editing? editing?
                     :on-remove #(let [current-metrics (:value metrics-field)
                                       updated-metrics (vec (concat (subvec current-metrics 0 idx)
                                                                    (subvec current-metrics (inc idx))))]
                                   ((:on-change metrics-field) updated-metrics))})))

            ($ :div.text-sm.text-gray-500.italic.py-2
               "No metrics selected")))

       ;; Add metric button (only if not editing)
       (when-not editing?
         ($ :div
            ($ ss/SearchableSelector
               {:module-id module-id
                :value nil  ;; Always empty for adding
                :on-change (fn [metric-name opts]
                             (when metric-name
                               (let [current-metrics (or (:value metrics-field) [])
                                     ;; Check if already added
                                     already-added? (some #(= (:name (:metric %)) metric-name) current-metrics)]
                                 (when-not already-added?
                                   (let [metric (:item opts)
                                         new-metric {:metric metric
                                                     :value ""
                                                     :required false}
                                         updated-metrics (conj current-metrics new-metric)]
                                     ((:on-change metrics-field) updated-metrics))))))
               :sente-event-fn (fn [mid search-string]
                                 [:human-feedback/get-metrics
                                  {:module-id mid
                                   :filters {:search-string search-string}}])
               :items-key :items
                :item-id-fn :name
                :item-label-fn :name
                :item-sublabel-fn (fn [m]
                                    (cond
                                      (contains? m :categories) (str "Categorical: " (str/join ", " (:categories m)))
                                      (contains? m :min) (str "Numeric: " (:min m) " - " (:max m))
                                      :else ""))
                :placeholder "Add a metric..."
                :label "Add Metric"
                :hide-label? false
                :allow-clear? true
                :data-testid "add-metric-selector"})))

       ;; Comment
       ($ forms/form-field
          {:label "Comment (Optional)"
           :type :textarea
           :rows 3
           :value (:value comment-field)
           :on-change (:on-change comment-field)
           :placeholder "Optional comment about this feedback..."
           :data-testid "feedback-comment-input"}))))

;; Register form for adding/editing manual feedback
(forms/reg-form
 :add-manual-feedback
 {:steps [:main]
  :main
  {:initial-fields (fn [props]
                     (merge {:reviewer-name ""
                             :metrics []
                             :comment ""}
                            props))
   :validators {:reviewer-name [forms/required]
                :metrics [(fn [metrics]
                            (when (or (nil? metrics) (empty? metrics))
                              "At least one metric is required"))
                          (fn [metrics]
                            ;; Validate each metric value
                            (let [errors (keep-indexed
                                          (fn [idx {:keys [metric value required]}]
                                            (let [metric-name (:name metric)]
                                              (cond
                                                ;; Required metrics must have value
                                                (and required (str/blank? value))
                                                (str metric-name " is required")

                                                ;; Categorical: must be one of the categories
                                                (and (contains? metric :categories)
                                                     (not (str/blank? value))
                                                     (not (contains? (:categories metric) value)))
                                                (str metric-name " must be one of: " (str/join ", " (:categories metric)))

                                                ;; Numeric: must be in range
                                                (and (contains? metric :min)
                                                     (not (str/blank? value)))
                                                (let [num-val (js/parseInt value 10)]
                                                  (cond
                                                    (js/isNaN num-val)
                                                    (str metric-name " must be a number")

                                                    (< num-val (:min metric))
                                                    (str metric-name " must be at least " (:min metric))

                                                    (> num-val (:max metric))
                                                    (str metric-name " must be at most " (:max metric))

                                                    :else nil))

                                                :else nil)))
                                          metrics)]
                              (when (seq errors)
                                (first errors))))]}
   :ui (fn [{:keys [form-id]}] ($ ManualFeedbackForm {:form-id form-id}))
   :modal-props (fn [props]
                  {:title (if (:editing? props) "Edit Feedback" "Add Feedback")
                   :submit-text (if (:editing? props) "Save" "Submit")})}

  :on-submit
  {:event (fn [db form-state]
            (let [{:keys [form-id module-id agent-name invoke-id node-task-id node-invoke-id
                          reviewer-name metrics comment feedback-id editing?]} form-state
                  ;; Convert metrics to scores map
                  scores (into {} (map (fn [{:keys [metric value]}]
                                         [(keyword (:name metric)) value])
                                       (filter #(not (str/blank? (:value %))) metrics)))]
              (if editing?
                [:human-feedback/edit-feedback
                 {:module-id module-id
                  :agent-name agent-name
                  :invoke-id invoke-id
                  :node-task-id node-task-id
                  :node-invoke-id node-invoke-id
                  :feedback-id feedback-id
                  :reviewer-name reviewer-name
                  :scores scores
                  :comment comment}]
                [:human-feedback/add-feedback
                 {:module-id module-id
                  :agent-name agent-name
                  :invoke-id invoke-id
                  :node-task-id node-task-id
                  :node-invoke-id node-invoke-id
                  :reviewer-name reviewer-name
                  :scores scores
                  :comment comment}])))
   :on-success (fn [db form-state reply]
                 (let [{:keys [form-id invoke-id module-id agent-name]} form-state]
                   [[:modal/hide]
                    [:form/clear form-id]
                    ;; Reload the invocation to show updated feedback
                    [:invocation/start-graph-loading
                     {:invoke-id invoke-id
                      :module-id module-id
                      :agent-name agent-name}]]))}})
