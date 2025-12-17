(ns com.rpl.agent-o-rama.ui.components.json-path-preview
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.rules-forms :refer [DatasetCombobox]]
   [clojure.string :as str]
   ["use-debounce" :refer [useDebounce]]))

(defui PreviewBox [{:keys [label result error loading? empty-msg data-testid]}]
  ($ :div.mt-1.text-xs
     {:data-testid data-testid}
     (when label ($ :div.text-gray-500.mb-1 label))
     (cond
       loading?
       ($ :div.text-gray-400.italic
          {:data-testid (str data-testid "-loading")}
          "Evaluating...")

       error
       ($ :div.text-red-500.bg-red-50.p-2.rounded.border.border-red-100
          {:data-testid (str data-testid "-error")}
          (str "Error: " error))

       (nil? result)
       ($ :div.text-gray-400.italic
          {:data-testid (str data-testid "-empty")}
          (or empty-msg "No result"))

       :else
       ($ :pre.bg-gray-100.p-2.rounded.border.border-gray-200.overflow-x-auto.font-mono.text-gray-800
          {:data-testid (str data-testid "-result")}
          (if (string? result) result (common/pp-json result))))))

(defui ExpressionPreview
  "Computes and displays a preview of a JSON path or template against a dataset."
  [{:keys [module-id dataset-id snapshot-name expression type source-field data-testid]
    :or {type :path source-field :input}}]

  (let [[debounced-expression] (useDebounce expression 500)
        should-fetch? (and module-id dataset-id (not (str/blank? debounced-expression)))

        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:preview-expression module-id dataset-id snapshot-name debounced-expression type source-field]
          :sente-event [:datasets/preview-expression
                        {:module-id module-id
                         :dataset-id dataset-id
                         :snapshot-name snapshot-name
                         :expression debounced-expression
                         :type type
                         :source-field source-field}]
          :enabled? should-fetch?})]

    ($ PreviewBox
       {:result (:result data)
        :error (or error (:error data))
        :loading? loading?
        :empty-msg (if (not dataset-id) "Select a dataset to preview" "No match found")
        :data-testid data-testid})))

;; Note: Now using DatasetCombobox from rules-forms
;; Circular dependency resolved by moving evaluator helper functions to common.cljs

(defui EvaluatorPreviewSection [{:keys [module-id input-path output-path ref-path show-input? show-output? show-ref?]}]
  (let [[selected-dataset set-selected-dataset] (uix/use-state nil)]
    ($ :div.mt-6.p-4.bg-gray-50.rounded-lg.border.border-gray-200
       ($ :h4.text-sm.font-medium.text-gray-900.mb-3 "Preview on Data")

       ($ :div.mb-4
          ($ :label.block.text-xs.font-medium.text-gray-500.mb-1 "Select Dataset for Preview")
          ($ SimpleDatasetSelector {:module-id module-id
                                    :value selected-dataset
                                    :on-change set-selected-dataset}))

       (when selected-dataset
         ($ :div.space-y-4
            (when (and show-input? (not (str/blank? input-path)))
              ($ :div
                 ($ :div.text-xs.font-medium.text-indigo-600 "Input Path Result:")
                 ($ ExpressionPreview {:module-id module-id
                                       :dataset-id selected-dataset
                                       :expression input-path
                                       :type :path
                                       :source-field :input})))

            (when (and show-output? (not (str/blank? output-path)))
              ($ :div
                 ($ :div.text-xs.font-medium.text-indigo-600 "Output Path Result:")
                 ($ ExpressionPreview {:module-id module-id
                                       :dataset-id selected-dataset
                                       :expression output-path
                                       :type :path
                                       :source-field :input})))

            (when (and show-ref? (not (str/blank? ref-path)))
              ($ :div
                 ($ :div.text-xs.font-medium.text-indigo-600 "Reference Output Path Result:")
                 ($ ExpressionPreview {:module-id module-id
                                       :dataset-id selected-dataset
                                       :expression ref-path
                                       :type :path
                                       :source-field :reference-output}))))))))
