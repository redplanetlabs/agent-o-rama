(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]))

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
                            :placeholder "Optional description for this dataset"
                            :rows 3})
       ($ :div.grid.grid-cols-2.gap-4
          ($ forms/form-field {:label "Input JSON Schema"
                               :type :textarea
                               :value (:value input-schema-field)
                               :on-change (:on-change input-schema-field)
                               :error (:error input-schema-field)
                               :placeholder example-schema
                               :rows 15
                               :class-name "font-mono"})
          ($ forms/form-field {:label "Output JSON Schema"
                               :type :textarea
                               :value (:value output-schema-field)
                               :on-change (:on-change output-schema-field)
                               :error (:error output-schema-field)
                               :placeholder example-schema
                               :rows 15
                               :class-name "font-mono"}))

       ;; Server-side error for centralized forms
       ($ forms/form-error {:error error})

       ;; JSON Schema Help Box
       ($ :div.bg-blue-50.border.border-blue-200.rounded-md.p-4
          ($ :div.flex
             ($ :div.flex-shrink-0
                ($ :svg {:className "h-5 w-5 text-blue-400" :fill "currentColor" :viewBox "0 0 20 20"}
                   ($ :path {:fillRule "evenodd" :d "M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7-4a1 1 0 11-2 0 1 1 0 012 0zM9 9a1 1 0 000 2v3a1 1 0 001 1h1a1 1 0 100-2v-3a1 1 0 00-1-1H9z" :clipRule "evenodd"})))
             ($ :div.ml-3
                ($ :h3.text-sm.font-medium.text-blue-800 "JSON Schema Guidelines")
                ($ :div.mt-2.text-sm.text-blue-700
                   ($ :ul.list-disc.space-y-1.pl-5
                      ($ :li
                         "Follow "
                         ($ :a.underline.hover:text-blue-900 {:href "https://json-schema.org/" :target "_blank"} "JSON Schema")
                         " specification")
                      ($ :li "AOR supports " ($ :code.bg-blue-100.px-1.rounded "x-javaType") " extension to reference Java types")
                      ($ :li "Do not include " ($ :code.bg-blue-100.px-1.rounded "$schema") " or " ($ :code.bg-blue-100.px-1.rounded "$vocabulary") " keys - these are added automatically")))))))))

(defui EditDatasetForm [{:keys [form-id]}]
  (let [;; Use the centralized form hook
        {:keys [valid? submitting? error]} (forms/use-centralized-form form-id)

        ;; Use the field-specific hook for clean binding
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]

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
                            :error (:error description-field)
                            :rows 3})

       ;; form-error now reads from the centralized state
       ($ forms/form-error {:error error}))))

(defui AddExampleForm [{:keys [form-id]}]
  (let [;; Use the centralized form hook
        {:keys [valid? submitting? error]} (forms/use-centralized-form form-id)

        ;; Use the field-specific hook for clean binding
        input-field (forms/use-form-field form-id :input)
        output-field (forms/use-form-field form-id :output)]

    ($ forms/form
       ($ forms/form-field {:label "Input (JSON)"
                            :type :textarea
                            :value (:value input-field)
                            :on-change (:on-change input-field)
                            :error (:error input-field)
                            :placeholder "Enter input as a valid JSON object..."
                            :rows 12
                            :class-name "font-mono"})
       ($ forms/form-field {:label "Reference Output (JSON, Optional)"
                            :type :textarea
                            :value (:value output-field)
                            :on-change (:on-change output-field)
                            :error (:error output-field)
                            :placeholder "Enter reference output as valid JSON..."
                            :rows 12
                            :class-name "font-mono"})

       ;; form-error now reads from the centralized state
       ($ forms/form-error {:error error}))))

(defui EditExampleForm [{:keys [module-id dataset-id snapshot-name example-id initial-input initial-output on-success]}]
  (let [;; Convert initial values to JSON strings for form display
        initial-input-str (if (string? initial-input)
                            initial-input
                            (if initial-input
                              (js/JSON.stringify (clj->js initial-input) nil 2)
                              ""))
        initial-output-str (if (string? initial-output)
                             initial-output
                             (if initial-output
                               (js/JSON.stringify (clj->js initial-output) nil 2)
                               ""))

        input-field (forms/use-form-state initial-input-str [forms/required forms/valid-json])
        output-field (forms/use-form-state initial-output-str [forms/valid-json])
        {:keys [submitting? error submit]} (forms/use-global-form-submission :dataset/edit-example)

        ;; Check if form values have changed from initial values
        is-changed? (or (not= (:value input-field) initial-input-str)
                        (not= (:value output-field) initial-output-str))

        is-valid? (and (nil? (:error input-field))
                       (nil? (:error output-field))
                       (not (str/blank? (:value input-field)))
                       is-changed?)

        handle-save (fn []
                      (submit {:module-id module-id
                               :dataset-id dataset-id
                               :snapshot-name snapshot-name
                               :example-id example-id
                               :input (:value input-field)
                               :output (:value output-field)
                               :initial-input initial-input
                               :initial-output initial-output
                               :on-success on-success}))]

    ($ forms/form
       ($ forms/form-field {:label "Input (JSON)"
                            :type :textarea
                            :value (:value input-field)
                            :on-change (:set-value input-field)
                            :error (:error input-field)
                            :placeholder "Enter input as a valid JSON object..."
                            :rows 12
                            :class-name "font-mono"})
       ($ forms/form-field {:label "Reference Output (JSON, Optional)"
                            :type :textarea
                            :value (:value output-field)
                            :on-change (:set-value output-field)
                            :error (:error output-field)
                            :placeholder "Enter reference output as valid JSON..."
                            :rows 12
                            :class-name "font-mono"})

       ($ forms/form-error {:error error}))))

(defui CreateSnapshotForm [{:keys [module-id dataset-id from-snapshot-name on-success]}]
  (let [to-name-field (forms/use-form-state "" [forms/required])
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)
        is-valid? (and (nil? (:error to-name-field))
                       (not (str/blank? (:value to-name-field))))

        handle-create (fn []
                        (set-submitting true)
                        (set-error-msg nil)
                        (sente/request!
                         [:datasets/create-snapshot {:module-id module-id
                                                     :dataset-id dataset-id
                                                     :from-snapshot-name from-snapshot-name
                                                     :to-snapshot-name (:value to-name-field)}]
                         10000
                         (fn [reply]
                           (set-submitting false)
                           (if (:success reply)
                             (do (state/dispatch [:modal/hide])
                                 (on-success))
                             (set-error-msg (:error reply))))))]
    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700 "Source Snapshot")
          ($ :p.mt-1.text-sm.text-gray-500.bg-gray-100.p-2.rounded-md
             (if (str/blank? from-snapshot-name) "Latest (Working Copy)" from-snapshot-name)))

       ($ forms/form-field {:label "New Snapshot Name"
                            :value (:value to-name-field)
                            :on-change (:set-value to-name-field)
                            :error (:error to-name-field)
                            :required? true})
       (when error-msg
         ($ forms/form-error {:error error-msg})))))

(defui DropdownRow [{:keys [label selected? on-select delete-button action? icon]}]
  ($ :div
     {:className (str "group flex items-center justify-between w-full px-4 py-2 text-sm hover:bg-gray-100 cursor-pointer "
                      (cond
                        action? "text-blue-600 hover:bg-blue-50"
                        selected? "text-blue-600 bg-blue-50"
                        :else "text-gray-700"))
      :onClick on-select}
     ($ :div.flex.items-center.justify-between.w-full
        ($ :div.flex.items-center
           (when icon icon)
           ($ :span.truncate {:className (when icon "ml-3")} label))
        ($ :div.flex.items-center.space-x-2
           (when selected? ($ :span "✓"))
           (when delete-button
             ($ :div {:onClick #(.stopPropagation %)}
                delete-button))))))

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
                        (state/dispatch [:modal/show :create-snapshot
                                         {:title "Create New Snapshot"
                                          :component ($ CreateSnapshotForm
                                                        {:module-id module-id
                                                         :dataset-id dataset-id
                                                         :from-snapshot-name selected-snapshot
                                                         :on-success refetch})}]))

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
                                 (refetch)) ;; Refetch the list
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
                         (set-dropdown-open (not dropdown-open?)))
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

(defui ExamplesList [{:keys [examples module-id dataset-id snapshot-name on-delete-success refetch]}]
  (let [[open-dropdown set-open-dropdown] (uix/use-state nil)]

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
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Input")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Output")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Tags")
                ($ :th.relative.px-6.py-3)))
          ($ :tbody.bg-white.divide-y.divide-gray-200
             (for [example examples]
               (let [example-id (:example-id example)
                     is-open? (= open-dropdown example-id)]
                 ($ :tr {:key example-id}
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.font-mono
                       (let [input-str (if (string? (:input example))
                                         (:input example)
                                         (js/JSON.stringify (clj->js (:input example)) nil 2))
                             truncated (if (> (count input-str) 100)
                                         (str (subs input-str 0 97) "...")
                                         input-str)]
                         ($ :span {:title input-str :className "cursor-help"} truncated)))
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.font-mono
                       (let [output-str (if (string? (:reference-output example))
                                          (:reference-output example)
                                          (js/JSON.stringify (clj->js (:reference-output example)) nil 2))
                             truncated (if (> (count output-str) 100)
                                         (str (subs output-str 0 97) "...")
                                         output-str)]
                         ($ :span {:title output-str :className "cursor-help"} (or truncated "—"))))
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-500 (str (:tags example)))
                    ($ :td.px-6.py-4.whitespace-nowrap.text-right.text-sm.font-medium
                       ($ :div.relative.inline-block.text-left
                          ;; Three dots button
                          ($ :button.inline-flex.items-center.justify-center.w-8.h-8.rounded-full.text-gray-400.hover:text-gray-600.hover:bg-gray-100.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500.cursor-pointer
                             {:onClick (fn [e]
                                         (.stopPropagation e)
                                         (set-open-dropdown (if is-open? nil example-id)))}
                             ($ EllipsisVerticalIcon {:className "h-5 w-5"}))

                          ;; Dropdown menu
                          (when is-open?
                            ($ :div.origin-top-right.absolute.right-0.mt-2.w-48.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
                               {:onClick #(.stopPropagation %)}
                               ($ :div.py-1
                                  ;; Edit option
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-gray-100.hover:text-gray-900.cursor-pointer
                                     {:onClick (fn []
                                                 (set-open-dropdown nil)
                                                 (state/dispatch [:modal/show :edit-example
                                                                  {:title "Edit Example"
                                                                   :component ($ EditExampleForm
                                                                                 {:module-id module-id
                                                                                  :dataset-id dataset-id
                                                                                  :snapshot-name snapshot-name
                                                                                  :example-id example-id
                                                                                  :initial-input (:input example)
                                                                                  :initial-output (:reference-output example)
                                                                                  :on-success refetch})}]))}
                                     ($ PencilIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                     "Edit")

                                  ;; Delete option
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-red-100.hover:text-red-800.cursor-pointer
                                     {:onClick (fn []
                                                 (set-open-dropdown nil)
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
                                                        (when on-delete-success (on-delete-success))
                                                        (js/alert (str "Error deleting example: " (:error reply))))))))}
                                     ($ TrashIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-red-500"})
                                     "Delete")

                                  ;; Try with evaluator option
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-gray-100.hover:text-gray-900.cursor-pointer
                                     {:onClick (fn []
                                                 (set-open-dropdown nil)
                                                 ;; TODO: Implement evaluator functionality
                                                 (println "Try with evaluator:" example-id))}
                                     ($ PlayIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                     "Try with evaluator"))))))))))))))

(defn get-dataset-path [module-id dataset-id]
  (rfe/href :module/dataset-detail
            {:module-id (common/url-encode module-id)
             :dataset-id (str dataset-id)}))

;; =============================================================================
;; DATASETS INDEX PAGE
;; =============================================================================
 ;; Helper function to show edit dataset modal with centralized state
(defn show-edit-dataset-modal! [module-id dataset-id initial-name initial-description on-success]
  ;; 1. Initialize the form state
  (state/dispatch [:form/init :edit-dataset
                   {:fields {:name initial-name
                             :description initial-description}
                    :validators {:name [forms/required]}
                    :submit-event [:dataset/edit
                                   {:module-id module-id
                                    :dataset-id dataset-id
                                    :initial-name initial-name
                                    :initial-description initial-description
                                    :on-success on-success}]}])

  ;; 2. Show the modal with the component
  (state/dispatch [:modal/show :edit-dataset
                   {:title (str "Edit Dataset: " initial-name)
                    :form-id :edit-dataset
                    :submit-text "Save Changes"
                    :component ($ EditDatasetForm {:form-id :edit-dataset})}]))

;; Helper function to show add example modal with centralized state  
(defn show-add-example-modal! [module-id dataset-id snapshot-name on-success]
  ;; 1. Initialize the form state
  (state/dispatch [:form/init :add-example
                   {:fields {:input ""
                             :output ""}
                    :validators {:input [forms/required forms/valid-json]
                                 :output [forms/valid-json]}
                    :submit-event [:dataset/add-example
                                   {:module-id module-id
                                    :dataset-id dataset-id
                                    :snapshot-name snapshot-name
                                    :on-success on-success}]}])

  ;; 2. Show the modal with the component
  (state/dispatch [:modal/show :add-example
                   {:title "Add Example"
                    :form-id :add-example
                    :submit-text "Add Example"
                    :component ($ AddExampleForm {:form-id :add-example})}]))

(defui datasets-index []
  (let [;; Get module_id from route, needs decoding for display
        module-id-raw (get-in (state/use-sub [:route]) [:path-params :module-id])
        module-id (when module-id-raw (common/url-decode module-id-raw))

        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:datasets module-id]
          :sente-event [:datasets/get-all {:module-id module-id-raw :pagination nil}]
          :enabled? (boolean module-id-raw)
          :refetch-interval-ms 5000})
        datasets (get-in data [:datasets])]

    ($ :div.p-6
       ;; Header
       ($ :div.flex.items-center.justify-between.mb-6
          ($ :div
             ($ :h1.text-2xl.font-bold.text-gray-900 "Datasets for " ($ :span.text-indigo-600 module-id))
             ($ :p.mt-2.text-sm.text-gray-600 "Create and manage datasets for agent training and evaluation."))
          ($ :button.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer
             {:onClick (fn []
                         ;; Initialize the form state
                         (state/dispatch [:form/init :create-dataset
                                          {:fields {:name ""
                                                    :description ""
                                                    :input-schema ""
                                                    :output-schema ""}
                                           :validators {:name [forms/required]
                                                        :input-schema [forms/valid-json]
                                                        :output-schema [forms/valid-json]}
                                           :submit-event [:dataset/create {:module-id module-id-raw
                                                                           :on-success refetch
                                                                           :form-id :create-dataset}]}])
                         ;; Show the modal
                         (state/dispatch [:modal/show :create-dataset
                                          {:title "Create New Dataset"
                                           :form-id :create-dataset
                                           :submit-text "Create Dataset"
                                           :component ($ CreateDatasetForm {:form-id :create-dataset})}]))}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Create New Dataset"))

       (cond
         loading? ($ :div.text-center.py-12 "Loading datasets...")
         error ($ :div.text-center.py-12.text-red-500 "Error: " error)
         (empty? datasets)
         ($ :div.text-center.py-12
            ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400"})
            ($ :h3.mt-2.text-sm.font-medium.text-gray-900 "No datasets yet")
            ($ :p.mt-1.text-sm.text-gray-500 "Get started by creating your first dataset."))
         :else
         ($ :div.space-y-4
            (for [dataset datasets]
              ($ :div.bg-white.shadow.rounded-lg.p-6 {:key (:dataset-id dataset)}
                 ($ :div.flex.items-center.justify-between
                    ;; Link to the detail page (now view-only for properties)
                    ($ :a {:href (get-dataset-path module-id (:dataset-id dataset))
                           :className "flex-grow"}
                       ($ :h3.text-lg.font-medium.text-gray-900.hover:text-blue-600 (:name dataset))
                       ($ :p.mt-1.text-sm.text-gray-600 (or (:description dataset) "No description.")))

                    ;; Action buttons
                    ($ :div.flex.space-x-4.flex-shrink-0.ml-4
                       ;; Edit Button
                       ($ :button.text-gray-600.hover:text-gray-800.p-1.rounded-full.hover:bg-gray-100.cursor-pointer
                          {:title "Edit Dataset"
                           :onClick #(show-edit-dataset-modal! module-id-raw
                                                               (:dataset-id dataset)
                                                               (:name dataset)
                                                               (:description dataset)
                                                               refetch)}
                          ($ PencilIcon {:className "h-5 w-5"}))

                       ;; Delete Button
                       ($ :button.text-red-600.hover:text-red-800.p-1.rounded-full.hover:bg-red-100.cursor-pointer
                          {:title "Delete Dataset"
                           :onClick (fn []
                                      (when (js/confirm (str "Are you sure you want to delete '" (:name dataset) "'?"))
                                        (sente/request! [:datasets/delete
                                                         {:module-id module-id-raw :dataset-id (:dataset-id dataset)}]
                                                        5000
                                                        #(when (:success %) (refetch)))))}
                          ($ TrashIcon {:className "h-5 w-5"})))))))))))

;; =============================================================================
;; DATASET DETAIL PAGE
;; =============================================================================

(defn pretty-print-json [json-data]
  "Pretty prints JSON data, handling both strings and objects"
  (try
    (cond
      ;; If it's already a string, try to parse and re-stringify it
      (string? json-data)
      (js/JSON.stringify (js/JSON.parse json-data) nil 2)

      ;; If it's a JavaScript object or Clojure data, stringify it
      (some? json-data)
      (js/JSON.stringify (clj->js json-data) nil 2)

      ;; If it's nil or undefined, return empty string
      :else "")
    (catch js/Error _
      ;; If parsing fails, try to stringify as-is, or fall back to string representation
      (try
        (if (string? json-data)
          json-data
          (js/JSON.stringify (clj->js json-data) nil 2))
        (catch js/Error _
          (str json-data))))))

(defui dataset-detail []
  (let [;; Get IDs from route
        {:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        decoded-module-id (when module-id (common/url-decode module-id))

        ;; State for selected tab
        [active-tab set-active-tab] (uix/use-state "examples")

        ;; State for selected snapshot and info panel
        [selected-snapshot-name set-selected-snapshot-name] (uix/use-state "")
        [show-info? set-show-info] (uix/use-state false)

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
         {:query-key [:dataset-examples module-id dataset-id selected-snapshot-name]
          :sente-event [:datasets/get-examples-page {:module-id module-id
                                                     :dataset-id dataset-id
                                                     :snapshot-name selected-snapshot-name
                                                     :pagination nil}]
          :enabled? (boolean (and module-id dataset-id))})

        ;; Rename destructured keys for clarity
        {examples-response :data, examples-loading? :loading?, examples-error :error, examples-refetch :refetch} examples-query

        ;; 3. Use the correctly named variables
        dataset dataset-props ;; Correctly assign dataset properties

        examples (let [raw-examples (get examples-response :examples) ;; Use renamed response
                       extracted-examples (mapv (fn [[uuid example-data]]
                                                  (assoc example-data :example-id (str uuid)))
                                                raw-examples)]
                   extracted-examples)]

        ;; --- END OF FIX ---

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
            ($ :div.flex-1.overflow-hidden
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
                          ;; Left side - Snapshot Manager
                          ($ :div.flex.items-center.space-x-4
                             ($ :span.text-sm.font-medium.text-gray-700 "Snapshot:")
                             ($ SnapshotManager {:module-id module-id
                                                 :dataset-id dataset-id
                                                 :selected-snapshot selected-snapshot-name
                                                 :set-selected-snapshot set-selected-snapshot-name}))

                          ;; Right side - Add Example button
                          ($ :div.flex.items-center.space-x-4
                             ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer
                                {:onClick #(show-add-example-modal! module-id
                                                                    dataset-id
                                                                    selected-snapshot-name
                                                                    examples-refetch)}
                                ($ PlusIcon {:className "h-4 w-4 mr-2"})
                                "Add Example"))))

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
                                                         :on-delete-success examples-refetch
                                                         :refetch examples-refetch})))))))

                 ;; Default case
                 ($ :div.flex.items-center.justify-center.h-full
                    ($ :div.text-center.text-gray-500
                       ($ :p "Unknown tab selected."))))))
         :else ($ :div.p-6 "No data available.")))))

;; =============================================================================
;; EXPORTS
;; =============================================================================

(def index datasets-index)
(def detail dataset-detail)
