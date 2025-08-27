(ns com.rpl.agent-o-rama.ui.datasets
  (:require
   [uix.core :as uix :refer [defui defhook $]]
   ["@heroicons/react/24/outline" :refer [CircleStackIcon PlusIcon TrashIcon PencilIcon ChevronDownIcon ChevronUpIcon EllipsisVerticalIcon PlayIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.queries :as queries]
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
(defui CreateDatasetForm [{:keys [module-id on-success]}]
  (let [[name set-name] (uix/use-state "")
        [description set-description] (uix/use-state "")
        [input-schema set-input-schema] (uix/use-state "")
        [output-schema set-output-schema] (uix/use-state "")
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)]

    ;; Test if we can even get console output at all
    (js/console.log "CreateDatasetForm rendered with name:" name)

    (letfn [(handle-create []
              (js/console.log "Button clicked! Name:" name "Module ID:" module-id)
              (println "Button clicked! Name:" name "Module ID:" module-id)
              (js/console.log "About to set submitting state")
              (set-submitting true)
              (set-error-msg nil)
              (js/console.log "Making sente request...")
              (sente/request!
               [:datasets/create {:module-id module-id
                                  :name name
                                  :description description
                                  :input-schema input-schema
                                  :output-schema output-schema}]
               15000 ;; Timeout
               (fn [reply]
                 (js/console.log "Got reply from server:" reply)
                 (println "Got reply from server:" reply)
                 (set-submitting false)
                 (if (:success reply)
                   (do
                     (js/console.log "Success! Hiding modal and calling on-success")
                     (state/dispatch [:modal/hide])
                     (on-success))
                   (do
                     (js/console.log "Error in reply:" (:error reply))
                     (set-error-msg (or (:error reply) "An unknown error occurred.")))))))]

      ($ :div
         ($ :div.space-y-4
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Name *")
               ($ :input {:className "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                          :type "text"
                          :value name
                          :required true
                          :onChange #(set-name (.. % -target -value))}))
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Description (Optional)")
               ($ :textarea {:className "w-full h-15 p-3 border rounded-md text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                             :value description
                             :onChange #(set-description (.. % -target -value))}))
            ($ :div.grid.grid-cols-2.gap-4
               ($ :div
                  ($ :label.block.text-sm.font-medium.text-gray-700 "Input JSON Schema (Optional)")
                  ($ :textarea {:className "w-full h-80 p-3 border rounded-md font-mono text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                                :value input-schema
                                :placeholder example-schema
                                :onChange #(set-input-schema (.. % -target -value))}))
               ($ :div
                  ($ :label.block.text-sm.font-medium.text-gray-700 "Output JSON Schema (Optional)")
                  ($ :textarea {:className "w-full h-80 p-3 border rounded-md font-mono text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                                :value output-schema
                                :placeholder example-schema
                                :onChange #(set-output-schema (.. % -target -value))})))

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
                           ($ :li "Do not include " ($ :code.bg-blue-100.px-1.rounded "$schema") " or " ($ :code.bg-blue-100.px-1.rounded "$vocabulary") " keys - these are added automatically")))))))

         (when error-msg
           ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
              ($ :p.text-sm.text-red-700.whitespace-pre-wrap error-msg)))

         ($ :div.mt-6.flex.justify-end.gap-3
            ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer
               {:type "button" :onClick #(state/dispatch [:modal/hide])}
               "Cancel")
            (let [is-disabled? (or submitting? (str/blank? name))]
              ($ :button
                 {:type "button"
                  :disabled is-disabled?
                  :onClick #(when-not is-disabled? (handle-create))
                  :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                  (if is-disabled?
                                    "text-gray-400 bg-gray-300 cursor-not-allowed"
                                    "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Create")))))))

(defui EditDatasetForm [{:keys [module-id dataset-id initial-name initial-description on-success]}]
  (let [[name set-name] (uix/use-state initial-name)
        [description set-description] (uix/use-state initial-description)
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)
        is-changed? (or (not= name initial-name) (not= description initial-description))]

    (letfn [(handle-save []
              (set-submitting true)
              (set-error-msg nil)

              ;; Create two promises, one for each Sente request
              (let [name-promise (js/Promise.
                                  (fn [resolve reject]
                                    (sente/request!
                                     [:datasets/set-name {:module-id module-id :dataset-id dataset-id :name name}]
                                     5000
                                     #(if (:success %) (resolve %) (reject (:error %))))))
                    desc-promise (js/Promise.
                                  (fn [resolve reject]
                                    (sente/request!
                                     [:datasets/set-description {:module-id module-id :dataset-id dataset-id :description description}]
                                     5000
                                     #(if (:success %) (resolve %) (reject (:error %))))))]

                ;; Use Promise.all to wait for both to complete
                (-> (.all js/Promise [name-promise desc-promise])
                    (.then (fn [_]
                             (set-submitting false)
                             (state/dispatch [:modal/hide])
                             (on-success)))
                    (.catch (fn [error]
                              (set-submitting false)
                              (set-error-msg (str "Failed to save: " error)))))))]

      ($ :div
         ($ :div.space-y-4
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Name *")
               ($ :input {:className "w-full p-3 border rounded-md text-sm transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                          :type "text"
                          :value name
                          :required true
                          :onChange #(set-name (.. % -target -value))}))
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Description (Optional)")
               ($ :textarea {:className "w-full h-24 p-3 border rounded-md text-sm resize-y transition-colors border-gray-300 focus:ring-blue-500 focus:border-blue-500"
                             :value description
                             :onChange #(set-description (.. % -target -value))})))

         (when error-msg
           ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
              ($ :p.text-sm.text-red-700 error-msg)))

         ($ :div.mt-6.flex.justify-end.gap-3
            ($ :button.px-4.py-2.border.border-gray-300.rounded-md.text-sm.font-medium.cursor-pointer
               {:type "button" :onClick #(state/dispatch [:modal/hide])}
               "Cancel")
            (let [is-disabled? (or submitting? (not is-changed?) (str/blank? name))]
              ($ :button
                 {:type "button"
                  :disabled is-disabled?
                  :onClick #(when-not is-disabled? (handle-save))
                  :className (str "px-4 py-2 border border-transparent rounded-md text-sm font-medium flex items-center gap-2 "
                                  (if is-disabled?
                                    "text-gray-400 bg-gray-300 cursor-not-allowed"
                                    "text-white bg-blue-600 hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Save Changes")))))))

(defui AddExampleForm [{:keys [module-id dataset-id snapshot-name on-success]}]
  (let [[input set-input] (uix/use-state "")
        [output set-output] (uix/use-state "")
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)]

    (letfn [(handle-add []
              (set-submitting true)
              (set-error-msg nil)

              ;; Client-side JSON validation for quick feedback
              (try
                (when-not (str/blank? input) (js/JSON.parse input))
                (when-not (str/blank? output) (js/JSON.parse output))
                ;; If parsing succeeds, send to server
                (sente/request!
                 [:datasets/add-example {:module-id module-id
                                         :dataset-id dataset-id
                                         :snapshot-name snapshot-name
                                         :input input
                                         :output output}]
                 10000
                 (fn [reply]
                   (set-submitting false)
                   (if (:success reply)
                     (do (state/dispatch [:modal/hide]) (on-success))
                     (set-error-msg (or (:error reply) "An unknown server error occurred.")))))
                (catch js/Error e
                  (set-submitting false)
                  (set-error-msg (str "Invalid JSON: " (.-message e))))))]
      ($ :div
         ($ :div.space-y-4
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Input (JSON)")
               ($ :textarea {:className "w-full h-48 p-3 border rounded-md font-mono text-sm resize-y"
                             :value input
                             :placeholder "Enter input as a valid JSON object..."
                             :onChange #(set-input (.. % -target -value))}))
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700 "Reference Output (JSON, Optional)")
               ($ :textarea {:className "w-full h-48 p-3 border rounded-md font-mono text-sm resize-y"
                             :value output
                             :placeholder "Enter reference output as valid JSON..."
                             :onChange #(set-output (.. % -target -value))})))

         (when error-msg
           ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
              ($ :p.text-sm.text-red-700.whitespace-pre-wrap error-msg)))

         ($ :div.mt-6.flex.justify-end.gap-3
            ($ :button.px-4.py-2.border.rounded-md.text-sm {:type "button" :onClick #(state/dispatch [:modal/hide])} "Cancel")
            (let [is-disabled? (or submitting? (str/blank? input))]
              ($ :button {:type "button"
                          :disabled is-disabled?
                          :onClick handle-add
                          :className (str "px-4 py-2 border rounded-md text-sm font-medium flex items-center "
                                          (if is-disabled? "bg-gray-300 text-gray-500 cursor-not-allowed"
                                              "bg-blue-600 text-white hover:bg-blue-700 cursor-pointer"))}
                 (when submitting? ($ common/spinner {:size :medium}))
                 "Add Example")))))))

(defui CreateSnapshotForm [{:keys [module-id dataset-id from-snapshot-name on-success]}]
  (let [[to-name set-to-name] (uix/use-state "")
        [submitting? set-submitting] (uix/use-state false)
        [error-msg set-error-msg] (uix/use-state nil)

        handle-create (fn []
                        (set-submitting true)
                        (set-error-msg nil)
                        (sente/request!
                         [:datasets/create-snapshot {:module-id module-id
                                                     :dataset-id dataset-id
                                                     :from-snapshot-name from-snapshot-name
                                                     :to-snapshot-name to-name}]
                         10000
                         (fn [reply]
                           (set-submitting false)
                           (if (:success reply)
                             (do (state/dispatch [:modal/hide])
                                 (on-success))
                             (set-error-msg (:error reply))))))]
    ($ :div
       ($ :div.space-y-4
          ($ :div
             ($ :label.block.text-sm.font-medium.text-gray-700 "Source Snapshot")
             ($ :p.mt-1.text-sm.text-gray-500.bg-gray-100.p-2.rounded-md
                (if (str/blank? from-snapshot-name) "Latest (Working Copy)" from-snapshot-name)))
          ($ :div
             ($ :label.block.text-sm.font-medium.text-gray-700 "New Snapshot Name *")
             ($ :input {:className "w-full p-3 border rounded-md"
                        :type "text" :value to-name :required true
                        :onChange #(set-to-name (.. % -target -value))})))
       (when error-msg
         ($ :div.mt-4.p-3.bg-red-50.border.border-red-200.rounded-md
            ($ :p.text-sm.text-red-700.whitespace-pre-wrap error-msg)))
       ($ :div.mt-6.flex.justify-end.gap-3
          ($ :button.px-4.py-2.border.rounded-md {:type "button" :onClick #(state/dispatch [:modal/hide])} "Cancel")
          (let [is-disabled? (or submitting? (str/blank? to-name))]
            ($ :button {:type "button" :disabled is-disabled? :onClick handle-create
                        :className (str "px-4 py-2 rounded-md flex items-center cursor-pointer "
                                        (if is-disabled? "bg-gray-300 cursor-not-allowed" "bg-blue-600 text-white hover:bg-blue-700"))}
               (when submitting? ($ common/spinner {:size :medium})) "Create"))))))

(defui DropdownRow [{:keys [label selected? on-select delete-button action? icon]}]
  ($ :button
     {:onClick on-select
      :className (str "group flex items-center justify-between w-full px-4 py-2 text-sm hover:bg-gray-100 cursor-pointer "
                      (cond
                        action? "text-blue-600 hover:bg-blue-50"
                        selected? "text-blue-600 bg-blue-50"
                        :else "text-gray-700"))}
     ($ :div.flex.items-center.justify-between.w-full
        ($ :div.flex.items-center
           (when icon icon)
           ($ :span.truncate {:className (when icon "ml-3")} label))
        ($ :div.flex.items-center.space-x-2
           (when selected? ($ :span "✓"))
           delete-button))))

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

(defui ExamplesList [{:keys [examples]}]
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
                          ($ :button.inline-flex.items-center.justify-center.w-8.h-8.rounded-full.text-gray-400.hover:text-gray-600.hover:bg-gray-100.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500
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
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-gray-100.hover:text-gray-900
                                     {:onClick (fn []
                                                 (set-open-dropdown nil)
                                                 ;; TODO: Implement edit functionality
                                                 (println "Edit example:" example-id))}
                                     ($ PencilIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                     "Edit")

                                  ;; Delete option
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-gray-100.hover:text-gray-900
                                     {:onClick (fn []
                                                 (set-open-dropdown nil)
                                                 ;; TODO: Implement delete functionality
                                                 (println "Delete example:" example-id))}
                                     ($ TrashIcon {:className "mr-3 h-4 w-4 text-gray-400 group-hover:text-gray-500"})
                                     "Delete")

                                  ;; Try with evaluator option
                                  ($ :button.group.flex.items-center.w-full.px-4.py-2.text-sm.text-gray-700.hover:bg-gray-100.hover:text-gray-900
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
             {:onClick #(state/dispatch [:modal/show :create-dataset
                                         {:title "Create New Dataset"
                                          :component ($ CreateDatasetForm {:module-id module-id-raw
                                                                           :on-success refetch})}])}
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
                           :onClick #(state/dispatch [:modal/show :edit-dataset
                                                      {:title (str "Edit Dataset: " (:name dataset))
                                                       :component ($ EditDatasetForm {:module-id module-id-raw
                                                                                      :dataset-id (:dataset-id dataset)
                                                                                      :initial-name (:name dataset)
                                                                                      :initial-description (:description dataset)
                                                                                      :on-success refetch})}])}
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

        ;; State for selected snapshot and search
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
                                                  (assoc example-data :example-id uuid))
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
            ($ :div.bg-white.border-b.border-gray-200.px-6.py-4
               ($ :div.flex.items-center.justify-between
                  ;; Left side - Title and info
                  ($ :div.flex.items-center.space-x-4
                     ($ :h1.text-2xl.font-bold.text-gray-900 (:name dataset))
                     ;; Info button
                     ;; Info button with conditional chevron
                     ;; Details button with conditional chevron
                     ($ :button.inline-flex.items-center.px-3.py-1.text-sm.text-gray-600.hover:text-gray-800.rounded-md.hover:bg-gray-100.cursor-pointer
                        {:onClick #(set-show-info (not show-info?))
                         :title (if show-info? "Hide Dataset Information" "Show Dataset Information")}
                        ($ :span.mr-1 "Details")
                        (if show-info?
                          ($ ChevronUpIcon {:className "h-4 w-4"})
                          ($ ChevronDownIcon {:className "h-4 w-4"}))))

                  ;; Right side - Controls
                  ($ :div.flex.items-center.space-x-4
                     ;; Snapshot select
                     ;; SNAPSHOT MANAGER
                     ($ SnapshotManager {:module-id module-id
                                         :dataset-id dataset-id
                                         :selected-snapshot selected-snapshot-name
                                         :set-selected-snapshot set-selected-snapshot-name})

                     ;; Search bar

;; Add Example button
                     ($ :button.inline-flex.items-center.px-3.py-2.text-sm.font-medium.rounded-md.text-white.bg-blue-600.hover:bg-blue-700.cursor-pointer
                        {:onClick #(state/dispatch [:modal/show :add-example
                                                    {:title (str "Add Example to '" (if (str/blank? selected-snapshot-name) "Latest" selected-snapshot-name) "'")
                                                     :component ($ AddExampleForm {:module-id module-id
                                                                                   :dataset-id dataset-id
                                                                                   :snapshot-name selected-snapshot-name
                                                                                   :on-success examples-refetch})}])} ;; Use examples-refetch
                        ($ PlusIcon {:className "h-4 w-4 mr-2"})
                        "Add Example"))))

            ;; Info Panel (collapsible)
            (when show-info?
              ($ :div.bg-blue-50.border-b.border-blue-200.px-6.py-4
                 ($ :div.space-y-4
                    ;; Description
                    (when (:description dataset)
                      ($ :div
                         ($ :h3.text-sm.font-medium.text-blue-900 "Description")
                         ($ :p.text-sm.text-blue-700.mt-1 (:description dataset))))

                    ;; Schemas
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

            ;; Examples Section (main content)
            ($ :div.flex-1.overflow-hidden
               ($ :div.h-full.flex.flex-col
                  ;; Examples content
                  ($ :div.flex-1.overflow-hidden
                     (cond
                       examples-loading? ($ :div.flex.items-center.justify-center.h-full ;; Use examples-loading?
                                            ($ :div "Loading examples..."))
                       examples-error ($ :div.flex.items-center.justify-center.h-full ;; Use examples-error
                                         ($ :div.text-red-500 "Error loading examples."))
                       (empty? examples) ($ :div.flex.items-center.justify-center.h-full
                                            ($ :div.text-center.text-gray-500
                                               ($ :p "No examples yet.")
                                               ($ :p.text-sm.mt-1 "Click 'Add Example' to get started.")))
                       :else ($ :div.h-full.overflow-auto
                                ($ ExamplesList {:examples examples})))))))
         :else ($ :div.p-6 "No data available.")))))

;; =============================================================================
;; EXPORTS
;; =============================================================================

(def index datasets-index)
(def detail dataset-detail)