(ns com.rpl.agent-o-rama.ui.datasets.add-from-trace
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [clojure.string :as str]
   ["react" :refer [useEffect]]
   ["use-debounce" :refer [useDebounce]]
   ["@heroicons/react/24/outline" :refer [ChevronDownIcon]]))

(defhook use-debounced-value
  "Returns a debounced value that only updates after the specified delay."
  [value delay-ms]
  (let [[debounced-value] (useDebounce value delay-ms)]
    debounced-value))

(defui SourceDataPanel [{:keys [source-type source-args source-result source-emits]}]
  ($ :div {:className "w-1/3 p-4 bg-gray-50 border-r overflow-auto"}
     ($ :h4 {:className "font-semibold mb-2"} "Source Data (from Trace)")
     ($ :div {:className "space-y-4"}
        ($ :div
           ($ :label {:className "text-xs font-medium text-gray-500"}
              (if (= source-type :agent) "Agent Initial Arguments" "Node Input Arguments"))
           ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"} (common/pp source-args)))
        ($ :div
           ($ :label {:className "text-xs font-medium text-gray-500"}
              (if (= source-type :agent) "Agent Final Result" "Node Emitted Data"))
           ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"}
              ;; Display the correct source for the output template
              (common/pp (if (= source-type :agent) source-result source-emits)))))))

(defui PreviewPanel [{:keys [preview-data error is-previewing]}]
  ($ :div {:className "w-1/3 p-4 overflow-auto"}
     ($ :div {:className "flex justify-between items-center mb-2"}
        ($ :h4 {:className "font-semibold"} "Live Preview")
        (when is-previewing
          ($ :div {:className "flex items-center gap-1 text-xs text-gray-500"}
             ($ common/spinner {:size :small})
             "Updating...")))
     (cond
       error ($ :div {:className "bg-red-50 p-2 rounded border border-red-200 text-red-700 text-xs whitespace-pre-wrap"} error)
       preview-data
       ($ :div {:className "space-y-4"}
          ;; Preview for Input
          ($ :div
             ($ :div {:className "flex justify-between items-center"}
                ($ :label {:className "text-xs font-medium text-gray-500"} "Dataset Input")
                (when-let [input-preview (:input preview-data)]
                  ($ :span {:className (if (:is-valid? input-preview)
                                         "text-xs font-medium text-green-600 bg-green-100 px-2 py-1 rounded-full"
                                         "text-xs font-medium text-red-600 bg-red-100 px-2 py-1 rounded-full")}
                     (if (:is-valid? input-preview) "Valid" "Invalid"))))
             ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"} (common/pp (:transformed-data (:input preview-data))))
             (when-let [err (:validation-error (:input preview-data))]
               ($ :p {:className "text-xs text-red-600 mt-1"} err)))
          ;; Preview for Reference Output
          ($ :div
             ($ :div {:className "flex justify-between items-center"}
                ($ :label {:className "text-xs font-medium text-gray-500"} "Dataset Reference Output")
                (when-let [output-preview (:output preview-data)]
                  ($ :span {:className (if (:is-valid? output-preview)
                                         "text-xs font-medium text-green-600 bg-green-100 px-2 py-1 rounded-full"
                                         "text-xs font-medium text-red-600 bg-red-100 px-2 py-1 rounded-full")}
                     (if (:is-valid? output-preview) "Valid" "Invalid"))))
             ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"} (common/pp (:transformed-data (:output preview-data))))
             (when-let [err (:validation-error (:output preview-data))]
               ($ :p {:className "text-xs text-red-600 mt-1"} err))))
       :else ($ :div {:className "text-sm text-gray-400 italic"} "Select a dataset to see a preview."))))

(defui AddFromTraceForm [{:keys [form-id]}]
  (let [;; Form fields
        dataset-id-field (forms/use-form-field form-id :dataset-id)
        input-template-field (forms/use-form-field form-id :input-template)
        output-template-field (forms/use-form-field form-id :output-template)

        ;; Props passed when the form was shown
        props (state/use-sub [:forms form-id])
        {:keys [module-id source-type source-args source-result source-emits]} props

        ;; Determine the correct source for the output template
        output-template-source (if (= source-type :agent) source-result source-emits)

        ;; Local state for preview
        [preview-data set-preview-data] (uix/use-state nil)
        [preview-error set-preview-error] (uix/use-state nil)
        [dropdown-open? set-dropdown-open] (uix/use-state false)
        [is-previewing set-is-previewing] (uix/use-state false)

        ;; Fetch available datasets
        {:keys [data loading?]} (queries/use-sente-query
                                 {:query-key [:datasets module-id]
                                  :sente-event [:datasets/get-all {:module-id module-id}]})

        ;; Debounced values for form fields
        debounced-dataset-id (use-debounced-value (:value dataset-id-field) 300)
        debounced-input-template (use-debounced-value (:value input-template-field) 300)
        debounced-output-template (use-debounced-value (:value output-template-field) 300)]

    ;; Effect for live preview that runs when debounced values change
    (useEffect
     (fn []
       ;; Only run if we have required data
       (when (and debounced-dataset-id (not (str/blank? debounced-input-template)))
         (set-is-previewing true)
         (sente/request! [:datasets/preview-from-trace
                          {:module-id module-id
                           :dataset-id debounced-dataset-id
                           :input-template debounced-input-template
                           :output-template debounced-output-template
                           :source-args source-args
                           :source-output output-template-source}]
                         15000
                         (fn [reply]
                           (set-is-previewing false)
                           (cond
                             ;; Keep last good data on timeout; don't show error, just stop spinner
                             (= reply :chsk/timeout) nil
                             ;; Success: show preview, clear any previous error
                             (:success reply) (do
                                                (set-preview-data (:data reply))
                                                (set-preview-error nil))
                             ;; Error (non-timeout): show error but keep last preview
                             :else (set-preview-error (:error reply))))))
       ;; No cleanup needed
       js/undefined)
     ;; Dependencies - effect runs when debounced values change
     (clj->js [debounced-dataset-id debounced-input-template debounced-output-template]))

    ($ :div {:className "flex h-full"}
       ($ SourceDataPanel {:source-type source-type
                           :source-args source-args
                           :source-result source-result
                           :source-emits source-emits})
       ;; Center Panel: Controls
       ($ :div {:className "w-1/3 p-4 border-r overflow-auto"}
          ($ forms/form
             ($ :h4 {:className "font-semibold mb-4"} "Configuration")
             ;; Custom dropdown field for datasets
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
             ($ forms/form-field
                {:label "Input Template (JSONPath)" :type :textarea :rows 4
                 :value (:value input-template-field) :on-change (:on-change input-template-field)
                 :error (:error input-template-field)})
             ($ forms/form-field
                {:label "Reference Output Template (JSONPath)" :type :textarea :rows 4
                 :value (:value output-template-field) :on-change (:on-change output-template-field)
                 :error (:error output-template-field)})))
       ($ PreviewPanel {:preview-data preview-data :error preview-error :is-previewing is-previewing}))))

(forms/reg-form
 :add-from-trace
 {:steps [:main]
  :main
  {:initial-fields
   (fn [props]
     (merge
      {:dataset-id ""
       ;; Simple defaults - just use $ for everything
       :input-template "$"
       :output-template "$"}
      props))
   :validators {:dataset-id [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ AddFromTraceForm {:form-id form-id}))
   :modal-props (fn [props] {:title (or (:title props) "Add to Dataset") :submit-text "Add Example"})}
  :on-submit
  {:event
   (fn [_db form-state]
     ;; On submit, we call the add-from-trace endpoint that does both preview and add
     (let [{:keys [module-id source-type]} form-state
           dataset-id (:dataset-id form-state)
           input-template (:input-template form-state)
           output-template (:output-template form-state)
           source-args (:source-args form-state)
           source-result (:source-result form-state)
           source-emits (:source-emits form-state)
           output-template-source (if (= source-type :agent) source-result source-emits)]
       [:datasets/add-from-trace
        {:module-id module-id
         :dataset-id dataset-id
         :input-template input-template
         :output-template output-template
         :source-args source-args
         :source-output output-template-source}]))
   :on-success-invalidate (fn [_db {:keys [module-id dataset-id]} _reply]
                            {:query-key-pattern [:dataset-examples module-id dataset-id]})}})