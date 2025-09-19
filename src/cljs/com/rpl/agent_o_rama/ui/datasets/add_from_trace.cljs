(ns com.rpl.agent-o-rama.ui.datasets.add-from-trace
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [clojure.string :as str]
   ["@heroicons/react/24/outline" :refer [ChevronDownIcon]]))

;; Removed - no longer needed for simplified form

;; Removed - no longer needed for simplified form

;; Removed - no longer needed for simplified form

(defui AddFromTraceForm [{:keys [form-id]}]
  (let [;; Form fields
        dataset-id-field (forms/use-form-field form-id :dataset-id)
        input-data-field (forms/use-form-field form-id :input-data)
        output-data-field (forms/use-form-field form-id :output-data)

;; Props passed when the form was shown
        props (state/use-sub [:forms form-id])
        {:keys [module-id source-args source-result source-emits]} props

        ;; Local state
        [dropdown-open? set-dropdown-open] (uix/use-state false)

        ;; Fetch available datasets
        {:keys [data loading?]} (queries/use-sente-query
                                 {:query-key [:datasets module-id]
                                  :sente-event [:datasets/get-all {:module-id module-id}]})]

    ($ :div {:className "max-w-4xl mx-auto p-6"}
       ($ forms/form
          ($ :div {:className "space-y-6"}
             ;; Dataset Selection
             ($ :div {:className "space-y-1"}
                ($ :label {:className "block text-sm font-medium text-gray-700"}
                   "Target Dataset"
                   ($ :span {:className "text-red-500 ml-1"} "*"))
                ($ :div {:className "relative"}
                   ($ :button {:type "button"
                               :className (str "inline-flex items-center justify-between w-full px-3 py-2 text-sm bg-white border rounded-md shadow-sm hover:bg-gray-50 "
                                               (if (:error dataset-id-field)
                                                 "border-red-300 focus:ring-red-500 focus:border-red-500"
                                                 "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                               :onClick #(set-dropdown-open (not dropdown-open?))
                               :disabled loading?}
                      ($ :span {:className "truncate"}
                         (cond
                           loading? "Loading datasets..."
                           (str/blank? (:value dataset-id-field)) "Select a dataset..."
                           :else (some-> (:datasets data)
                                         (->> (filter #(= (:dataset-id %) (:value dataset-id-field)))
                                              (first)
                                              (:name)))))
                      ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))
                   (when dropdown-open?
                     ($ :div {:className "origin-top-right absolute right-0 mt-1 w-full rounded-md shadow-lg bg-white ring-1 ring-black ring-opacity-5 z-50"
                              :onClick #(.stopPropagation %)}
                        ($ :div {:className "py-1"}
                           (if (seq (:datasets data))
                             (for [ds (:datasets data)]
                               ($ common/DropdownRow {:key (:dataset-id ds)
                                                      :label (:name ds)
                                                      :selected? (= (:dataset-id ds) (:value dataset-id-field))
                                                      :on-select #(do
                                                                    ((:on-change dataset-id-field) (:dataset-id ds))
                                                                    (set-dropdown-open false))
                                                      :delete-button nil}))
                             ($ :div {:className "px-4 py-2 text-sm text-gray-500"} "No datasets available"))))))
                (if (:error dataset-id-field)
                  ($ :p {:className "text-sm text-red-600 mt-1"} (:error dataset-id-field))
                  ($ :div {:className "mt-1 h-5"})))

             ;; Input Data
             ($ forms/form-field
                {:label "Input Data"
                 :type :textarea
                 :rows 8
                 :value (:value input-data-field)
                 :on-change (:on-change input-data-field)
                 :error (:error input-data-field)
                 :help-text "Edit the input data for this dataset example"})

             ;; Output Data  
             ($ forms/form-field
                {:label "Reference Output Data"
                 :type :textarea
                 :rows 8
                 :value (:value output-data-field)
                 :on-change (:on-change output-data-field)
                 :error (:error output-data-field)
                 :help-text "Edit the expected output data for this dataset example"}))))))

(forms/reg-form
 :add-from-trace
 {:steps [:main]
  :main
  {:initial-fields
   (fn [props]
     (let [{:keys [source-args source-result source-emits]} props
           ;; Use source-result if available, otherwise fall back to source-emits
           output-data (or source-result source-emits)]
       (merge
        {:dataset-id ""
         ;; Pre-fill input with source args, output with result/emits
         :input-data (common/pp source-args)
         :output-data (common/pp output-data)}
        props)))
   :validators {:dataset-id [forms/required]
                :input-data [forms/required forms/valid-json]
                :output-data [forms/required forms/valid-json]}
   :ui (fn [{:keys [form-id]}] ($ AddFromTraceForm {:form-id form-id}))
   :modal-props (fn [props] {:title (or (:title props) "Add to Dataset") :submit-text "Add Example"})}
  :on-submit
  {:event
   (fn [_db form-state]
     ;; On submit, we send the direct data - backend will validate JSON and schema
     (let [{:keys [module-id]} form-state
           dataset-id (:dataset-id form-state)
           input-data (:input-data form-state)
           output-data (:output-data form-state)]
       [:datasets/add-example
        {:module-id module-id
         :dataset-id dataset-id
         :input-data input-data
         :output-data output-data}]))
   :on-success-invalidate (fn [_db {:keys [module-id dataset-id]} _reply]
                            {:query-key-pattern [:dataset-examples module-id dataset-id]})}})