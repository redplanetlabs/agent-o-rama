(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon XMarkIcon LockClosedIcon InformationCircleIcon DocumentDuplicateIcon MagnifyingGlassIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.datasets-forms :as datasets-forms]
   [com.rpl.agent-o-rama.ui.datasets.snapshot-selector :as snapshot-selector]
   [com.rpl.agent-o-rama.ui.evaluators :as evaluators]
   [com.rpl.agent-o-rama.ui.experiments.index :as experiments]
   [reitit.frontend.easy :as rfe]
   [clojure.string :as str]
   [com.rpl.specter :as s]))

(defui SourceDisplay [{:keys [example]}]
  (let [source-string (:source-string example)]
    ($ :div.flex.flex-col.gap-1
       ($ :div.flex.items-center
          source-string))))

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
                               (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
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
                                       (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
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
                                      (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
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

            ;; Source section
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Source")
               ($ :div.bg-gray-50.rounded-md.p-4.border
                  ($ SourceDisplay {:example example})))

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
                                  (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
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
                                   (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
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
               ($ common/DropdownRow {:label "Select evaluator..."
                                      :selected? (nil? selected-evaluator)
                                      :on-select #(do
                                                    (set-open false)
                                                    (on-select nil))
                                      :delete-button nil})

               ;; Evaluator options
               (for [evaluator evaluators]
                 ($ common/DropdownRow {:key (:id evaluator)
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
;; CONTEXTUAL ACTION BAR
;; =============================================================================

(defui ContextualActionBar [{:keys [module-id dataset-id snapshot-name selected-example-ids examples is-read-only?]}]
  (let [example-count (count selected-example-ids)
        ;; Filter examples to get only the selected ones
        selected-examples (filter #(contains? selected-example-ids (:id %)) examples)]

    ($ :div.bg-gray-50.border-b.border-gray-200.px-6.py-3
       ($ :div.flex.items-center.justify-between
          ;; Left side - Selection info and clear button
          ($ :div.flex.items-center.space-x-4
             ($ :span.text-sm.font-medium.text-gray-900
                (str example-count " example"
                     (when (> example-count 1) "s")
                     " selected")))
          ;; Right side - Action buttons
          ($ :div.flex.items-center.space-x-2
             ;; Add Tag button
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:disabled is-read-only?
                 :onClick #(when-not is-read-only?
                             (datasets-forms/show-add-tag-modal! {:module-id module-id
                                                                  :dataset-id dataset-id
                                                                  :snapshot-name snapshot-name
                                                                  :example-ids selected-example-ids}))
                 :title (when is-read-only? "Cannot add tags to a read-only snapshot.")}
                "Add Tag...")

             ;; Remove Tag button
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:disabled is-read-only?
                 :onClick #(when-not is-read-only?
                             (datasets-forms/show-remove-tag-modal! {:module-id module-id
                                                                     :dataset-id dataset-id
                                                                     :snapshot-name snapshot-name
                                                                     :example-ids selected-example-ids
                                                                     :selected-examples selected-examples}))
                 :title (when is-read-only? "Cannot remove tags from a read-only snapshot.")}
                "Remove Tag...")

             ;; Try Summary Evaluator button
             ($ :button.px-3.py-1.text-sm.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:onClick #(when (seq selected-example-ids)
                             ;; Show the new unified modal in :multi mode
                             (state/dispatch [:modal/show :run-evaluator
                                              {:title "Run Summary Evaluation"
                                               :component ($ evaluators/RunEvaluatorModal {:module-id module-id
                                                                                           :dataset-id dataset-id
                                                                                           :mode :multi
                                                                                           :selected-example-ids selected-example-ids})}]))}
                "Try summary evaluator")

             ;; Delete Selected button
             ($ :button.px-3.py-1.text-sm.bg-red-600.text-white.rounded-md.hover:bg-red-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:disabled is-read-only?
                 :onClick #(when-not is-read-only?
                             (datasets-forms/handle-delete-selected! module-id dataset-id snapshot-name selected-example-ids))
                 :title (when is-read-only? "Cannot delete examples from a read-only snapshot.")}
                "Delete Selected"))))))

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
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Created")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Modified")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Source")
                ($ :th.px-6.py-3.text-left.text-xs.font-medium.text-gray-500.uppercase.tracking-wider "Actions")))
          ($ :tbody.bg-white.divide-y.divide-gray-200
             (for [example examples]
               (let [example-id (:id example)
                     is-open? (= open-dropdown example-id)
                     is-selected? (contains? selected-ids example-id)]
                 ($ :tr {:key example-id
                         :className (common/cn "hover:bg-gray-50 cursor-pointer"
                                               {"bg-blue-50" is-selected?})
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
                    ;; Tags column
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-500
                       (let [tags (:tags example)]
                         (if (and tags (seq tags))
                           (->> tags
                                (map name) ; Convert keywords to strings
                                (sort) ; Sort alphabetically
                                (clojure.string/join ", ")) ; Join with commas
                           ($ :span.italic "no tags"))))
;; Created timestamp column
                    ;; Created timestamp column
                    ($ :td.px-6.py-4.text-sm.text-gray-600
                       {:title (common/format-timestamp (:created-at example))}
                       (common/format-relative-time (:created-at example)))
;; Modified timestamp column
                    ($ :td.px-6.py-4.text-sm.text-gray-600
                       {:title (common/format-timestamp (:modified-at example))}
                       (common/format-relative-time (:modified-at example)))
                    ($ :td.px-6.py-4.whitespace-nowrap.text-sm.text-gray-500
                       ($ SourceDisplay {:example example :full-details? false}))
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
                                                   ;; Show the new unified modal in :single mode
                                                   (state/dispatch [:modal/show :run-evaluator
                                                                    {:title "Try Evaluator on Example"
                                                                     :component ($ evaluators/RunEvaluatorModal {:module-id module-id
                                                                                                                 :dataset-id dataset-id
                                                                                                                 :mode :single
                                                                                                                 :example example})}]))}
                                       ($ PlayIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                       "Try with evaluator")
                                    ;; Duplicate button
                                    ($ :button
                                       {:className "group flex items-center w-full px-4 py-2 text-sm text-gray-700 hover:bg-gray-100 hover:text-gray-900 cursor-pointer"
                                        :onClick (fn [e]
                                                   (.stopPropagation e)
                                                   (set-open-dropdown nil) ; Close dropdown
                                                   (sente/request!
                                                    [:datasets/add-example
                                                     {:module-id module-id
                                                      :dataset-id dataset-id
                                                      :snapshot-name snapshot-name
                                                      ;; Pass the data from the current example
                                                      :input (:input example)
                                                      :output (:reference-output example)
                                                      :tags (vec (:tags example))}] ;; Pass tags as well
                                                    10000
                                                    (fn [reply]
                                                      (if (:success reply)
                                                        ;; Invalidate query to refresh the list with the new example
                                                        (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                                        (js/alert (str "Error duplicating example: " (:error reply)))))))}
                                       ($ DocumentDuplicateIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                       "Duplicate")
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
                                                            (state/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                                            (when on-delete-success (on-delete-success)))
                                                          (js/alert (str "Error deleting example: " (:error reply))))))))}
                                       ($ TrashIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-red-500"})
                                       "Delete")))))))))))))))

(defn get-dataset-path [module-id dataset-id]
  (rfe/href :module/dataset-detail.examples
            {:module-id module-id
             :dataset-id dataset-id}))

;; =============================================================================
;; MAIN DATASETS INDEX PAGE
;; =============================================================================

(defui index [{:keys [module-id]}]
  (let [;; Add state for search term and debounce it
        [search-term set-search-term] (useState "")
        [debounced-search-term] (useDebounce search-term 300)

        ;; Update the query to use the debounced search term
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:datasets module-id debounced-search-term]
          :sente-event [:datasets/get-all
                        {:module-id module-id
                         :pagination nil
                         :filters (when-not (str/blank? debounced-search-term)
                                    {:search-string debounced-search-term})}]
          :enabled? (boolean module-id)})

        datasets (:datasets data)]

    ($ :div.p-6
       ;; Header with search input
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex.items-center.gap-3
             ($ CircleStackIcon {:className "h-8 w-8 text-indigo-600"})
             ;; Search input field
             ($ :div.relative.ml-4
                ($ :div.pointer-events-none.absolute.inset-y-0.left-0.flex.items-center.pl-3
                   ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"}))
                ($ :input
                   {:type "text"
                    :value search-term
                    :onChange #(set-search-term (.. % -target -value))
                    :className "block w-full rounded-md border-0 py-1.5 pl-10 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    :placeholder "Search datasets..."})))

          ($ :div.flex.items-center.gap-2
             ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
                {:onClick #(state/dispatch [:modal/show-form :create-dataset {:module-id module-id}])}
                ($ PlusIcon {:className "h-5 w-5 mr-2"})
                "Create Dataset")))

       ;; Content
       (cond
         loading? ($ :div.flex.items-center.justify-center.h-full ($ :div "Loading datasets..."))
         error ($ :div.flex.items-center.justify-center.h-full ($ :div.text-red-500 "Error loading datasets"))
         (empty? datasets)
         ($ :div.text-center.py-12
            ($ CircleStackIcon {:className "mx-auto h-12 w-12 text-gray-400 mb-4"})
            ($ :h3.text-lg.font-medium.text-gray-900.mb-2 "No datasets yet")
            ($ :p.text-gray-500.mb-6 "Create your first dataset to get started.")
            ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
               {:onClick #(state/dispatch [:modal/show-form :create-dataset {:module-id module-id}])}
               ($ PlusIcon {:className "h-5 w-5 mr-2"})
               "Create Dataset"))
         :else
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Description")
                     ($ :th {:className (:th common/table-classes)} "Created")
                     ($ :th {:className (:th common/table-classes)} "Modified")
                     ($ :th {:className (:th common/table-classes)} "Actions")))
               ($ :tbody
                  (into []
                        (for [dataset datasets
                              :let [name (:name dataset)
                                    desc (:description dataset)
                                    dsid (:dataset-id dataset)
                                    href (get-dataset-path module-id dsid)]]
                          ($ :tr {:key dsid
                                  :className "hover:bg-gray-50 cursor-pointer"
                                  :onClick (fn [_]
                                             (rfe/push-state :module/dataset-detail.examples
                                                             {:module-id module-id
                                                              :dataset-id dsid}))}
                             ($ :td {:className (:td common/table-classes)}
                                ($ :a.text-indigo-600.hover:text-indigo-800 {:href href} name))
                             ($ :td {:className (:td common/table-classes)}
                                (if (seq (str desc))
                                  ($ :span.text-sm.text-gray-600.desc.truncate {:title desc} desc)
                                  ($ :span.text-sm.text-gray-400.italic "—")))
                             ($ :td {:className (:td common/table-classes)}
                                ($ :span.text-sm.text-gray-600 {:title (common/format-timestamp (:created-at dataset))}
                                   (common/format-relative-time (:created-at dataset))))
                             ($ :td {:className (:td common/table-classes)}
                                ($ :span.text-sm.text-gray-600 {:title (common/format-timestamp (:modified-at dataset))}
                                   (common/format-relative-time (:modified-at dataset))))
                             ($ :td {:className (:td-right common/table-classes)}
                                ($ :div.flex.items-center.space-x-2
                                   ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-gray-700.cursor-pointer
                                      {:onClick (fn [e]
                                                  (.preventDefault e)
                                                  (.stopPropagation e)
                                                  (state/dispatch [:modal/show-form :edit-dataset
                                                                   {:module-id module-id
                                                                    :dataset-id dsid
                                                                    :name name
                                                                    :description desc
                                                                    :initial-name name
                                                                    :initial-description desc}]))}
                                      ($ PencilIcon {:className "h-4 w-4 mr-1"})
                                      "Edit")
                                   ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                      {:onClick (fn [e]
                                                  (.preventDefault e)
                                                  (.stopPropagation e)
                                                  (when (js/confirm (str "Are you sure you want to delete dataset '" name "'? This action cannot be undone."))
                                                    (sente/request!
                                                     [:datasets/delete {:module-id module-id :dataset-id dsid}]
                                                     10000
                                                     (fn [reply]
                                                       (if (:success reply)
                                                         (state/dispatch [:query/invalidate {:query-key-pattern [:datasets module-id]}])
                                                         (js/alert (str "Error deleting dataset: " (:error reply))))))))}
                                      ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                      "Delete")))))))))))))

;; =============================================================================
;; PRETTY PRINT UTILITY
;; =============================================================================

(defn pretty-print-json [json-data]
  (try
    (js/JSON.stringify (clj->js json-data) nil 2)
    (catch js/Error _
      (str json-data))))

(defui ImportResultsModal [{:keys [success_count failure_count errors]}]
  ($ :div.p-6
     ;; Summary section
     ($ :div.mb-6
        ($ :div.flex.items-center.gap-4.mb-4
           (if (zero? failure_count)
             ($ :div.flex.items-center.gap-2.text-green-700
                ($ :svg.h-6.w-6.text-green-600 {:fill "currentColor" :viewBox "0 0 20 20"}
                   ($ :path {:fillRule "evenodd"
                             :d "M10 18a8 8 0 100-16 8 8 0 000 16zm3.707-9.293a1 1 0 00-1.414-1.414L9 10.586 7.707 9.293a1 1 0 00-1.414 1.414l2 2a1 1 0 001.414 0l4-4z"
                             :clipRule "evenodd"}))
                ($ :h3.text-lg.font-semibold "Import Successful"))
             ($ :div.flex.items-center.gap-2.text-yellow-700
                ($ :svg.h-6.w-6.text-yellow-600 {:fill "currentColor" :viewBox "0 0 20 20"}
                   ($ :path {:fillRule "evenodd"
                             :d "M8.257 3.099c.765-1.36 2.722-1.36 3.486 0l5.58 9.92c.75 1.334-.213 2.98-1.742 2.98H4.42c-1.53 0-2.493-1.646-1.743-2.98l5.58-9.92zM11 13a1 1 0 11-2 0 1 1 0 012 0zm-1-8a1 1 0 00-1 1v3a1 1 0 002 0V6a1 1 0 00-1-1z"
                             :clipRule "evenodd"}))
                ($ :h3.text-lg.font-semibold "Import Completed with Errors"))))

        ;; Stats
        ($ :div.grid.grid-cols-2.gap-4.text-sm
           ($ :div.bg-green-50.border.border-green-200.rounded-lg.p-3
              ($ :div.text-green-800.font-medium "Successful")
              ($ :div.text-2xl.font-bold.text-green-900 success_count))
           ($ :div.bg-red-50.border.border-red-200.rounded-lg.p-3
              ($ :div.text-red-800.font-medium "Failed")
              ($ :div.text-2xl.font-bold.text-red-900 failure_count))))

     ;; Errors section (only show if there are errors)
     (when (and (seq errors) (> failure_count 0))
       ($ :div.mt-6
          ($ :h4.text-md.font-semibold.text-gray-900.mb-3 "Error Details")
          ($ :div.max-h-96.overflow-y-auto.border.border-gray-200.rounded-lg
             ($ :div.divide-y.divide-gray-200
                (for [[idx error] (map-indexed vector errors)]
                  ($ :div.p-4.hover:bg-gray-50 {:key idx}
                     ($ :div.text-sm.font-medium.text-gray-900.mb-2
                        (str "Line " (inc idx) ":"))
                     ($ :div.text-xs.font-mono.text-gray-600.bg-gray-100.p-2.rounded.mb-2.break-all
                        (:line_content error))
                     ($ :div.text-sm.text-red-600
                        (:error error))))))))))

;; =============================================================================
;; DATASET DETAIL EXAMPLES TAB COMPONENT
;; =============================================================================

(defui detail-examples [{:keys [module-id dataset-id]}]
  (let [;; Get selected examples for this dataset
        selected-example-ids (or (state/use-sub [:ui :datasets :selected-examples dataset-id]) #{})

        ;; --- REFACTORED ---
        ;; State for selected snapshot now comes from app-db and is updated via dispatch
        selected-snapshot-name (or (state/use-sub [:ui :datasets :selected-snapshot-per-dataset dataset-id]) "")
        set-selected-snapshot-name (fn [new-name]
                                     (state/dispatch [:datasets/set-selected-snapshot
                                                      {:dataset-id dataset-id :snapshot-name new-name}]))
        is-read-only? (not (str/blank? selected-snapshot-name))

        ;; State for search string
        [search-string set-search-string] (uix/use-state "")

        ;; Fetch examples
        {:keys [data loading? error refetch]}
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

        examples (get data :examples)]

    ($ :div.h-full.flex.flex-col
       ;; Examples Tab Header with Controls
       ($ :div.bg-gray-50.border-b.border-gray-200.px-6.py-4
          ($ :div.flex.items-center.justify-between
             ;; Left side - Snapshot Manager and Search
             ($ :div.flex.items-center.space-x-4
                ($ :span.text-sm.font-medium.text-gray-700 "Snapshot:")
                ;; --- REPLACED ---
                ($ snapshot-selector/SnapshotManager {:module-id module-id
                                                      :dataset-id dataset-id
                                                      :selected-snapshot selected-snapshot-name
                                                      :on-select-snapshot set-selected-snapshot-name})

                ;; Search input field
                ($ :input.ml-4.px-3.py-1.border.border-gray-300.rounded-md.text-sm
                   {:type "text"
                    :placeholder "Search examples..."
                    :value search-string
                    :onChange #(set-search-string (.. % -target -value))}))

;; Right side - Action buttons
             ($ :div.flex.items-center.space-x-4
                   ;; Export button
                ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-green-600.hover:bg-green-700.cursor-pointer
                   {:onClick (fn [_]
                               (let [snapshot-param (when-not (str/blank? selected-snapshot-name)
                                                      (str "?snapshot=" (common/url-encode selected-snapshot-name)))
                                     href (str "/api/datasets/" (common/url-encode module-id) "/" (common/url-encode (str dataset-id)) "/export" snapshot-param)]
                                 (set! (.-href js/window.location) href)))}
                   "Export")

                   ;; Import button - next to export
                (let [[uploading? set-uploading!] (uix/use-state false)
                      file-input-ref (uix/use-ref nil)]
                  ($ :<>
                     ($ :input {:ref file-input-ref
                                :type "file"
                                :accept ".jsonl,application/jsonl,application/octet-stream"
                                :style {:display "none"}
                                :key (str "file-input-" dataset-id) ; Force re-render to clear previous selections
                                :onChange (fn [e]
                                            (let [files (.. e -target -files)
                                                  f (when (and files (> (.-length files) 0)) (aget files 0))]
                                              (js/console.log "File selected for import:" (.-name f) "size:" (.-size f))
                                              (when f
                                                (set-uploading! true)
                                                ;; Clear the input value to allow re-selecting the same file
                                                (set! (.. e -target -value) "")
                                                (let [fd (js/FormData.)
                                                      url (str "/api/datasets/" (common/url-encode module-id) "/" (common/url-encode (str dataset-id)) "/import")]
                                                  (.append fd "file" f)
                                                  (-> (js/fetch url #js {:method "POST" :body fd})
                                                      (.then (fn [resp]
                                                               (if (.-ok resp)
                                                                 (.json resp)
                                                                 (throw (js/Error. (str "HTTP " (.-status resp) ": " (.-statusText resp)))))))
                                                      (.then (fn [data]
                                                               (set-uploading! false)
                                                               ;; Refresh examples after import
                                                               (refetch)
                                                               ;; Show results modal instead of alert
                                                               (state/dispatch [:modal/show :import-results
                                                                                {:title "Import Results"
                                                                                 :component ($ ImportResultsModal
                                                                                               {:success_count (.-success_count data)
                                                                                                :failure_count (.-failure_count data)
                                                                                                :errors (js->clj (.-errors data) :keywordize-keys true)})}])))
                                                      (.catch (fn [err]
                                                                (set-uploading! false)
                                                                (state/dispatch [:modal/show :import-error
                                                                                 {:title "Import Failed"
                                                                                  :component ($ :div.p-6
                                                                                                ($ :div.flex.items-center.gap-2.text-red-700.mb-4
                                                                                                   ($ :svg.h-6.w-6.text-red-600 {:fill "currentColor" :viewBox "0 0 20 20"}
                                                                                                      ($ :path {:fillRule "evenodd"
                                                                                                                :d "M18 10a8 8 0 11-16 0 8 8 0 0116 0zm-7 4a1 1 0 11-2 0 1 1 0 012 0zm-1-9a1 1 0 00-1 1v4a1 1 0 102 0V6a1 1 0 00-1-1z"
                                                                                                                :clipRule "evenodd"}))
                                                                                                   ($ :h3.text-lg.font-semibold "Import Failed"))
                                                                                                ($ :div.text-sm.text-gray-700
                                                                                                   ($ :p "The import operation failed with the following error:")
                                                                                                   ($ :div.mt-2.p-3.bg-red-50.border.border-red-200.rounded.text-red-800.font-mono.text-xs
                                                                                                      (str err))))}]))))))))})
                     ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-indigo-600.hover:bg-indigo-700.cursor-pointer.disabled:bg-gray-400.disabled:cursor-not-allowed
                        {:onClick (fn [_]
                                    (when-let [node (.-current file-input-ref)]
                                      (.click node)))
                         :disabled is-read-only?
                         :title (when is-read-only? "Cannot import into a read-only snapshot.")}
                        (if uploading? "Uploading..." "Import"))))

                   ;; Add Example button
                ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer.disabled:bg-gray-400.disabled:cursor-not-allowed
                   {:onClick #(datasets-forms/show-add-example-modal!
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
       (if (seq selected-example-ids)
         ($ ContextualActionBar {:module-id module-id
                                 :dataset-id dataset-id
                                 :snapshot-name selected-snapshot-name
                                 :selected-example-ids selected-example-ids
                                 :examples examples
                                 :is-read-only? is-read-only?})
         ($ :div.h-10)) ;; Placeholder to maintain layout height 

       ;; Examples Content
       ($ :div.flex-1.overflow-hidden
          ($ :div.h-full.flex.flex-col
             ($ :div.flex-1.overflow-hidden
                (cond
                  loading? ($ :div.flex.items-center.justify-center.h-full
                              ($ :div "Loading examples..."))
                  error ($ :div.flex.items-center.justify-center.h-full
                           ($ :div.text-red-500 "Error loading examples."))
                  (empty? examples) ($ :div.flex.items-center.justify-center.h-full
                                       ($ :div.text-center.text-gray-500
                                          ($ :p "No examples yet.")
                                          ($ :p.text-sm.mt-1 "Click 'Add Example' to get started.")))
                  :else ($ :div.h-full.overflow-auto.min-h-screen
                           ($ ExamplesList {:examples examples
                                            :module-id module-id
                                            :dataset-id dataset-id
                                            :snapshot-name selected-snapshot-name
                                            :is-read-only? is-read-only?})))))))))

;; =============================================================================
;; DATASET DETAIL LAYOUT COMPONENT
;; =============================================================================

(defui detail [{:keys [match module-id dataset-id]}]
  (let [route-name (get-in match [:data :name])
        experiments-routes #{:module/dataset-detail
                             :module/dataset-detail.experiments
                             :module/dataset-detail.experiment-detail}
        comparative-routes #{:module/dataset-detail.comparative-experiments
                             :module/dataset-detail.comparative-experiment-detail}
        active-tab (cond
                     (contains? comparative-routes route-name)
                     "comparative"

                     (contains? experiments-routes route-name)
                     "experiments"

                     :else "examples")
        [show-info? set-show-info] (uix/use-state false)
        {:keys [loading? error]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id :dataset-id dataset-id}]
          :enabled? (boolean (and module-id dataset-id))})
        ;; not sure about doing it this way, why not. maybe we can eventually decouple fetching from views?
        dataset (state/use-sub [:queries :dataset-props module-id dataset-id :data])]
    ($ :div.h-full.flex.flex-col
       (cond
         loading? ($ :div.p-6 "Loading dataset details...")
         error ($ :div.p-6 "Error: " error)
         dataset
         ($ :div.h-full.flex.flex-col
            ;; Header Bar for the whole dataset page
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
                  ;; Right side - reserved for actions
                  ($ :div.flex.items-center.space-x-4)))

            ;; Collapsible info panel
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

            ;; Tab navigation bar
            ($ :div.bg-white.border-b.border-gray-200
               ($ :nav.flex.space-x-8.px-6
                  ($ :a {:href (rfe/href :module/dataset-detail.examples {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "examples")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "examples")})}
                     "Examples")
                  ($ :a {:href (rfe/href :module/dataset-detail.experiments {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "experiments")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "experiments")})}
                     "Experiments")
                  ($ :a {:href (rfe/href :module/dataset-detail.comparative-experiments {:module-id module-id, :dataset-id dataset-id}),
                         :className (common/cn "py-2 px-1 border-b-2 font-medium text-sm"
                                               {"border-indigo-500 text-indigo-600" (= active-tab "comparative")
                                                "border-transparent text-gray-500 hover:text-gray-700 hover:border-gray-300" (not= active-tab "comparative")})}
                     "Comparative Experiments"))))
         :else ($ :div.p-6 "Dataset not found.")))))
