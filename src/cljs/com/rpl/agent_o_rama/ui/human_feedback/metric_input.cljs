(ns com.rpl.agent-o-rama.ui.human-feedback.metric-input
  "Shared metric input component for human feedback forms.
   
   Used by:
   - Manual feedback form (add/edit feedback on invocations)
   - Queue item review form (reviewing items in a feedback queue)"
  (:require
   [uix.core :refer [defui $]]))

(defn- numeric-metric? [metric]
  (contains? metric :min))

(defn- category-metric? [metric]
  (contains? metric :categories))

(defui MetricInput
  "A unified input component for human feedback metrics.
   
   Props:
   - :metric - The metric definition (has :min/:max or :categories)
   - :label - Display label (defaults to metric :name if present, use false to hide)
   - :description - Optional description text
   - :required? - Whether the field is required
   - :value - Current value (string)
   - :on-change - Callback fn [new-value]
   - :error - Error message to display
   - :data-testid - Optional test ID for the input"
  [{:keys [metric label description required? value on-change error data-testid]}]
  (let [;; label=false means hide, label=nil means use metric name
        show-label? (not (false? label))
        display-label (if (false? label) nil (or label (:name metric)))
        is-category? (category-metric? metric)
        is-numeric? (numeric-metric? metric)]
    ($ :div
       ;; Label (optional)
       (when (and show-label? display-label)
         ($ :label.block.text-sm.font-medium.text-gray-700.mb-2
            display-label
            (when required?
              ($ :span.text-red-500.ml-1 "*"))
            (when description
              ($ :div.text-xs.text-gray-500.font-normal.mt-1 description))))

       ;; Input control based on metric type
       (cond
         is-category?
         ($ :select.w-full.p-2.border.border-gray-300.rounded-md.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
            {:value (or value "")
             :onChange #(on-change (.. % -target -value))
             :className (if error "border-red-500" "")
             :data-testid data-testid}
            ($ :option {:value ""} "-- Select --")
            (for [category (sort (:categories metric))]
              ($ :option {:key category :value category} category)))

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
                :className (if error "border-red-500" "")
                :data-testid data-testid})
            ($ :div.text-xs.text-gray-500.mt-1
               (str "Valid range: " (:min metric) " - " (:max metric))))

         :else
         ($ :div.text-gray-500.italic "Unknown metric type"))

       ;; Error message
       (when error
         ($ :div.text-sm.text-red-600.mt-1 error)))))
