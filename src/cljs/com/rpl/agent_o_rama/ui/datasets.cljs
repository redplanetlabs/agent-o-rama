(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon XMarkIcon LockClosedIcon InformationCircleIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]
   [com.rpl.specter :as s]))

(def example-schema "{
  \"type\": \"object\",
  \"properties\": {
    \"context\": {
      \"type\": \"string\",
      \"description\": \"Information about the user\"
    },
    \"prompt\": {
      \"x-javaType\": \"dev.langchain4j.data.message.UserMessage\",
    }
  },
  \"required\": [\"context\", \"prompt\"]
}")

;; =============================================================================
;; MODAL FOR CREATING DATASETS
;; =============================================================================

(defui CreateDatasetForm [{:keys [form-id]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)
        input-schema-field (forms/use-form-field form-id :input-schema)
        output-schema-field (forms/use-form-field form-id :output-schema)]

    ($ forms/form
       ($ forms/form-field {:label "Name"
                            :value (:value name-field)
                            :on-change (:on-change name-field)
                            :error (:error name-field)
                            :required? true})
       ($ forms/form-field {:label "Description"
                            :type :textarea
                            :value (:value description-field)
                            :on-change (:on-change description-field)
                            :error (:error description-field)})
       ($ forms/form-field {:label "Input JSON Schema"
                            :type :textarea
                            :value (:value input-schema-field)
                            :on-change (:on-change input-schema-field)
                            :error (:error input-schema-field)
                            :placeholder example-schema})
       ($ forms/form-field {:label "Output JSON Schema"
                            :type :textarea
                            :value (:value output-schema-field)
                            :on-change (:on-change output-schema-field)
                            :error (:error output-schema-field)
                            :placeholder example-schema})
       ($ forms/form-error {:error error}))))

(defn show-create-dataset-modal!
  "Shows the create dataset modal."
  [module-id]
  (state/dispatch [:form/init :create-dataset
                   {:fields {:name ""
                             :description ""
                             :input-schema ""
                             :output-schema ""}
                    :validators {:name [forms/required]
                                 :input-schema [forms/valid-json]
                                 :output-schema [forms/valid-json]}
                    :submit-event [:dataset/create {:module-id module-id}]}])
  (state/dispatch [:modal/show :create-dataset
                   {:title "Create New Dataset"
                    :form-id :create-dataset
                    :submit-text "Create Dataset"
                    :component ($ CreateDatasetForm {:form-id :create-dataset})}]))

;; =============================================================================
;; MODAL FOR EDITING DATASETS
;; =============================================================================

(def edit-dataset-form-spec
  {:fields {:name ""
            :description ""}
   :validators {:name [forms/required]}
   :submit-event [:dataset/edit]})

(defui EditDatasetForm [{:keys [form-id initial-name initial-description]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]

    ;; Set initial values when component mounts
    (uix/use-effect
     (fn []
       ((:on-change name-field) initial-name)
       ((:on-change description-field) initial-description))
     [initial-name initial-description])

    ($ forms/form
       ($ forms/form-field {:label "Name"
                            :value (:value name-field)
                            :on-change (:on-change name-field)
                            :error (:error name-field)
                            :required? true})
       ($ forms/form-field {:label "Description"
                            :value (:value description-field)
                            :on-change (:on-change description-field)
                            :error (:error description-field)
                            :type :textarea})
       ($ forms/form-error {:error error}))))

;; =============================================================================
;; MODALS FOR EXAMPLES
;; =============================================================================

(defn example-form-spec [config]
  {:fields {:input ""
            :output ""}
   :validators {:input [forms/required forms/valid-json]
                :output [forms/valid-json]}
   :submit-event [:dataset/add-example config]})

(defui ExampleForm [{:keys [form-id]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        input-field (forms/use-form-field form-id :input)
        output-field (forms/use-form-field form-id :output)]

    ($ forms/form
       ($ forms/form-field {:label "Input (JSON)"
                            :value (:value input-field)
                            :on-change (:on-change input-field)
                            :error (:error input-field)
                            :required? true
                            :type :textarea
                            :placeholder "{\"prompt\": \"Hello world\"}"})
       ($ forms/form-field {:label "Reference Output (JSON, Optional)"
                            :value (:value output-field)
                            :on-change (:on-change output-field)
                            :error (:error output-field)
                            :type :textarea
                            :placeholder "{\"response\": \"Hello there!\"}"})
       ($ forms/form-error {:error error}))))

(defn show-add-example-modal! [config]
  (state/dispatch [:form/init :add-example
                   (example-form-spec config)])
  (state/dispatch [:modal/show :add-example
                   {:title "Add Example"
                    :form-id :add-example
                    :submit-text "Add Example"
                    :component ($ ExampleForm {:form-id :add-example})}]))

;; =============================================================================
;; MODALS FOR SNAPSHOTS
;; =============================================================================

(def create-snapshot-form-spec
  {:fields {:snapshot-name ""}
   :validators {:snapshot-name [forms/required]}
   :submit-event [:dataset/create-snapshot]})

(defui CreateSnapshotForm [{:keys [form-id from-snapshot-name]}]
  (let [{:keys [error]} (forms/use-centralized-form form-id)
        snapshot-name-field (forms/use-form-field form-id :snapshot-name)]

    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700 "Source Snapshot")
          ($ :p.mt-1.text-sm.text-gray-500.bg-gray-100.p-2.rounded-md
             (if (str/blank? from-snapshot-name) "Latest (Working Copy)" from-snapshot-name)))

       ($ forms/form-field {:label "New Snapshot Name"
                            :value (:value snapshot-name-field)
                            :on-change (:on-change snapshot-name-field)
                            :error (:error snapshot-name-field)
                            :required? true})
       ($ forms/form-error {:error error}))))

(defn show-create-snapshot-modal!
  "Shows the create snapshot modal and accepts an on-success callback."
  [module-id dataset-id from-snapshot-name on-success] ;; Add on-success parameter
  (state/dispatch [:form/init :create-snapshot
                   (-> create-snapshot-form-spec
                       (assoc :submit-event [:dataset/create-snapshot {:module-id module-id
                                                                       :dataset-id dataset-id
                                                                       :from-snapshot-name from-snapshot-name
                                                                       :on-success on-success}]))]) ;; Pass on-success
  (state/dispatch [:modal/show :create-snapshot
                   {:title "Create New Snapshot"
                    :form-id :create-snapshot
                    :submit-text "Create Snapshot"
                    :component ($ CreateSnapshotForm {:form-id :create-snapshot
                                                      :from-snapshot-name from-snapshot-name})}]))

;; =============================================================================
;; MODALS FOR EVALUATORS
;; =============================================================================

(defui TryEvaluatorModal [{:keys [module-id example]}]
  (let [[selected-evaluator set-selected-evaluator] (uix/use-state nil)
        [output set-output] (uix/use-state nil)
        [error set-error] (uix/use-state nil)
        [loading? set-loading] (uix/use-state false)

        ;; Fetch evaluators for this module
        {:keys [data evaluators-loading? evaluators-error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all {:module-id module-id}]
          :enabled? (boolean module-id)})

        evaluators (or data [])

        handle-run (fn []
                     (when selected-evaluator
                       (set-loading true)
                       (set-error nil)
                       (set-output nil)

                       (sente/request!
                        [:evaluators/run {:module-id module-id
                                          :evaluator-id (:id selected-evaluator)
                                          :input (:input example)
                                          :reference-output (:reference-output example)}]
                        30000
                        (fn [reply]
                          (set-loading false)
                          (if (:success reply)
                            (set-output (:data reply))
                            (set-error (:error reply)))))))]

    ($ :div.p-6.space-y-6
       ;; Evaluator Selection
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Select Evaluator")
          (cond
            evaluators-loading? ($ :div.text-sm.text-gray-500 "Loading evaluators...")
            evaluators-error ($ :div.text-sm.text-red-600 "Error loading evaluators")
            (empty? evaluators) ($ :div.text-sm.text-gray-500 "No evaluators available")
            :else ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.focus:outline-none.focus:ring-2.focus:ring-blue-500
                     {:value (or (:id selected-evaluator) "")
                      :onChange (fn [e]
                                  (let [evaluator-id (.. e -target -value)]
                                    (if (str/blank? evaluator-id)
                                      (set-selected-evaluator nil)
                                      (set-selected-evaluator (first (filter #(= (:id %) evaluator-id) evaluators))))))}
                     ($ :option {:value ""} "Choose an evaluator...")
                     (for [evaluator evaluators]
                       ($ :option {:key (:id evaluator) :value (:id evaluator)}
                          (:name evaluator))))))

       ;; Example Input (read-only)
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Input")
          ($ :div.bg-gray-50.rounded-md.p-4.border
             ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                (js/JSON.stringify (clj->js (:input example)) nil 2))))

       ;; Reference Output (read-only)
       (when (:reference-output example)
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Reference Output")
            ($ :div.bg-gray-50.rounded-md.p-4.border
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                  (js/JSON.stringify (clj->js (:reference-output example)) nil 2)))))

       ;; Run Button
       ($ :div.flex.justify-center
          ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick handle-run
              :disabled (or (not selected-evaluator) loading?)}
             (if loading? "Running..." "Run Evaluator")))

       ;; Error Display
       (when error
         ($ :div.p-4.bg-red-50.border.border-red-200.rounded-md
            ($ :h4.text-sm.font-medium.text-red-800 "Error")
            ($ :p.text-sm.text-red-700.mt-1 error)))

       ;; Output Display
       (when output
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Evaluator Output")
            ($ :div.bg-green-50.rounded-md.p-4.border.border-green-200
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                  (js/JSON.stringify (clj->js output) nil 2))))))))

(defui TrySummaryEvaluatorModal [{:keys [module-id dataset-id selected-example-ids]}]
  (let [[selected-evaluator set-selected-evaluator] (uix/use-state nil)
        [output set-output] (uix/use-state nil)
        [error set-error] (uix/use-state nil)
        [loading? set-loading] (uix/use-state false)

        ;; Fetch evaluators for this module
        {:keys [data evaluators-loading? evaluators-error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all {:module-id module-id}]
          :enabled? (boolean module-id)})

        evaluators (or data [])

        handle-run (fn []
                     (when selected-evaluator
                       (set-loading true)
                       (set-error nil)
                       (set-output nil)

                       (sente/request!
                        [:evaluators/run-summary {:module-id module-id
                                                  :evaluator-id (:id selected-evaluator)
                                                  :dataset-id dataset-id
                                                  :example-ids (vec selected-example-ids)}]
                        60000 ; Longer timeout for summary evaluations
                        (fn [reply]
                          (set-loading false)
                          (if (:success reply)
                            (set-output (:data reply))
                            (set-error (:error reply)))))))]

    ($ :div.p-6.space-y-6
       ;; Evaluator Selection
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Select Evaluator")
          (cond
            evaluators-loading? ($ :div.text-sm.text-gray-500 "Loading evaluators...")
            evaluators-error ($ :div.text-sm.text-red-600 "Error loading evaluators")
            (empty? evaluators) ($ :div.text-sm.text-gray-500 "No evaluators available")
            :else ($ :select.w-full.px-3.py-2.border.border-gray-300.rounded-md.focus:outline-none.focus:ring-2.focus:ring-blue-500
                     {:value (or (:id selected-evaluator) "")
                      :onChange (fn [e]
                                  (let [evaluator-id (.. e -target -value)]
                                    (if (str/blank? evaluator-id)
                                      (set-selected-evaluator nil)
                                      (set-selected-evaluator (first (filter #(= (:id %) evaluator-id) evaluators))))))}
                     ($ :option {:value ""} "Choose an evaluator...")
                     (for [evaluator evaluators]
                       ($ :option {:key (:id evaluator) :value (:id evaluator)}
                          (:name evaluator))))))

       ;; Selected Examples Info
       ($ :div.p-4.bg-blue-50.border.border-blue-200.rounded-md
          ($ :h4.text-sm.font-medium.text-blue-800
             (str "Running on " (count selected-example-ids) " selected example"
                  (when (> (count selected-example-ids) 1) "s"))))

       ;; Run Button
       ($ :div.flex.justify-center
          ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick handle-run
              :disabled (or (not selected-evaluator) loading?)}
             (if loading? "Running..." "Run Summary Evaluation")))

       ;; Error Display
       (when error
         ($ :div.p-4.bg-red-50.border.border-red-200.rounded-md
            ($ :h4.text-sm.font-medium.text-red-800 "Error")
            ($ :p.text-sm.text-red-700.mt-1 error)))

       ;; Output Display
       (when output
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Summary Results")
            ($ :div.bg-green-50.rounded-md.p-4.border.border-green-200
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                  (js/JSON.stringify (clj->js output) nil 2))))))))

;; =============================================================================
;; EXAMPLE ACTIONS AND EDITING
;; =============================================================================

(defui ExampleActionButtons [{:keys [example-id module-id dataset-id snapshot-name on-delete-success]}]
  (let [delete-icon-classes "mr-2 h-4 w-4 text-gray-400 group-hover:text-red-500"]

    ($ :div.flex.items-center.space-x-2
       ;; Delete button
       ($ :button.group.flex.items-center.px-2.py-1.text-xs.text-gray-700.hover:bg-red-100.hover:text-red-800.rounded.cursor-pointer
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (when (js/confirm "Are you sure you want to delete this example?")
                        (sente/request!
                         [:datasets/delete-example
                          {:module-id module-id, :dataset-id dataset-id, :snapshot-name snapshot-name, :example-id example-id}]
                         10000
                         (fn [reply]
                           (if (:success reply)
                             (do
                               (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                               (when on-delete-success (on-delete-success)))
                             (js/alert (str "Error deleting example: " (:error reply))))))))}
          ($ TrashIcon {:className delete-icon-classes})
          "Delete"))))

(defui EditableField [{:keys [label value field-key example-id module-id dataset-id snapshot-name on-save current-example read-only?]}] ;; Add read-only?
  (let [[editing? set-editing!] (uix/use-state false)
        [edit-value set-edit-value!] (uix/use-state "")
        [saving? set-saving!] (uix/use-state false)
        [error set-error!] (uix/use-state nil)

        handle-edit-click (fn []
                            ;; Always use JSON.stringify to ensure proper JSON formatting with quotes
                            (set-edit-value! (if (some? value)
                                               (js/JSON.stringify (clj->js value) nil 2)
                                               ""))
                            (set-editing! true)
                            (set-error! nil))

        handle-cancel-click (fn []
                              (set-editing! false)
                              (set-edit-value! "")
                              (set-error! nil))

        handle-save-click (fn [current-example]
                            (set-saving! true)
                            (set-error! nil)
                            (try
                              (let [parsed-value (if (str/blank? edit-value)
                                                   nil
                                                   (js/JSON.parse edit-value))
                                    ;; Create updated example with the new field value
                                    updated-example (assoc current-example field-key (js->clj parsed-value :keywordize-keys true))]
                                (sente/request!
                                 [:datasets/edit-example
                                  {:module-id module-id
                                   :dataset-id dataset-id
                                   :snapshot-name snapshot-name
                                   :example-id example-id
                                   :input (:input updated-example)
                                   :reference-output (:reference-output updated-example)}]
                                 10000
                                 (fn [reply]
                                   (set-saving! false)
                                   (if (:success reply)
                                     (do
                                       (set-editing! false)
                                       (set-edit-value! "")
                                       ;; Invalidate both the single example query and the main examples list
                                       (state/dispatch [:query/invalidate {:query-key-pattern [:single-example module-id dataset-id snapshot-name (str example-id)]}])
                                       (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                                       (when on-save (on-save)))
                                     (set-error! (str "Error saving: " (:error reply)))))))
                              (catch js/Error e
                                (set-saving! false)
                                (set-error! (str "Invalid JSON: " (.-message e))))))]

    ($ :div
       ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 label)
       (if editing?
         ;; Edit mode (unchanged, but will only be reachable if not read-only)
         ($ :div.space-y-2
            ($ :textarea
               {:className "w-full p-3 border border-gray-300 rounded-md font-mono text-sm"
                :rows 8
                :value edit-value
                :onChange #(set-edit-value! (.. % -target -value))
                :disabled saving?})
            (when error
              ($ :div.text-sm.text-red-600 error))
            ($ :div.flex.items-center.space-x-2
               ($ :button
                  {:className "inline-flex items-center px-3 py-1 text-sm text-white bg-blue-600 border border-transparent rounded-md hover:bg-blue-700 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-blue-500 cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
                   :onClick #(handle-save-click current-example)
                   :disabled saving?}
                  (when saving?
                    ($ :svg.animate-spin.-ml-1.mr-2.h-4.w-4.text-white
                       {:fill "none" :viewBox "0 0 24 24"}
                       ($ :circle.opacity-25 {:cx "12" :cy "12" :r "10" :stroke "currentColor" :strokeWidth "4"})
                       ($ :path.opacity-75 {:fill "currentColor" :d "M4 12a8 8 0 018-8V0C5.373 0 0 5.373 0 12h4zm2 5.291A7.962 7.962 0 014 12H0c0 3.042 1.135 5.824 3 7.938l3-2.647z"})))
                  (if saving? "Saving..." "Save"))
               ($ :button
                  {:className "inline-flex items-center px-3 py-1 text-sm text-gray-700 bg-white border border-gray-300 rounded-md hover:bg-gray-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                   :onClick handle-cancel-click
                   :disabled saving?}
                  "Cancel")))
         ;; View mode
         ($ :div.bg-gray-50.rounded-md.p-4.border.relative.group
            (when-not read-only? ;; Only show Edit button if not read-only
              ($ :button
                 {:className "absolute top-2 right-2 opacity-0 group-hover:opacity-100 transition-opacity inline-flex items-center px-2 py-1 text-xs text-gray-600 bg-white border border-gray-300 rounded hover:bg-gray-100 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-indigo-500 cursor-pointer"
                  :onClick handle-edit-click}
                 ($ PencilIcon {:className "mr-1 h-3 w-3"})
                 "Edit"))
            (if value
              ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono.pr-16
                 (pretty-print-json value))
              ($ :div.text-sm.text-gray-500.italic "No value")))))))

(defui EditableExampleModal [{:keys [example-id module-id dataset-id snapshot-name on-delete-success is-read-only?]}] ;; Add is-read-only?
  (let [;; Fetch the specific example data
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:single-example module-id dataset-id snapshot-name (str example-id)]
          :sente-event [:datasets/get-example {:module-id module-id
                                               :dataset-id dataset-id
                                               :snapshot-name snapshot-name
                                               :example-id example-id}]
          :enabled? (boolean (and module-id dataset-id example-id))})

        example (:example data)]

    (cond
      loading? ($ :div.p-6 "Loading example details...")
      error ($ :div.p-6.text-red-500 "Error loading example details")
      (not example) ($ :div.p-6.text-gray-500 "Example not found")
      :else
      ($ :div.p-6.space-y-6
         ;; --- Header with Delete Button ---
         ($ :div.flex.items-center.justify-between
            ($ :div)
            (when-not is-read-only? ;; Only show delete button if not read-only
              ($ :button
                 {:className "inline-flex items-center px-3 py-1 text-sm text-red-700 bg-white border border-red-300 rounded-md hover:bg-red-50 focus:outline-none focus:ring-2 focus:ring-offset-2 focus:ring-red-500 cursor-pointer"
                  :onClick (fn []
                             (when (js/confirm "Are you sure you want to delete this example?")
                               (state/dispatch [:modal/hide]) ; Close modal before deleting
                               (sente/request!
                                [:datasets/delete-example
                                 {:module-id module-id, :dataset-id dataset-id, :snapshot-name snapshot-name, :example-id example-id}]
                                10000
                                (fn [reply]
                                  (if (:success reply)
                                    (do
                                      (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                                      (when on-delete-success (on-delete-success)))
                                    (js/alert (str "Error deleting example: " (:error reply))))))))}
                 ($ TrashIcon {:className "mr-2 h-4 w-4"})
                 "Delete")))

         ;; --- Editable Fields ---
         ($ :div.space-y-6
            ;; Input field
            ($ EditableField {:label "Input"
                              :value (:input example)
                              :field-key :input
                              :example-id example-id
                              :module-id module-id
                              :dataset-id dataset-id
                              :snapshot-name snapshot-name
                              :on-save refetch
                              :current-example example
                              :read-only? is-read-only?}) ;; Pass read-only state

            ;; Reference Output field
            ($ EditableField {:label "Reference Output"
                              :value (:reference-output example)
                              :field-key :reference-output
                              :example-id example-id
                              :module-id module-id
                              :dataset-id dataset-id
                              :snapshot-name snapshot-name
                              :on-save refetch
                              :current-example example
                              :read-only? is-read-only?}) ;; Pass read-only state

            ;; Tags section
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Tags")
               ($ :div.bg-gray-50.rounded-md.p-4.border
                  ($ TagInput {:tags (:tags example) :module-id module-id :dataset-id dataset-id :snapshot-name snapshot-name :example-id example-id :read-only? is-read-only?}))) ;; Pass read-only state

            ;; Example ID (read-only)
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Example ID")
               ($ :div.bg-gray-50.rounded-md.p-2.border
                  ($ :code.text-xs.text-gray-600 (str example-id)))))))))

(defui TagInput [{:keys [tags module-id dataset-id snapshot-name example-id on-tags-change read-only?]}] ;; Add read-only?
  (let [[input-value set-input-value] (uix/use-state "")
        [is-adding set-is-adding] (uix/use-state false)

        handle-add-tag (fn [tag-name]
                         (when-not (or (str/blank? tag-name) (contains? (set (map name tags)) tag-name))
                           (set-is-adding true)
                           (sente/request!
                            [:datasets/add-tag {:module-id module-id
                                                :dataset-id dataset-id
                                                :snapshot-name snapshot-name
                                                :example-id example-id
                                                :tag tag-name}]
                            10000
                            (fn [reply]
                              (set-is-adding false)
                              (if (:success reply)
                                (do
                                  (set-input-value "")
                                  ;; Invalidate both the single example query and the main examples list
                                  (state/dispatch [:query/invalidate {:query-key-pattern [:single-example module-id dataset-id snapshot-name (str example-id)]}])
                                  (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                                  (when on-tags-change (on-tags-change)))
                                (js/alert (str "Error adding tag: " (:error reply))))))))

        handle-remove-tag (fn [tag-name]
                            (sente/request!
                             [:datasets/remove-tag {:module-id module-id
                                                    :dataset-id dataset-id
                                                    :snapshot-name snapshot-name
                                                    :example-id example-id
                                                    :tag tag-name}]
                             10000
                             (fn [reply]
                               (if (:success reply)
                                 (do
                                   ;; Invalidate both the single example query and the main examples list
                                   (state/dispatch [:query/invalidate {:query-key-pattern [:single-example module-id dataset-id snapshot-name (str example-id)]}])
                                   (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                                   (when on-tags-change (on-tags-change)))
                                 (js/alert (str "Error removing tag: " (:error reply)))))))

        handle-key-press (fn [e]
                           (when (= (.-key e) "Enter")
                             (.preventDefault e)
                             (let [trimmed-value (str/trim input-value)]
                               (when-not (str/blank? trimmed-value)
                                 (handle-add-tag trimmed-value)))))]

    ($ :div
       ;; Existing tags as pills
       (if (and tags (seq tags))
         ($ :div.flex.flex-wrap.gap-2.mb-3
            (for [tag (sort (map name tags))]
              ($ :span.inline-flex.items-center.px-2.5.py-0.5.rounded-full.text-xs.font-medium.bg-blue-100.text-blue-800
                 {:key tag}
                 tag
                 (when-not read-only? ;; Only show remove button if not read-only
                   ($ :button.ml-1.inline-flex.items-center.justify-center.w-4.h-4.rounded-full.text-blue-400.hover:bg-blue-200.hover:text-blue-600.focus:outline-none
                      {:onClick #(handle-remove-tag tag)
                       :title (str "Remove " tag)}
                      ($ XMarkIcon {:className "w-3 h-3"}))))))
         ($ :div.text-sm.text-gray-500.italic.mb-3 "No tags"))

       ;; Input field for adding new tags
       (when-not read-only? ;; Only show input field if not read-only
         ($ :div.flex.items-center.space-x-2
            ($ :input.flex-1.px-3.py-2.text-sm.border.border-gray-300.rounded-md.focus:outline-none.focus:ring-2.focus:ring-blue-500.focus:border-blue-500
               {:type "text"
                :placeholder "Add a tag and press Enter..."
                :value input-value
                :onChange #(set-input-value (.. % -target -value))
                :onKeyPress handle-key-press
                :disabled is-adding})
            (when is-adding
              ($ :div.text-sm.text-gray-500 "Adding...")))))))

(defui DropdownRow [{:keys [label selected? on-select delete-button action? icon extra-content]}]
  (let [row-classes (str "flex items-center justify-between w-full px-4 py-2 text-sm cursor-pointer hover:bg-gray-100 focus:bg-gray-100 "
                         (cond
                           selected? "bg-blue-50 text-blue-700"
                           action? "text-blue-600 hover:bg-blue-50"
                           :else "text-gray-700"))]
    ($ :div
       ;; Main clickable area
       ($ :div
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (when on-select (on-select)))
           :className row-classes}
          ($ :div.flex.items-center.flex-1
             (when icon ($ :div.mr-3 icon))
             ($ :span.truncate label)
             (when selected? ($ :span.ml-2.text-xs.text-blue-600 "✓")))
          ;; Delete button area (separate from main click area to avoid nesting)
          (when (and delete-button (not action?))
            ($ :div.ml-2
               {:onClick #(.stopPropagation %)} ;; Prevent triggering the row click
               delete-button)))
       ;; Extra content below the main row
       (when extra-content extra-content))))

;; =============================================================================
;; EVALUATOR UTILITIES
;; =============================================================================

(defn get-evaluator-type-display [evaluator-type]
  (case evaluator-type
    :llm-as-judge "LLM as Judge"
    :simple-string-match "String Match"
    :json-field-match "JSON Field Match"
    :custom-function "Custom Function"
    (str evaluator-type)))

(defn get-evaluator-type-badge-style [evaluator-type]
  (case evaluator-type
    :llm-as-judge "bg-purple-100 text-purple-800"
    :simple-string-match "bg-green-100 text-green-800"
    :json-field-match "bg-blue-100 text-blue-800"
    :custom-function "bg-orange-100 text-orange-800"
    "bg-gray-100 text-gray-800"))

(defui EvaluatorDropdown [{:keys [evaluators on-select selected-evaluator]}]
  (let [[open? set-open] (uix/use-state false)]

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [e]
                            (when open?
                              (set-open false)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [open?])

    ($ :div.relative.inline-block.text-left
       ;; Main dropdown button
       ($ :button.inline-flex.items-center.justify-between.w-64.px-3.py-2.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-blue-500.cursor-pointer
          {:onClick (fn [e]
                      (.stopPropagation e)
                      (set-open (not open?)))}
          ($ :span.truncate
             (if selected-evaluator
               (:name selected-evaluator)
               "Select evaluator..."))
          ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))

       ;; Dropdown menu
       (when open?
         ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
            {:onClick #(.stopPropagation %)}
            ($ :div.py-1
               ;; Default option
               ($ DropdownRow {:label "Select evaluator..."
                               :selected? (nil? selected-evaluator)
                               :on-select #(do
                                             (set-open false)
                                             (on-select nil))
                               :delete-button nil})

               ;; Evaluator options
               (for [evaluator evaluators]
                 ($ DropdownRow {:key (:id evaluator)
                                 :label (:name evaluator)
                                 :selected? (= (:id selected-evaluator) (:id evaluator))
                                 :on-select #(do
                                               (set-open false)
                                               (on-select evaluator))
                                 :delete-button nil
                                 :extra-content ($ :div.px-4.pb-2.text-xs.text-gray-500
                                                   ($ :span.inline-flex.items-center.px-2.py-0.5.rounded-full.text-xs.font-medium
                                                      {:className (get-evaluator-type-badge-style (:type evaluator))}
                                                      (get-evaluator-type-display (:type evaluator))))}))))))))

;; =============================================================================
;; SNAPSHOT MANAGER
;; =============================================================================

(defui SnapshotManager [{:keys [module-id dataset-id selected-snapshot set-selected-snapshot]}]
  (let [[dropdown-open? set-dropdown-open] (uix/use-state false)

        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:snapshot-names module-id dataset-id]
          :sente-event [:datasets/get-snapshot-names {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        snapshot-names (or (sort data) [])

        handle-create (fn []
                        (set-dropdown-open false)
                        ;; Pass the setter function as a callback
                        (show-create-snapshot-modal! module-id
                                                     dataset-id
                                                     selected-snapshot
                                                     set-selected-snapshot))

        handle-delete (fn [snapshot-name]
                        (set-dropdown-open false)
                        (when (js/confirm (str "Are you sure you want to delete snapshot '" snapshot-name "'?"))
                          (sente/request!
                           [:datasets/delete-snapshot {:module-id module-id :dataset-id dataset-id :snapshot-name snapshot-name}]
                           10000
                           (fn [reply]
                             (if (:success reply)
                               (do
                                 (when (= selected-snapshot snapshot-name)
                                   (set-selected-snapshot "")) ;; Reset view to latest if deleting current
                                 ;; Invalidate snapshot names query to trigger refetch
                                 (state/dispatch [:query/invalidate {:query-key-pattern [:snapshot-names module-id dataset-id]}]))
                               (js/alert (str "Error deleting snapshot: " (:error reply))))))))

        handle-select (fn [snapshot-name]
                        (set-dropdown-open false)
                        (set-selected-snapshot snapshot-name))

        current-display-name (if (str/blank? selected-snapshot)
                               "Latest (Working Copy)"
                               selected-snapshot)]

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [e]
                            (when dropdown-open?
                              (set-dropdown-open false)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [dropdown-open?])

    ($ :div.flex.items-center.space-x-2
       ($ :div.relative.inline-block.text-left
          ;; Main dropdown button
          ($ :button.inline-flex.items-center.justify-between.w-64.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-blue-500.cursor-pointer
             {:onClick (fn [e]
                         (.stopPropagation e)
                         (let [is-opening (not dropdown-open?)]
                           (set-dropdown-open is-opening)
                           (when is-opening (refetch))))
              :disabled loading?}
             ($ :span.truncate current-display-name)
             ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))

          ;; Dropdown menu
          (when dropdown-open?
            ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
               {:onClick #(.stopPropagation %)}
               ($ :div.py-1
                  ;; Latest option
                  ($ DropdownRow {:label "Latest (Working Copy)"
                                  :selected? (str/blank? selected-snapshot)
                                  :on-select #(handle-select "")
                                  :delete-button nil})

                  ;; Named snapshots
                  (for [name snapshot-names]
                    ($ DropdownRow {:key name
                                    :label name
                                    :selected? (= selected-snapshot name)
                                    :on-select #(handle-select name)
                                    :delete-button ($ :button.text-red-600.hover:text-red-800.p-1.rounded.hover:bg-red-100
                                                      {:onClick (fn [e]
                                                                  (.stopPropagation e)
                                                                  (handle-delete name))
                                                       :title (str "Delete " name)}
                                                      ($ TrashIcon {:className "h-3 w-3"}))}))

                  ;; Divider
                  ($ :div.border-t.border-gray-100.my-1)

                  ;; New snapshot action
                  ($ DropdownRow {:label "New snapshot"
                                  :action? true
                                  :on-select handle-create
                                  :icon ($ PlusIcon {:className "h-4 w-4"})
                                  :delete-button nil}))))))))

(defui ExamplesList [{:keys [examples module-id dataset-id snapshot-name on-delete-success is-read-only?]}] ;; Add is-read-only?
  (let [[open-dropdown set-open-dropdown] (uix/use-state nil)
        selected-ids (or (state/use-sub [:ui :datasets :selected-examples dataset-id]) #{})
        all-on-page-ids (set (map :id examples))
        all-selected? (and (seq all-on-page-ids)
                           (clojure.set/subset? all-on-page-ids selected-ids))]

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [e]
                            (when open-dropdown
                              (set-open-dropdown nil)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [open-dropdown])

    ($ :div.mt-4.overflow-visible
       ($ :table.min-w-full.divide-y.divide-gray-200
          ($ :thead.bg-gray-50
             ($ :tr
                ;; Checkbox column header - entire cell is clickable
                ($ :th.px-4.py-3.text-left.cursor-pointer.hover:bg-blue-100
                   {:onClick #(state/dispatch [:datasets/toggle-all-selection
                                               {:dataset-id dataset-id
                                                :example-ids-on-page all-on-page-ids
                                                :select-all? (not all-selected?)}])}
                   ($ :input {:type "checkbox"
                              :checked all-selected?
                              :readOnly true ; Make it read-only since cell handles the click
                              :className "pointer-events-none"}))
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Input")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Reference Output")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Tags")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Actions")))
          ($ :tbody.bg-white.divide-y.divide-gray-200
             (for [example examples]
               (let [example-id (:id example)
                     is-open? (= open-dropdown example-id)
                     is-selected? (contains? selected-ids example-id)]
                 ($ :tr {:key example-id
                         :className (str (when is-selected? "bg-blue-50 ")
                                         "hover:bg-gray-50 cursor-pointer")
                         :onClick #(state/dispatch [:modal/show :example-viewer
                                                    {:title "Example Details"
                                                     :component ($ EditableExampleModal
                                                                   {:example-id example-id
                                                                    :module-id module-id
                                                                    :dataset-id dataset-id
                                                                    :snapshot-name snapshot-name
                                                                    :on-delete-success on-delete-success
                                                                    :is-read-only? is-read-only?})}])} ;; Pass read-only state
                    ;; Checkbox column - entire cell is clickable
                    ($ :td.px-4.py-4.cursor-pointer.hover:bg-blue-100
                       {:onClick (fn [e]
                                   (.stopPropagation e) ; Prevent row click
                                   (state/dispatch [:datasets/toggle-selection
                                                    {:dataset-id dataset-id
                                                     :example-id example-id}]))}
                       ($ :input {:type "checkbox"
                                  :checked is-selected?
                                  :readOnly true ; Make it read-only since cell handles the click
                                  :className "pointer-events-none"}))
                    ;; Input column
                    ($ :td.px-6.py-4.text-sm.font-mono.max-w-xs
                       (let [input-str (if (string? (:input example))
                                         (:input example)
                                         (js/JSON.stringify (clj->js (:input example)) nil 2))]
                         ($ :div.truncate.cursor-help {:title input-str} input-str)))
                    ;; Reference Output column
                    ($ :td.px-6.py-4.text-sm.font-mono.max-w-xs
                       (let [output-str (if (string? (:reference-output example))
                                          (:reference-output example)
                                          (js/JSON.stringify (clj->js (:reference-output example)) nil 2))]
                         (if output-str
                           ($ :div.truncate.cursor-help {:title output-str} output-str)
                           ($ :span "—"))))
                    ;; Tags column
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-500
                       (let [tags (:tags example)]
                         (if (and tags (seq tags))
                           (->> tags
                                (map name) ; Convert keywords to strings
                                (sort) ; Sort alphabetically
                                (clojure.string/join ", ")) ; Join with commas
                           ($ :span.italic "no tags"))))
                    ;; Actions column
                    ($ :td.px-6.py-4.whitespace-nowrap.text-right.text-sm.font-medium
                       ;; Conditionally render actions
                       (if is-read-only?
                         ($ :div.flex.justify-center.items-center
                            ($ LockClosedIcon {:className "h-5 w-5 text-gray-400" :title "This snapshot is read-only"}))
                         ($ :div.relative.inline-block.text-left
                            ;; Three dots button - prevent row click when clicking
                            ($ :button.inline-flex.items-center.justify-center.w-8.h-8.rounded-full.text-gray-400.hover:text-gray-600.hover:bg-gray-100.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500.cursor-pointer
                               {:onClick (fn [e]
                                           (.stopPropagation e) ; Prevent row click
                                           (set-open-dropdown (if is-open? nil example-id)))}
                               ($ EllipsisVerticalIcon {:className "h-5 w-5"}))

                            ;; Dropdown menu
                            (when is-open?
                              ($ :div.origin-top-right.absolute.right-0.mt-2.w-48.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
                                 {:onClick #(.stopPropagation %)}
                                 ($ :div.py-1
                                    ;; Try with evaluator button
                                    ($ :button
                                       {:className "group flex items-center w-full px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 hover:text-gray-900 cursor-pointer"
                                        :onClick (fn [e]
                                                   (.stopPropagation e)
                                                   (set-open-dropdown nil) ; Close dropdown
                                                   ;; Show the Try Evaluator modal
                                                   (state/dispatch [:modal/show :try-evaluator
                                                                    {:title "Try with Evaluator"
                                                                     :component ($ TryEvaluatorModal {:module-id module-id :example example})}]))}
                                       ($ PlayIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                       "Try with evaluator")
                                    ;; Delete button
                                    ($ :button
                                       {:className "group flex items-center w-full px-4 py-2 text-sm text-gray-700 hover:bg-red-100 hover:text-red-800 cursor-pointer"
                                        :onClick (fn [e]
                                                   (.stopPropagation e)
                                                   (set-open-dropdown nil) ; Close dropdown
                                                   (when (js/confirm "Are you sure you want to delete this example?")
                                                     (sente/request!
                                                      [:datasets/delete-example
                                                       {:module-id module-id
                                                        :dataset-id dataset-id
                                                        :snapshot-name snapshot-name
                                                        :example-id example-id}]
                                                      10000
                                                      (fn [reply]
                                                        (if (:success reply)
                                                          (do
                                                            (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id snapshot-name]}])
                                                            (when on-delete-success (on-delete-success)))
                                                          (js/alert (str "Error deleting example: " (:error reply))))))))}
                                       ($ TrashIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-red-500"})
                                       "Delete")))))))))))))))

(defn get-dataset-path [module-id dataset-id]
  (rfe/href :module/dataset-detail
            {:module-id (common/url-encode module-id)
             :dataset-id dataset-id}))

(defn show-edit-dataset-modal! [module-id dataset-id initial-name initial-description]
  (state/dispatch [:form/init :edit-dataset
                   (-> edit-dataset-form-spec
                       (assoc :submit-event [:dataset/edit {:module-id module-id
                                                            :dataset-id dataset-id
                                                            :initial-name initial-name
                                                            :initial-description initial-description}]))])
  (state/dispatch [:modal/show :edit-dataset
                   {:title "Edit Dataset"
                    :form-id :edit-dataset
                    :submit-text "Save Changes"
                    :component ($ EditDatasetForm {:form-id :edit-dataset
                                                   :initial-name initial-name
                                                   :initial-description initial-description})}]))

;; =============================================================================
;; MAIN DATASETS INDEX PAGE
;; =============================================================================

(defui datasets-index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))

        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:datasets decoded-module-id]
          :sente-event [:datasets/get-all {:module-id decoded-module-id
                                           :pagination nil}]
          :enabled? (boolean decoded-module-id)})

        datasets (:datasets data)]

    ($ :div.h-full.flex.flex-col
       ($ :div.bg-white.shadow.sm:rounded-lg.mb-6
          ($ :div.px-4.py-5.sm:p-6
             ($ :div.flex.items-center.justify-between
                ($ :div
                   ($ :h1.text-2xl.font-bold.text-gray-900 "Datasets")
                   ($ :p.mt-1.text-sm.text-gray-600 "Manage your training and evaluation datasets"))
                ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.shadow-sm.text-white.bg-indigo-600.hover:bg-indigo-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500.cursor-pointer
                   {:onClick #(show-create-dataset-modal! decoded-module-id)}
                   ($ PlusIcon {:className "h-4 w-4 mr-2"})
                   "Create Dataset"))))

       ($ :div.flex-1
          (cond
            loading? ($ :div.flex.items-center.justify-center.h-full ($ :div "Loading datasets..."))
            error ($ :div.flex.items-center.justify-center.h-full ($ :div.text-red-500 "Error loading datasets"))
            (empty? datasets)
            ($ :div.flex.items-center.justify-center.h-full
               ($ :div.text-center.text-gray-500
                  ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400"})
                  ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No datasets")
                  ($ :p.mt-1.text-sm.text-gray-500 "Get started by creating a new dataset.")
                  ($ :div.mt-6
                     ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.shadow-sm.text-sm.font-medium.rounded-md.text-white.bg-indigo-600.hover:bg-indigo-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500.cursor-pointer
                        {:onClick #(show-create-dataset-modal! decoded-module-id)}
                        ($ PlusIcon {:className "h-4 w-4 mr-2"})
                        "Create Dataset"))))
            :else
            ($ :div {:className (:container common/table-classes)}
               ($ :table {:className (:table common/table-classes)}
                  ($ :thead {:className (:thead common/table-classes)}
                     ($ :tr
                        ($ :th {:className (:th common/table-classes)} "Name")
                        ($ :th {:className (:th common/table-classes)} "Description")
                        ($ :th {:className (:th common/table-classes)} "Actions")))
                  ($ :tbody
                     (into []
                           (for [dataset datasets
                                 :let [name (:name dataset)
                                       desc (:description dataset)
                                       dsid (:dataset-id dataset)
                                       href (get-dataset-path decoded-module-id dsid)]]
                             ($ :tr {:key dsid
                                     :className "hover:bg-gray-50 cursor-pointer"
                                     :onClick (fn [_]
                                                (rfe/push-state :module/dataset-detail
                                                                {:module-id decoded-module-id
                                                                 :dataset-id dsid}))}
                                ($ :td {:className (:td common/table-classes)}
                                   ($ :a.text-indigo-600.hover:text-indigo-800 {:href href} name))
                                ($ :td {:className (:td common/table-classes)}
                                   (if (seq (str desc))
                                     ($ :span.text-sm.text-gray-600.desc.truncate {:title desc} desc)
                                     ($ :span.text-sm.text-gray-400.italic "—")))
                                ($ :td {:className (:td-right common/table-classes)}
                                   ($ :div.flex.items-center.space-x-2
                                      ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-gray-700.cursor-pointer
                                         {:onClick (fn [e]
                                                     (.preventDefault e)
                                                     (.stopPropagation e)
                                                     (show-edit-dataset-modal! decoded-module-id dsid name desc))}
                                         ($ PencilIcon {:className "h-4 w-4 mr-1"})
                                         "Edit")
                                      ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                         {:onClick (fn [e]
                                                     (.preventDefault e)
                                                     (.stopPropagation e)
                                                     (when (js/confirm (str "Are you sure you want to delete dataset '" name "'? This action cannot be undone."))
                                                       (sente/request!
                                                        [:datasets/delete {:module-id decoded-module-id :dataset-id dsid}]
                                                        10000
                                                        (fn [reply]
                                                          (if (:success reply)
                                                            (state/dispatch [:query/invalidate {:query-key-pattern [:datasets decoded-module-id]}])
                                                            (js/alert (str "Error deleting dataset: " (:error reply))))))))}
                                         ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                         "Delete"))))))))))))))

;; =============================================================================
;; PRETTY PRINT UTILITY
;; =============================================================================

(defn pretty-print-json [json-data]
  (try
    (js/JSON.stringify (clj->js json-data) nil 2)
    (catch js/Error _
      (str json-data))))

;; =============================================================================
;; DATASET DETAIL PAGE
;; =============================================================================

(defui dataset-detail []
  (let [;; Get IDs from route
        {:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))

        ;; Get selected examples for this dataset
        selected-example-ids (or (state/use-sub [:ui :datasets :selected-examples dataset-id]) #{})

        ;; State for selected tab
        [active-tab set-active-tab] (uix/use-state "examples")

        ;; State for selected snapshot and info panel
        [selected-snapshot-name set-selected-snapshot-name] (uix/use-state "")
        [show-info? set-show-info] (uix/use-state false)
        is-read-only? (not (str/blank? selected-snapshot-name)) ;; DERIVED STATE FOR IMMUTABILITY

        ;; State for search string
        [search-string set-search-string] (uix/use-state "")

        ;; --- START OF FIX ---

        ;; 1. Fetch dataset properties, RENAMING keys to avoid collision
        {:keys [data loading? error refetch] :as props-query}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})

        ;; Rename destructured keys for clarity
        {dataset-props :data, props-loading? :loading?, props-error :error, props-refetch :refetch} props-query

        ;; 2. Fetch examples, also RENAMING keys
        {:keys [data loading? error refetch] :as examples-query}
        (queries/use-sente-query
         {:query-key [:dataset-examples module-id dataset-id selected-snapshot-name search-string]
          :sente-event [:datasets/search-examples {:module-id module-id
                                                   :dataset-id dataset-id
                                                   :snapshot-name selected-snapshot-name
                                                   :filters (when-not (str/blank? search-string)
                                                              {:search-string search-string})
                                                   :limit 20
                                                   :pagination nil}]
          :enabled? (boolean (and module-id dataset-id))})

        ;; Rename destructured keys for clarity
        {examples-response :data, examples-loading? :loading?, examples-error :error, examples-refetch :refetch} examples-query

        ;; 3. Use the correctly named variables
        dataset dataset-props ;; Correctly assign dataset properties

        examples (get examples-response :examples)]

    ;; --- END OF FIX ---

    ;; Clear selections when dataset changes
    (uix/use-effect
     (fn []
       #(state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}]))
     [dataset-id])

    ($ :div.h-full.flex.flex-col
       (cond
         props-loading? ($ :div.p-6 "Loading dataset details...") ;; Use props-loading?
         props-error ($ :div.p-6 "Error: " props-error) ;; Use props-error
         dataset ;; This will now correctly be the props data object
         ($ :div.h-full.flex.flex-col
            ;; Header Bar
            ($ :div.bg-white.px-6.py-4
               ($ :div.flex.items-center.justify-between
                  ;; Left side - Title and info
                  ($ :div.flex.items-center.space-x-4
                     ($ :h1.text-2xl.font-bold.text-gray-900 (:name dataset))
                     ;; Details button with conditional chevron
                     ($ :button.inline-flex.items-center.px-3.py-1.text-sm.text-gray-600.hover:text-gray-800.rounded-md.hover:bg-gray-100.cursor-pointer
                        {:onClick #(set-show-info (not show-info?))
                         :title (if show-info? "Hide Dataset Information" "Show Dataset Information")}
                        ($ :span.mr-1 "Details")
                        (if show-info?
                          ($ ChevronUpIcon {:className "h-4 w-4"})
                          ($ ChevronDownIcon {:className "h-4 w-4"}))))

                  ;; Right side - removed snapshot manager and add example button
                  ($ :div.flex.items-center.space-x-4)))

            ;; Info Panel (collapsible)
            (when show-info?
              ($ :div.bg-blue-50.border-b.border-blue-200.px-6.py-4
                 ($ :div.space-y-4
                    ;; Description
                    (when (:description dataset)
                      ($ :div
                         ($ :h3.text-sm.font-medium.text-blue-900 "Description")
                         ($ :p.text-sm.text-blue-700.mt-1 (:description dataset))))

                    ;; Schemas - Always show this section
                    (let [input-schema (:input-json-schema dataset)
                          output-schema (:output-json-schema dataset)]
                      ($ :div
                         ($ :h3.text-sm.font-medium.text-blue-900 "Schemas")
                         ($ :div.grid.grid-cols-2.gap-4.mt-2
                            ;; Input Schema - always show
                            ($ :div
                               ($ :h4.text-xs.font-medium.text-blue-800.mb-1 "Input Schema")
                               (if input-schema
                                 ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-32.text-blue-800
                                    (pretty-print-json input-schema))
                                 ($ :div.text-xs.bg-gray-100.p-2.rounded.text-gray-500.italic
                                    "Schema: nil")))
                            ;; Output Schema - always show
                            ($ :div
                               ($ :h4.text-xs.font-medium.text-blue-800.mb-1 "Output Schema")
                               (if output-schema
                                 ($ :pre.text-xs.bg-blue-100.p-2.rounded.overflow-auto.max-h-32.text-blue-800
                                    (pretty-print-json output-schema))
                                 ($ :div.text-xs.bg-gray-100.p-2.rounded.text-gray-500.italic
                                    "Schema: nil")))))))))

            ;; Tab Navigation
            ($ :div.bg-white.border-b.border-gray-200
               ($ :nav.flex.space-x-8.px-6 {:aria-label "Tabs"}
                  ;; Experiments Tab
                  ($ :button.py-4.px-1.border-b-2.font-medium.text-sm.cursor-pointer
                     {:className (if (= active-tab "experiments")
                                   "cursor-pointer border-indigo-500 text-indigo-600"
                                   "cursor-pointer border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")
                      :onClick #(set-active-tab "experiments")}
                     "Experiments")

                  ;; Comparative Experiments Tab
                  ($ :button.py-4.px-1.border-b-2.font-medium.text-sm.cursor-pointer
                     {:className (if (= active-tab "comparative-experiments")
                                   "cursor-pointer border-indigo-500 text-indigo-600"
                                   "cursor-pointer border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")
                      :onClick #(set-active-tab "comparative-experiments")}
                     "Comparative Experiments")

                  ;; Examples Tab
                  ($ :button.py-4.px-1.border-b-2.font-medium.text-sm.cursor-pointer
                     {:className (if (= active-tab "examples")
                                   "cursor-pointer border-indigo-500 text-indigo-600"
                                   "cursor-pointer border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300")
                      :onClick #(set-active-tab "examples")}
                     "Examples")))

            ;; Tab Content Section (main content)
            ($ :div.flex-1.min-h-0
               (case active-tab
                 "experiments"
                 ($ :div.flex.items-center.justify-center.h-full
                    ($ :div.text-center.text-gray-500
                       ($ :p "Experiments functionality coming soon.")))

                 "comparative-experiments"
                 ($ :div.flex.items-center.justify-center.h-full
                    ($ :div.text-center.text-gray-500
                       ($ :p "Comparative experiments functionality coming soon.")))

                 "examples"
                 ($ :div.h-full.flex.flex-col
                    ;; Examples Tab Header with Controls
                    ($ :div.bg-gray-50.border-b.border-gray-200.px-6.py-4
                       ($ :div.flex.items-center.justify-between
                          ;; Left side - Snapshot Manager and Search
                          ($ :div.flex.items-center.space-x-4
                             ($ :span.text-sm.font-medium.text-gray-700 "Snapshot:")
                             ($ SnapshotManager {:module-id module-id
                                                 :dataset-id dataset-id
                                                 :selected-snapshot selected-snapshot-name
                                                 :set-selected-snapshot set-selected-snapshot-name})

                             ;; Search input field
                             ($ :input.ml-4.px-3.py-1.border.border-gray-300.rounded-md.text-sm
                                {:type "text"
                                 :placeholder "Search examples..."
                                 :value search-string
                                 :onChange #(set-search-string (.. % -target -value))}))

                          ;; Right side - Add Example button
                          ($ :div.flex.items-center.space-x-4
                             ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer.disabled:bg-gray-400.disabled:cursor-not-allowed
                                {:onClick #(show-add-example-modal!
                                            {:module-id module-id
                                             :dataset-id dataset-id
                                             :snapshot-name selected-snapshot-name})
                                 :disabled is-read-only?
                                 :title (when is-read-only? "Cannot add examples to a read-only snapshot.")}
                                ($ PlusIcon {:className "h-4 w-4 mr-2"})
                                "Add Example"))))
                    ;; Add read-only banner
                    (when is-read-only?
                      ($ :div.bg-yellow-100.border-b.border-yellow-200.px-6.py-2.text-sm.text-yellow-800.flex.items-center.gap-2
                         ($ LockClosedIcon {:className "h-4 w-4"})
                         ($ :span ($ :b "Read-only:") " You are viewing an immutable snapshot. Editing is disabled.")))

                    ;; Action bar - always visible
                    ($ :div.bg-gray-50.border-b.border-gray-200.px-6.py-3
                       ($ :div.flex.items-center.justify-between
                          ($ :div.flex.items-center.space-x-4
                             (if (seq selected-example-ids)
                               ($ :span.text-sm.font-medium.text-gray-900
                                  (str (count selected-example-ids) " example"
                                       (when (> (count selected-example-ids) 1) "s")
                                       " selected"))
                               ($ :span.text-sm.text-gray-500 "No examples selected")))
                          ($ :div.flex.items-center.space-x-2
                             ($ :button.px-3.py-1.text-sm.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                                {:disabled (empty? selected-example-ids)
                                 :onClick #(when (seq selected-example-ids)
                                             (state/dispatch [:modal/show :try-summary-evaluator
                                                              {:title "Run Summary Evaluation"
                                                               :component ($ TrySummaryEvaluatorModal
                                                                             {:module-id module-id
                                                                              :dataset-id dataset-id
                                                                              :selected-example-ids selected-example-ids})}]))}
                                "Try summary evaluator")
                             ($ :button.px-3.py-1.text-sm.text-gray-600.border.border-gray-300.rounded-md.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                                {:disabled (empty? selected-example-ids)
                                 :onClick #(when (seq selected-example-ids)
                                             (state/dispatch [:datasets/clear-selection {:dataset-id dataset-id}]))}
                                "Clear Selection"))))

                    ;; Examples Content
                    ($ :div.flex-1.overflow-hidden
                       ($ :div.h-full.flex.flex-col
                          ($ :div.flex-1.overflow-hidden
                             (cond
                               examples-loading? ($ :div.flex.items-center.justify-center.h-full
                                                    ($ :div "Loading examples..."))
                               examples-error ($ :div.flex.items-center.justify-center.h-full
                                                 ($ :div.text-red-500 "Error loading examples."))
                               (empty? examples) ($ :div.flex.items-center.justify-center.h-full
                                                    ($ :div.text-center.text-gray-500
                                                       ($ :p "No examples yet.")
                                                       ($ :p.text-sm.mt-1 "Click 'Add Example' to get started.")))
                               :else ($ :div.h-full.overflow-auto
                                        ($ ExamplesList {:examples examples
                                                         :module-id module-id
                                                         :dataset-id dataset-id
                                                         :snapshot-name selected-snapshot-name
                                                         :is-read-only? is-read-only?})))))))

                 ;; Default case
                 ($ :div.flex.items-center.justify-center.h-full
                    ($ :div.text-center.text-gray-500
                       ($ :p "Unknown tab selected."))))))
         :else ($ :div.p-6 "No data available.")))))

(def index datasets-index)
(def detail dataset-detail)
