(ns com.rpl.agent-o-rama.ui.datasets.add-from-trace
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [clojure.string :as str]
   ["react" :refer [useEffect]]))

(defhook use-debounced-effect
  "Runs an effect after a specified delay when dependencies change.
  Cancels the previous effect if dependencies change before the delay has passed."
  [effect-fn delay-ms deps]
  (useEffect
   (fn []
      ;; Set up the timeout
     (let [handler (js/setTimeout effect-fn delay-ms)]
        ;; Return a cleanup function that clears the timeout.
        ;; This is the core of the debounce logic.
       #(js/clearTimeout handler)))
    ;; The effect re-runs whenever the dependencies change.
   (clj->js deps)))

(defui SourceDataPanel [{:keys [source-args source-result]}]
  ($ :div {:className "w-1/3 p-4 bg-gray-50 border-r overflow-auto"}
     ($ :h4 {:className "font-semibold mb-2"} "Source Data (from Trace)")
     ($ :div {:className "space-y-4"}
        ($ :div
           ($ :label {:className "text-xs font-medium text-gray-500"} "Input Arguments")
           ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"} (common/pp source-args)))
        ($ :div
           ($ :label {:className "text-xs font-medium text-gray-500"} "Result")
           ($ :pre {:className "text-xs bg-white p-2 rounded border mt-1"} (common/pp source-result))))))

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
        {:keys [module-id source-args source-result]} props

        ;; Local state for preview
        [preview-data set-preview-data] (uix/use-state nil)
        [preview-error set-preview-error] (uix/use-state nil)

        ;; Fetch available datasets
        {:keys [data loading?]} (queries/use-sente-query
                                 {:query-key [:datasets module-id]
                                  :sente-event [:datasets/get-all {:module-id module-id}]})]

    ;; Debounced effect for live preview
    (use-debounced-effect
     (fn []
       ;; Only run if we have required data
       (when (and (:value dataset-id-field) (not (str/blank? (:value input-template-field))))
         (sente/request! [:datasets/preview-from-trace
                          {:module-id module-id
                           :dataset-id (:value dataset-id-field)
                           :input-template (:value input-template-field)
                           :output-template (:value output-template-field)
                           :source-args source-args
                           :source-result source-result}]
                         5000
                         (fn [reply]
                           (if (:success reply)
                             (do
                               (set-preview-data (:data reply))
                               (set-preview-error nil))
                             (do
                               (set-preview-data nil)
                               (set-preview-error (:error reply))))))))
     300 ; debounce delay in ms
     [(:value dataset-id-field) (:value input-template-field) (:value output-template-field)])

    ($ :div {:className "flex h-full"}
       ($ SourceDataPanel {:source-args source-args :source-result source-result})
       ;; Center Panel: Controls
       ($ :div {:className "w-1/3 p-4 border-r overflow-auto"}
          ($ forms/form
             ($ :h4 {:className "font-semibold mb-4"} "Configuration")
                          ;; Custom select field for datasets
             ($ :div {:className "space-y-1"}
                ($ :label {:className "block text-sm font-medium text-gray-700"}
                   "Target Dataset"
                   ($ :span {:className "text-red-500 ml-1"} "*"))
                ($ :select {:className (str "w-full p-3 border rounded-md text-sm transition-colors "
                                            (if (:error dataset-id-field)
                                              "border-red-300 focus:ring-red-500 focus:border-red-500"
                                              "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))
                            :value (or (:value dataset-id-field) "")
                            :onChange #((:on-change dataset-id-field) (.. % -target -value))}
                   (if loading?
                     ($ :option {:value ""} "Loading datasets...")
                     (for [ds (:datasets data)]
                       ($ :option {:key (:dataset-id ds) :value (:dataset-id ds)} (:name ds)))))
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
       ($ PreviewPanel {:preview-data preview-data :error preview-error}))))

(forms/reg-form
 :add-from-trace
 {:steps [:main]
  :main
  {:initial-fields (fn [props] (merge {:dataset-id "" :input-template "$[0]" :output-template "$"} props))
   :validators {:dataset-id [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ AddFromTraceForm {:form-id form-id}))
   :modal-props (fn [props] {:title (or (:title props) "Add to Dataset") :submit-text "Add Example"})}
  :on-submit
  {:event
   (fn [_db form-state]
     ;; On submit, we call the add-from-trace endpoint that does both preview and add
     (let [{:keys [module-id]} form-state
           dataset-id (:dataset-id form-state)
           input-template (:input-template form-state)
           output-template (:output-template form-state)
           source-args (:source-args form-state)
           source-result (:source-result form-state)]
       [:datasets/add-from-trace
        {:module-id module-id
         :dataset-id dataset-id
         :input-template input-template
         :output-template output-template
         :source-args source-args
         :source-result source-result}]))
   :on-success-invalidate (fn [_db {:keys [module-id dataset-id]} _reply]
                            {:query-key-pattern [:dataset-examples module-id dataset-id]})}})