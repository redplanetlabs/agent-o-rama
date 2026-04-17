(ns com.rpl.agent-o-rama.ui.datasets.add-from-trace
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [re-frame.query :as rfq]
   [clojure.string :as str]
   ["react" :refer [useEffect]]
   ["use-debounce" :refer [useDebounce]]))

(defn parse-json->cljs [s]
  (try
    (-> (js/JSON.parse s)
        (js->clj :keywordize-keys false))
    (catch :default e
      ::parse-error)))

(defui AddFromTraceForm [{:keys [form-id]}]
  (let [;; Form fields
        dataset-id-field (forms/use-form-field form-id :dataset-id)
        input-data-field (forms/use-form-field form-id :input-data)
        output-data-field (forms/use-form-field form-id :output-data)

        props (use-subscribe [::forms/form form-id])
        {:keys [module-id source-args source-result source-emits error]} (merge (:fields props {}) (:meta props {}))

        ;; Local state
        [validation-state set-validation-state] (uix/use-state nil)
        [is-validating set-is-validating] (uix/use-state false)

        ;; Debounced values for real-time validation
        [debounced-dataset-id, _] (useDebounce (:value dataset-id-field) 500)
        [debounced-input-data, _] (useDebounce (:value input-data-field) 500)
        [debounced-output-data, _] (useDebounce (:value output-data-field) 500)

        ;; Fetch dataset schemas for the selected dataset
        {:keys [data]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-datasets/get-all!! {:module-id module-id}])
        loading? (#{:loading :idle} query-status)]

    (useEffect
     (fn []
       (when (and debounced-dataset-id
                  (not (str/blank? debounced-dataset-id))
                  (not (str/blank? debounced-input-data))
                  (not (str/blank? debounced-output-data)))
         (let [parsed-input (parse-json->cljs debounced-input-data)
               parsed-output (parse-json->cljs debounced-output-data)]
           (cond
             (= ::parse-error parsed-input)
             (set-validation-state {:input {:parse-error "Invalid JSON format"}})

             (= ::parse-error parsed-output)
             (set-validation-state {:output {:parse-error "Invalid JSON format"}})

             :else
             (do
               (set-is-validating true)
               (-> (rpc/call ::rpc-datasets/validate-direct-data!!
                               {:module-id module-id
                                :dataset-id debounced-dataset-id
                                :input parsed-input
                                :output parsed-output})
                              (.then (fn [data]
                                       (set-is-validating false)
                                       (set-validation-state data)))
                              (.catch (fn [_err] (set-is-validating false))))))))
       js/undefined)
     (clj->js [debounced-dataset-id debounced-input-data debounced-output-data]))

    ($ forms/form
       ($ :div.space-y-4.p-4
             ;; Dataset Selection
          ($ ss/SearchableSelector
             {:module-id module-id
              :value (:value dataset-id-field)
              :on-change (:on-change dataset-id-field)
              :rfq-key ::rpc-datasets/get-all!!
              :items-key :datasets
              :item-id-fn :dataset-id
              :item-label-fn :name
              :item-sublabel-fn #(str (:dataset-id %))
              :placeholder "Type to search datasets..."
              :label "Dataset ID"
              :required? true
              :error (:error dataset-id-field)
              :data-testid "dataset-selector"})

             ;; Schema display (place right after the dataset dropdown block)
          (let [selected-ds (some->> (:datasets data)
                                     (filter #(= (:dataset-id %) (:value dataset-id-field)))
                                     first)]
            (when (and selected-ds
                       (or (:input-json-schema selected-ds)
                           (:output-json-schema selected-ds)))
              ($ :div {:className "space-y-4 p-4 bg-gray-50 border rounded-md"}
                 ($ :h4 {:className "font-semibold text-gray-700"} "Dataset Schema")

                    ;; Input schema
                 (when-let [in-schema (:input-json-schema selected-ds)]
                   ($ :div {:className "space-y-2"}
                      ($ :label {:className "block text-sm font-medium text-gray-600"} "Expected Input Format")
                      ($ :pre {:className "text-xs bg-white p-3 rounded border overflow-auto max-h-48"}
                         (common/pp-json in-schema))))

                    ;; Output schema
                 (when-let [out-schema (:output-json-schema selected-ds)]
                   ($ :div {:className "space-y-2"}
                      ($ :label {:className "block text-sm font-medium text-gray-600"} "Expected Output Format")
                      ($ :pre {:className "text-xs bg-white p-3 rounded border overflow-auto max-h-48"}
                         (common/pp-json out-schema)))))))

                                        ;
                                        ; Input Data
          ($ :div
             ($ forms/form-field
                {:label "Input Data"
                 :type :textarea
                 :rows 8
                 :value (:value input-data-field)
                 :on-change (:on-change input-data-field)
                 :error (:error input-data-field)
                 :help-text "Edit the input data for this dataset example"})
             (when validation-state
               (let [input-validation (:input validation-state)]
                 ($ :div {:className "mt-1 text-sm"}
                    (cond
                      (:parse-error input-validation)
                      ($ :p {:className "text-red-600"} (:parse-error input-validation))

                      (:validation-error input-validation)
                      ($ :p {:className "text-red-600"} (str "Schema error: " (:validation-error input-validation)))

                      (:is-valid? input-validation)
                      ($ :p {:className "text-green-600"} "✓ Valid input data")

                      :else nil)))))

             ;; Output Data  
          ($ :div
             ($ forms/form-field
                {:label "Reference Output Data"
                 :type :textarea
                 :rows 8
                 :value (:value output-data-field)
                 :on-change (:on-change output-data-field)
                 :error (:error output-data-field)
                 :help-text "Edit the expected output data for this dataset example"})
             (when validation-state
               (let [output-validation (:output validation-state)]
                 ($ :div {:className "mt-1 text-sm"}
                    (cond
                      (:parse-error output-validation)
                      ($ :p {:className "text-red-600"} (:parse-error output-validation))

                      (:validation-error output-validation)
                      ($ :p {:className "text-red-600"} (str "Schema error: " (:validation-error output-validation)))

                      (:is-valid? output-validation)
                      ($ :p {:className "text-green-600"} "✓ Valid output data")

                      :else nil)))))

             ;; Display backend validation errors
          ($ forms/form-error {:error error})))))

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
         ;; Pre-fill input with source args, output with result/emits as JSON
         :input-data (common/pp-json source-args)
         :output-data (common/pp-json output-data)}
        props)))
   :validators {:dataset-id [forms/required]
                :input-data [forms/required]
                :output-data [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ AddFromTraceForm {:form-id form-id}))
   :modal-props (fn [props] {:title (or (:title props) "Add to Dataset") :submit-text "Add Example"})}
  :on-submit
  {:mutation
   (fn [_db form-state]
     (let [{:keys [module-id]} form-state
           dataset-id (:dataset-id form-state)
           input-str (:input-data form-state)
           output-str (:output-data form-state)
           parsed-input (parse-json->cljs input-str)
           parsed-output (parse-json->cljs output-str)]
       (cond
         (= ::parse-error parsed-input)
         (do (forms/set-error! (:form-id form-state) "Input is not valid JSON")
             nil)
         (= ::parse-error parsed-output)
         (do (forms/set-error! (:form-id form-state) "Output is not valid JSON")
             nil)
         :else
         [::rpc-datasets/add-direct-data!!
          {:module-id module-id
           :dataset-id dataset-id
           :input parsed-input
           :output parsed-output}])))
   :rfq-invalidate-tags (fn [_db {:keys [module-id dataset-id]} _reply]
                          [[:dataset-examples module-id dataset-id]])}})
