(ns com.rpl.agent-o-rama.ui.human-feedback.metric-input
  "Shared metric input component for human feedback forms.
   
   Used by:
   - Manual feedback form (add/edit feedback on invocations)
   - Queue item review form (reviewing items in a feedback queue)"
  (:require
   [uix.core :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]))

(defn- numeric-metric? [metric]
  (contains? metric :min))

(defn- category-metric? [metric]
  (contains? metric :categories))

(defn- validate-metric-value
  "Returns error string if value is invalid for the metric, nil otherwise."
  [metric value]
  (cond
    ;; Empty value - no inline error (let form-level required validation handle it)
    (or (nil? value) (= value ""))
    nil

    ;; Numeric validation
    (numeric-metric? metric)
    (let [int-val (js/parseInt value 10)]
      (cond
        (js/isNaN int-val) "Must be an integer"
        (< int-val (:min metric)) (str "Must be at least " (:min metric))
        (> int-val (:max metric)) (str "Must be at most " (:max metric))
        :else nil))

    ;; Categorical - value must be in categories
    (category-metric? metric)
    (when-not (contains? (:categories metric) value)
      (str "Must be one of: " (clojure.string/join ", " (:categories metric))))

    :else nil))

(defui MetricInput
  "A unified input component for human feedback metrics.
   
   Props:
   - :metric - The metric definition (has :min/:max or :categories)
   - :label - Display label (required)
   - :description - Optional description text
   - :required? - Whether the field is required
   - :value - Current value (string)
   - :on-change - Callback fn [new-value]
   - :on-remove - Optional callback for remove button
   - :error - External error message (inline validation also applied)
   - :data-testid - Optional test ID for the input"
  [{:keys [metric label description required? value on-change on-remove error data-testid]}]
  (let [metric-not-found? (= metric :not-found)
        is-category? (category-metric? metric)
        is-numeric? (numeric-metric? metric)
        ;; Inline validation
        inline-error (validate-metric-value metric value)
        ;; Required validation
        required-error (when (and required? (or (nil? value) (= value "")))
                         "This field is required")
        display-error (or error inline-error required-error)]
    ($ :div.p-3.bg-gray-50.rounded-md.border.border-gray-200
       ;; Header with label and optional remove button
       ($ :div.flex.items-center.justify-between.mb-2
          ($ :span.text-sm.font-medium.text-gray-700
             label
             (when required?
               ($ :span.text-red-500.ml-1 "*")))
          (when on-remove
            ($ :button.text-red-600.hover:text-red-800.text-sm
               {:type "button"
                :onClick on-remove}
               "Remove")))

       ;; Description if provided
       (when description
         ($ :div.text-xs.text-gray-500.mb-2 description))

       ;; Input control based on metric type
       (cond
         metric-not-found?
         ($ :div.p-3.bg-red-50.border.border-red-200.rounded-md
            ($ :div.text-sm.text-red-600
               (str "Metric \"" label "\" not found. It may have been deleted.")))

         is-category?
         (let [sorted-categories (sort (:categories metric))
               dropdown-items (mapv (fn [category]
                                      {:key category
                                       :label category
                                       :selected? (= value category)
                                       :on-select #(on-change category)})
                                    sorted-categories)
               display-text (if (and value (not= value ""))
                              value
                              "-- Select --")]
           ($ common/Dropdown
              {:display-text display-text
               :items dropdown-items
               :disabled? false
               :full-width? false
               :data-testid data-testid}))

         is-numeric?
         ($ :div
            ($ :input.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
               {:type "number"
                :min (:min metric)
                :max (:max metric)
                :step 1
                :value (or value "")
                :onChange #(let [raw (.. % -target -value)
                                 int-val (js/parseInt raw 10)]
                             (on-change (if (js/isNaN int-val) "" (str int-val))))
                :placeholder (str (:min metric) " - " (:max metric))
                :className (if display-error "border-red-500" "")
                :data-testid data-testid})
            ($ :div.text-xs.text-gray-500.mt-1
               (str "Valid range: " (:min metric) " - " (:max metric))))

         ;; No metric definition - show loading spinner (metric definitions still loading)
         :else
         ($ :div.flex.items-center.justify-center.p-4
            ($ common/spinner {:size :medium})))

       ;; Error message
       (when display-error
         ($ :div.text-sm.text-red-600.mt-1 display-error)))))
