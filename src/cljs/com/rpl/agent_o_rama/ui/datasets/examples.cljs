(ns com.rpl.agent-o-rama.ui.datasets.examples
  (:require
   [uix.core :as uix :refer [defui $]]
   [uix.re-frame :refer [use-subscribe]]
   ["@heroicons/react/24/outline" :refer [TrashIcon PencilIcon EllipsisVerticalIcon PlayIcon XMarkIcon LockClosedIcon InformationCircleIcon DocumentDuplicateIcon PlusIcon]]
   ["use-debounce" :refer [useDebounce]]
   [clojure.set]
   [clojure.string :as str]
   [re-frame.core :as rf]
   [re-frame.query :as rfq]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.datasets-forms :as datasets-forms]
   [com.rpl.agent-o-rama.ui.datasets.snapshot-selector :as snapshot-selector]
   [com.rpl.agent-o-rama.ui.datasets.common :refer [pretty-print-json]]
   [com.rpl.agent-o-rama.ui.evaluators :as evaluators]
   [com.rpl.agent-o-rama.ui.rpc :as rpc]
   [com.rpl.agent-o-rama.impl.ui.rpc.datasets :as rpc-datasets]))

(defui SourceDisplay [{:keys [example]}]
  (let [source-string (:source-string example)]
    ($ :div.flex.flex-col.gap-1
       ($ :div.flex.items-center
          source-string))))

(defui EditableField [{:keys [label value field-key example-id module-id dataset-id snapshot-name on-save current-example read-only?]}]
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
                                (-> (rpc/call ::rpc-datasets/edit-example!!
                                 {:module-id module-id
                                  :dataset-id dataset-id
                                  :snapshot-name snapshot-name
                                  :example-id example-id
                                  :input (:input updated-example)
                                  :reference-output (:reference-output updated-example)})
                                (.then (fn [_]
                                         (set-saving! false)
                                         (set-editing! false)
                                         (set-edit-value! "")
                                         (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                         (rf/dispatch [:re-frame.query/invalidate-tags
                                                       [[:fetch-example module-id dataset-id example-id]
                                                        [:dataset-examples module-id dataset-id]]])
                                         (js/setTimeout
                                          #(rf/dispatch [:re-frame.query/ensure-query
                                                         ::rpc-datasets/fetch-example!!
                                                         {:module-id module-id :dataset-id dataset-id
                                                          :snapshot-name snapshot-name :example-id example-id}])
                                          50)
                                         (when on-save (on-save))))
                                (.catch (fn [err]
                                          (set-saving! false)
                                          (set-error! (str "Error saving: " (if (map? err) (or (:error err) (str err)) (str err))))))))
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

(defui TagInput [{:keys [tags module-id dataset-id snapshot-name example-id on-tags-change read-only?]}]
  (let [[input-value set-input-value] (uix/use-state "")
        [is-adding set-is-adding] (uix/use-state false)

        handle-add-tag (fn [tag-name]
                         (when-not (or (str/blank? tag-name) (contains? (set (map name tags)) tag-name))
                           (set-is-adding true)
                           (-> (rpc/call ::rpc-datasets/add-tag!!
                             {:module-id module-id
                              :dataset-id dataset-id
                              :snapshot-name snapshot-name
                              :example-id example-id
                              :tag tag-name})
                            (.then (fn [_]
                                     (set-is-adding false)
                                     (set-input-value "")
                                     (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                     (rf/dispatch [:re-frame.query/invalidate-tags
                                                   [[:fetch-example module-id dataset-id example-id]
                                                    [:dataset-examples module-id dataset-id]]])
                                     ;; Force a fresh ensure-query to trigger immediate refetch
                                     (js/setTimeout
                                      #(rf/dispatch [:re-frame.query/ensure-query
                                                     ::rpc-datasets/fetch-example!!
                                                     {:module-id module-id :dataset-id dataset-id
                                                      :snapshot-name snapshot-name :example-id example-id}])
                                      50)
                                     (when on-tags-change (on-tags-change))))
                            (.catch (fn [err]
                                      (set-is-adding false)
                                      (js/alert (str "Error adding tag: " (if (map? err) (or (:error err) (str err)) (str err)))))))))

        handle-remove-tag (fn [tag-name]
                            (-> (rpc/call ::rpc-datasets/remove-tag!!
                              {:module-id module-id
                               :dataset-id dataset-id
                               :snapshot-name snapshot-name
                               :example-id example-id
                               :tag tag-name})
                             (.then (fn [_]
                                      (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                      (rf/dispatch [:re-frame.query/invalidate-tags
                                                    [[:fetch-example module-id dataset-id example-id]
                                                     [:dataset-examples module-id dataset-id]]])
                                      (js/setTimeout
                                       #(rf/dispatch [:re-frame.query/ensure-query
                                                      ::rpc-datasets/fetch-example!!
                                                      {:module-id module-id :dataset-id dataset-id
                                                       :snapshot-name snapshot-name :example-id example-id}])
                                       50)
                                      (when on-tags-change (on-tags-change))))
                             (.catch (fn [err] (js/alert (str "Error removing tag: " (if (map? err) (or (:error err) (str err)) (str err))))))))

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

(defui EditableExampleModal [{:keys [example-id module-id dataset-id snapshot-name on-delete-success is-read-only?]}]
  (let [;; Fetch the specific example data
        {:keys [data loading? error refetch]}
        (queries/use-rpc-query
         {:rfq-key ::rpc-datasets/get-example!!
          :params {:module-id module-id
                   :dataset-id dataset-id
                   :snapshot-name snapshot-name
                   :example-id example-id}
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
                               (rf/dispatch [:modal/hide]) ; Close modal before deleting
                               (-> (rpc/call ::rpc-datasets/delete-example!!
                                      {:module-id module-id :dataset-id dataset-id
                                       :snapshot-name snapshot-name :example-id example-id})
                                  (.then (fn [_]
                                           (do (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                   (rf/dispatch [:re-frame.query/invalidate-tags [[:dataset-examples module-id dataset-id]]]))
                                           (when on-delete-success (on-delete-success))))
                                  (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))}
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
                              :read-only? is-read-only?})

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
                              :read-only? is-read-only?})

            ;; Tags section
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Tags")
               ($ :div.bg-gray-50.rounded-md.p-4.border
                  ($ TagInput {:tags (:tags example) :module-id module-id :dataset-id dataset-id :snapshot-name snapshot-name :example-id example-id :read-only? is-read-only? :on-tags-change refetch})))

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
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:onClick #(when (seq selected-example-ids)
                             ;; Show the new unified modal in :multi mode
                             (rf/dispatch [:modal/show :run-evaluator
                                              {:title "Run Summary Evaluation"
                                               :component ($ evaluators/RunEvaluatorModal {:module-id module-id
                                                                                           :dataset-id dataset-id
                                                                                           :mode :multi
                                                                                           :selected-example-ids selected-example-ids})}]))}
                "Try summary evaluator")

             ;; Run Experiment button
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.cursor-pointer
                {:onClick #(rf/dispatch [:modal/show-form :create-experiment
                                            {:module-id module-id
                                             :dataset-id dataset-id
                                             :snapshot snapshot-name
                                             :selector {:type :example-ids
                                                        :example-ids selected-example-ids}
                                             :spec {:type :regular}}])
                 :title "Run a regular experiment with the selected examples"}
                "Run Experiment")

             ;; Run Comparative Experiment button
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-gray-300.rounded-md.hover:bg-gray-50.cursor-pointer
                {:onClick #(rf/dispatch [:modal/show-form :create-experiment
                                            {:module-id module-id
                                             :dataset-id dataset-id
                                             :snapshot snapshot-name
                                             :selector {:type :example-ids
                                                        :example-ids selected-example-ids}
                                             :spec {:type :comparative
                                                    :targets [{:target-spec {:type :agent :agent-name nil}
                                                               :input->args [{:id (random-uuid) :value "$"}]}
                                                              {:target-spec {:type :agent :agent-name nil}
                                                               :input->args [{:id (random-uuid) :value "$"}]}]}}])
                 :title "Run a comparative experiment with the selected examples"}
                "Run Comparative Experiment")

             ;; Delete Selected button
             ($ :button.px-3.py-1.text-sm.bg-white.border.border-red-300.text-red-700.rounded-md.hover:bg-red-50.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
                {:disabled is-read-only?
                 :onClick #(when-not is-read-only?
                             (datasets-forms/handle-delete-selected! module-id dataset-id snapshot-name selected-example-ids))
                 :title (when is-read-only? "Cannot delete examples from a read-only snapshot.")}
                "Delete Selected"))))))

(defui ExamplesList [{:keys [examples module-id dataset-id snapshot-name on-delete-success is-read-only?
                             has-more? is-fetching-more? load-more
                             selected-ids set-selected-ids]}]
  (let [[open-dropdown set-open-dropdown] (uix/use-state nil)
        all-on-page-ids (set (map :id examples))
        all-selected? (and (seq all-on-page-ids)
                           (clojure.set/subset? all-on-page-ids selected-ids))]

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [_]
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
                   {:onClick #(set-selected-ids
                               (if all-selected?
                                 #{}
                                 (into selected-ids all-on-page-ids)))}
                   ($ :input {:type "checkbox"
                              :checked all-selected?
                              :readOnly true
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
                         :onClick #(rf/dispatch [:modal/show :example-viewer
                                                    {:title "Example Details"
                                                     :component ($ EditableExampleModal
                                                                   {:example-id example-id
                                                                    :module-id module-id
                                                                    :dataset-id dataset-id
                                                                    :snapshot-name snapshot-name
                                                                    :on-delete-success on-delete-success
                                                                    :is-read-only? is-read-only?})}])}
                    ;; Checkbox column - entire cell is clickable
                    ($ :td.px-4.py-4.cursor-pointer.hover:bg-blue-100
                       {:onClick (fn [e]
                                   (.stopPropagation e)
                                   (set-selected-ids
                                    (fn [current-ids]
                                      (if (contains? current-ids example-id)
                                        (disj current-ids example-id)
                                        (conj current-ids example-id)))))}
                       ($ :input {:type "checkbox"
                                  :checked is-selected?
                                  :readOnly true
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
                                (map name)
                                (sort)
                                (str/join ", "))
                           ($ :span.italic "no tags"))))
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
                                           (.stopPropagation e)
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
                                                   (set-open-dropdown nil)
                                                   (rf/dispatch [:modal/show :run-evaluator
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
                                                   (set-open-dropdown nil)
                                                   (-> (rpc/call ::rpc-datasets/add-example!!
                                                     {:module-id module-id :dataset-id dataset-id
                                                      :snapshot-name snapshot-name
                                                      :input (:input example)
                                                      :output (:reference-output example)
                                                      :tags (vec (:tags example))})
                                                   (.then (fn [_]
                                                            (do (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                   (rf/dispatch [:re-frame.query/invalidate-tags [[:dataset-examples module-id dataset-id]]]))))
                                                   (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err))))))))}
                                       ($ DocumentDuplicateIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                       "Duplicate")
                                    ;; Delete button
                                    ($ :button
                                       {:className "group flex items-center w-full px-4 py-2 text-sm text-gray-700 hover:bg-red-100 hover:text-red-800 cursor-pointer"
                                        :onClick (fn [e]
                                                   (.stopPropagation e)
                                                   (set-open-dropdown nil)
                                                   (when (js/confirm "Are you sure you want to delete this example?")
                                                     (-> (rpc/call ::rpc-datasets/delete-example!!
                                                      {:module-id module-id :dataset-id dataset-id
                                                       :snapshot-name snapshot-name :example-id example-id})
                                                    (.then (fn [_]
                                                             (do (rf/dispatch [:query/invalidate {:query-key-pattern [:dataset-examples module-id dataset-id]}])
                                   (rf/dispatch [:re-frame.query/invalidate-tags [[:dataset-examples module-id dataset-id]]]))
                                                             (when on-delete-success (on-delete-success))))
                                                    (.catch (fn [err] (js/alert (str "Error: " (if (map? err) (or (:error err) (str err)) (str err)))))))))}
                                       ($ TrashIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-red-500"})
                                       "Delete")))))))))))

          ;; Load More Footer
          (when has-more?
            ($ :tfoot.bg-gray-50.border-t.border-gray-200
               ($ :tr.hover:bg-gray-100.transition-colors.duration-150
                  {:onClick (when-not is-fetching-more? load-more)}
                  ($ :td.px-4.py-3.cursor-pointer {:colSpan 8}
                     ($ :div.flex.justify-center.items-center.text-gray-600.hover:text-gray-800.transition-colors.duration-150
                        ($ :span.mr-2.text-sm.font-medium (if is-fetching-more? "Loading..." "Load More"))
                        (when-not is-fetching-more?
                          ($ :svg.w-4.h-4 {:viewBox "0 0 20 20" :fill "currentColor"}
                             ($ :path {:fillRule "evenodd"
                                       :d "M5.293 7.293a1 1 0 011.414 0L10 10.586l3.293-3.293a1 1 0 111.414 1.414l-4 4a1 1 0 01-1.414 0l-4-4a1 1 0 010-1.414z"
                                       :clipRule "evenodd"}))))))))))))

(defui remote-detail-view [{:keys [dataset]}]
  ($ :div.h-full.flex.items-center.justify-center.p-6
     ($ :div.max-w-2xl.bg-purple-50.border.border-purple-200.rounded-lg.p-8
        ($ :div.flex.items-start.gap-4
           ($ InformationCircleIcon {:className "h-12 w-12 text-purple-600 flex-shrink-0"})
           ($ :div.flex-1
              ($ :h2.text-2xl.font-bold.text-purple-900.mb-4 "This is a Remote Dataset")
              ($ :div.space-y-4.text-gray-700
                 ($ :p.text-base
                    "The examples for this dataset reside on a different cluster and cannot be viewed or edited here.")
                 ($ :p.text-base
                    "However, you can run experiments with your local agents against this remote data by navigating to the "
                    ($ :span.font-semibold "Experiments")
                    " tab above.")
                 (when-let [module-name (:module-name dataset)]
                   ($ :div.mt-6.pt-6.border-t.border-purple-200
                      ($ :h3.text-sm.font-semibold.text-purple-800.mb-2 "Remote Dataset Information")
                      ($ :div.space-y-2.text-sm
                         ($ :div.flex.gap-2
                            ($ :span.font-medium "Module:")
                            ($ :span.font-mono.text-purple-700 module-name))
                         (when-let [host (:remote-host dataset)]
                           ($ :div.flex.gap-2
                              ($ :span.font-medium "Host:")
                              ($ :span.font-mono.text-purple-700 host)))
                         (when-let [port (:remote-port dataset)]
                           ($ :div.flex.gap-2
                              ($ :span.font-medium "Port:")
                              ($ :span.font-mono.text-purple-700 (str port)))))))))))))

(defui detail-examples [{:keys [module-id dataset-id]}]
  (let [;; Local state for selected example IDs
        [selected-example-ids set-selected-example-ids] (uix/use-state #{})

        ;; State for selected snapshot now comes from app-db and is updated via dispatch
        selected-snapshot-name (or (use-subscribe [::aor-rf/get-in [:ui :datasets :selected-snapshot-per-dataset dataset-id]]) "")
        set-selected-snapshot-name (fn [new-name]
                                     (rf/dispatch [:datasets/set-selected-snapshot
                                                      {:dataset-id dataset-id :snapshot-name new-name}]))
        is-read-only? (not (str/blank? selected-snapshot-name))

        ;; State for search string
        [search-string set-search-string] (uix/use-state "")
        [debounced-search-string] (useDebounce search-string 300)

        {:keys [data isLoading isFetchingMore hasMore loadMore error]}
        (queries/use-infinite-rpc-query
         {:rfq-key ::rpc-datasets/search-examples-inf!!
          :params {:module-id module-id
                   :dataset-id dataset-id
                   :snapshot-name selected-snapshot-name
                   :filters (when-not (str/blank? debounced-search-string)
                              {:search-string debounced-search-string})}
          :page-size 20
          :enabled? (boolean (and module-id dataset-id))})

        examples data]

    ($ :div.h-full.flex.flex-col
       ;; Examples Tab Header with Controls
       ($ :div.bg-gray-50.border-b.border-gray-200.px-6.py-4
          ($ :div.flex.items-center.justify-between
             ;; Left side - Snapshot Manager and Search
             ($ :div.flex.items-center.space-x-4
                ($ :span.text-sm.font-medium.text-gray-700 "Snapshot:")
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
                ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.bg-white.border.border-gray-300.hover:bg-gray-50.cursor-pointer
                   {:onClick (fn [_]
                               (let [snapshot-param (when-not (str/blank? selected-snapshot-name)
                                                      (str "?snapshot=" (common/url-encode selected-snapshot-name)))
                                     href (str "/api/datasets/" (common/url-encode module-id) "/" (common/url-encode (str dataset-id)) "/export" snapshot-param)]
                                 (set! (.-href js/window.location) href)))}
                   "Export")

                ;; Import button - next to export
                ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.bg-white.border.border-gray-300.hover:bg-gray-50.cursor-pointer.disabled:opacity-50.disabled:cursor-not-allowed
                   {:onClick #(rf/dispatch [:modal/show :dataset-import
                                               {:title "Import Examples from JSONL"
                                                :component ($ datasets-forms/ImportDatasetModal
                                                              {:module-id module-id
                                                               :dataset-id dataset-id})}])
                    :disabled is-read-only?
                    :title (when is-read-only? "Cannot import into a read-only snapshot.")}
                   "Import")

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
                  (and isLoading (empty? examples))
                  ($ :div.flex.items-center.justify-center.h-full
                     ($ common/spinner {:size :large})
                     ($ :div.ml-2.text-gray-500 "Loading examples..."))

                  error ($ :div.flex.items-center.justify-center.h-full
                           ($ :div.text-red-500 "Error loading examples."))
                  (empty? examples) ($ :div.flex.items-start.justify-center.h-screen
                                       ($ :div.text-center.text-gray-500
                                          ($ :p "No examples yet.")
                                          ($ :p.text-sm.mt-1 "Click 'Add Example' to get started.")))
                  :else ($ :div.h-full.overflow-auto.min-h-screen
                           ($ ExamplesList {:examples examples
                                            :module-id module-id
                                            :dataset-id dataset-id
                                            :snapshot-name selected-snapshot-name
                                            :is-read-only? is-read-only?
                                            ;; Pass local selection state handlers
                                            :selected-ids selected-example-ids
                                            :set-selected-ids set-selected-example-ids
                                            ;; Pass pagination props
                                            :has-more? hasMore
                                            :is-fetching-more? isFetchingMore
                                            :load-more loadMore})))))))))

(defui detail-examples-router [{:keys [module-id dataset-id]}]
  (let [{:keys [data error]
         query-status :status}
        (use-subscribe [::rfq/query ::rpc-datasets/get-props!!
                        {:module-id module-id :dataset-id dataset-id}])
        loading? (#{:loading :idle} query-status)
        dataset data
        is-remote? (boolean (:module-name dataset))]
    (cond
      loading? ($ :div.p-6 "Loading dataset details...")
      error ($ :div.p-6.text-red-500 "Error loading dataset details")
      (not dataset) ($ :div.p-6.text-gray-500 "Dataset not found")
      is-remote? ($ remote-detail-view {:dataset dataset})
      :else ($ detail-examples {:module-id module-id :dataset-id dataset-id}))))
