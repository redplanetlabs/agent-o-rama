(ns com.rpl.agent-o-rama.ui.human-feedback.manual-feedback
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [com.rpl.agent-o-rama.ui.human-feedback.metric-input :as metric-input]
   [com.rpl.agent-o-rama.ui.human-feedback.common :as hf-common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]))

;; Separate component for each metric row in the manual feedback form
;; Shows selector if no metric chosen, or input field if metric is selected
(defui MetricRow [{:keys [form-id idx metric-data editing? on-remove on-select module-id]}]
  ;; metric-data has :name, :metric (the definition), :value, :required
  (let [value-field (forms/use-form-field form-id [:metrics idx :value])
        metric-name (:name metric-data)
        has-metric-name? (some? metric-name)
        has-definition? (some? (:metric metric-data))
        
        ;; Fetch metric definition if we have a name but no definition
        fetch-result (when (and has-metric-name? (not has-definition?))
                       (queries/use-sente-query
                        {:query-key [:human-metric-definition module-id metric-name]
                         :sente-event [:human-feedback/get-metrics
                                       {:module-id module-id
                                        :filters {:search-string metric-name}}]}))
        
        ;; Extract the metric definition from the fetch result
        fetched-metric (when fetch-result
                         (let [items (get-in fetch-result [:data :items])]
                           (->> items
                                (filter #(= (:name %) metric-name))
                                first
                                :metric)))
        
        ;; Check if query completed but metric not found
        query-completed? (and fetch-result 
                              (not (:loading? fetch-result))
                              (not (:error fetch-result)))
        metric-not-found? (and query-completed? (nil? fetched-metric))
        
        ;; Use fetched definition if available, otherwise use what was passed in
        ;; Use :not-found sentinel value when metric doesn't exist
        metric-def (cond
                     (:metric metric-data) (:metric metric-data)
                     fetched-metric fetched-metric
                     metric-not-found? :not-found
                     :else nil)]

    (if has-metric-name?
      ;; Show input field when metric is selected
      ($ metric-input/MetricInput
         {:metric metric-def
          :label metric-name
          :required? (:required metric-data)
          :value (:value value-field)
          :on-change (:on-change value-field)
          :on-remove on-remove  ;; Allow removal in both add and edit modes
          :data-testid (str "metric-value-" idx)})

      ;; Show selector when no metric chosen
      ($ :div.flex.items-start.gap-2
         ($ :div.flex-1
            ($ ss/SearchableSelector
               {:module-id module-id
                :value nil
                :on-change (fn [metric-name opts]
                             (when metric-name
                               (on-select metric-name opts)))
                :sente-event-fn (fn [mid search-string]
                                  [:human-feedback/get-metrics
                                   {:module-id mid
                                    :filters {:search-string search-string}}])
                :items-key :items
                :item-id-fn :name
                :item-label-fn :name
                :item-sublabel-fn (fn [item]
                                    (let [m (:metric item)]
                                      (cond
                                        (contains? m :categories) (str "Categorical: " (str/join ", " (:categories m)))
                                        (contains? m :min) (str "Numeric: " (:min m) " - " (:max m))
                                        :else "")))
                :placeholder "Select metric..."
                :label "Metric"
                :hide-label? true
                :data-testid (str "metric-selector-" idx)}))

         ;; Remove button
         ($ :button.text-red-600.hover:text-red-800.p-2.rounded.mt-1
            {:type "button"
             :onClick on-remove}
            "Remove")))))

(defui ManualFeedbackForm [{:keys [form-id]}]
  (let [props (state/use-sub [:forms form-id])
        {:keys [module-id editing?]} props

        ;; Form fields
        metrics-field (forms/use-form-field form-id :metrics)
        reviewer-name-field (forms/use-form-field form-id :reviewer-name)
        comment-field (forms/use-form-field form-id :comment)]

    ($ :div.space-y-4.p-4
       ;; Reviewer name
       ($ forms/form-field
          {:label "Reviewer Name"
           :value (:value reviewer-name-field)
           :on-change (:on-change reviewer-name-field)
           :error (:error reviewer-name-field)
           :required? true
           :placeholder "Your name"
           :data-testid "reviewer-name-input"})

       ;; Metrics section
       ($ :div.space-y-2
          ($ :label.block.text-sm.font-medium.text-gray-700
             "Metrics")

          ($ :div.text-sm.text-gray-500.mb-2
             "Add metrics to evaluate this invocation")

          ;; Metric list
          ($ :div.space-y-2
             (vec
              (for [[idx metric-data] (map-indexed vector (or (:value metrics-field) []))]
                ($ MetricRow
                   {:key idx
                    :form-id form-id
                    :idx idx
                    :module-id module-id
                    :metric-data metric-data
                    :editing? editing?
                    :on-remove #(let [current-metrics (:value metrics-field)
                                      updated-metrics (vec (concat (subvec current-metrics 0 idx)
                                                                   (subvec current-metrics (inc idx))))]
                                  ((:on-change metrics-field) updated-metrics))
                    :on-select (fn [metric-name opts]
                                 (let [item (:item opts)
                                       metric-def (:metric item)
                                       current-metrics (:value metrics-field)
                                       ;; Check if already added in another row
                                       already-added? (some #(= (:name %) metric-name) current-metrics)]
                                   (when-not already-added?
                                     (let [updated-metrics (assoc-in current-metrics [idx]
                                                                     {:name (:name item)
                                                                      :metric metric-def
                                                                      :value ""
                                                                      :required false})]
                                       ((:on-change metrics-field) updated-metrics)))))}))))

          ;; Add metric button
          ($ :button.w-full.px-3.py-2.border-2.border-dashed.border-gray-300.rounded-md.text-gray-600.hover:border-gray-400.hover:text-gray-700.transition-colors
             {:data-testid "add-metric-button"
              :type "button"
              :onClick #(let [current-metrics (or (:value metrics-field) [])
                              new-metric {:name nil
                                          :metric nil
                                          :value ""
                                          :required false}
                              updated-metrics (conj current-metrics new-metric)]
                          ((:on-change metrics-field) updated-metrics))}
             "+ Add Metric")

          ;; Error message
          (when (:error metrics-field)
            ($ :p.text-sm.text-red-600.mt-1 (:error metrics-field))))

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
                     (merge {:reviewer-name (hf-common/get-reviewer-name)
                             :metrics []
                             :comment ""}
                            props))
   :validators {:reviewer-name [forms/required]
                :metrics [(fn [metrics form-state]
                            ;; New validation rules:
                            ;; 1. If metrics added with empty values → FAIL
                            ;; 2. If no metrics but has comment → PASS
                            ;; 3. If no metrics and no comment → FAIL
                            (let [has-metrics? (and metrics (seq metrics))
                                  has-comment? (not (str/blank? (:comment form-state)))]
                              (cond
                                ;; If metrics exist, validate each one
                                has-metrics?
                                (let [errors (keep-indexed
                                              (fn [idx {:keys [name metric value required]}]
                                                (cond
                                                  ;; Metric added but not selected yet (no name)
                                                  (nil? name)
                                                  "Please select a metric or remove the empty row"

                                                  ;; Metric selected but no value provided
                                                  (str/blank? value)
                                                  (str name " requires a value")

                                                  ;; Required metrics must have value (redundant but explicit)
                                                  (and required (str/blank? value))
                                                  (str name " is required")

                                                  ;; Categorical: must be one of the categories
                                                  (and (contains? metric :categories)
                                                       (not (str/blank? value))
                                                       (not (contains? (:categories metric) value)))
                                                  (str name " must be one of: " (str/join ", " (:categories metric)))

                                                  ;; Numeric: must be in range
                                                  (and (contains? metric :min)
                                                       (not (str/blank? value)))
                                                  (let [num-val (js/parseInt value 10)]
                                                    (cond
                                                      (js/isNaN num-val)
                                                      (str name " must be a number")

                                                      (< num-val (:min metric))
                                                      (str name " must be at least " (:min metric))

                                                      (> num-val (:max metric))
                                                      (str name " must be at most " (:max metric))

                                                      :else nil))

                                                  :else nil))
                                              metrics)]
                                  (when (seq errors)
                                    (first errors)))

                                ;; No metrics and no comment → FAIL
                                (and (not has-metrics?) (not has-comment?))
                                "Please provide either metrics or a comment"

                                ;; No metrics but has comment → PASS (return nil)
                                :else nil)))]}
   :ui (fn [{:keys [form-id]}] ($ ManualFeedbackForm {:form-id form-id}))
   :modal-props (fn [props]
                  {:title (if (:editing? props) "Edit Feedback" "Add Feedback")
                   :submit-text (if (:editing? props) "Save" "Submit")})}

  :on-submit
  {:event (fn [db form-state]
            (let [{:keys [form-id module-id agent-name invoke-id node-task-id node-invoke-id
                          reviewer-name metrics comment feedback-id editing?]} form-state
                  ;; Save reviewer name to localStorage
                  _ (when-not (str/blank? reviewer-name)
                      (hf-common/save-reviewer-name! reviewer-name))
                  ;; Convert metrics to scores map
                  ;; metric-data has :name, :metric, :value, :required
                  scores (into {} (map (fn [{:keys [name value]}]
                                         [name value])
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
                 (let [{:keys [invoke-id module-id agent-name]} form-state]
                   ;; Reload the invocation to show updated feedback
                   ;; Note: modal/hide and form/clear are already handled by the form framework
                   (state/dispatch [:invocation/start-graph-loading
                                    {:invoke-id invoke-id
                                     :module-id module-id
                                     :agent-name agent-name}])))}})
