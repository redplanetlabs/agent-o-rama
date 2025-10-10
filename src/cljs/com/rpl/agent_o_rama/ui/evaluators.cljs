(ns com.rpl.agent-o-rama.ui.evaluators
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [PlusIcon BeakerIcon TrashIcon EllipsisVerticalIcon ChevronDownIcon XMarkIcon InformationCircleIcon MagnifyingGlassIcon PlayIcon]]
   ["react" :refer [useState]]
   ["use-debounce" :refer [useDebounce]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [clojure.string :as str]))

;; =============================================================================
;; NEW: EVALUATOR DETAILS MODAL
;; =============================================================================

(defui DetailItem [{:keys [label children]}]
  ($ :div.py-3.sm:grid.sm:grid-cols-3.sm:gap-4.sm:px-0
     ($ :dt.text-sm.font-medium.leading-6.text-gray-900 label)
     ($ :dd.mt-1.text-sm.leading-6.text-gray-700.sm:col-span-2.sm:mt-0
        children)))

(defui EvaluatorDetailsModal [{:keys [spec]}]
  (let [{:keys [name type description builder-name builder-params
                input-json-path output-json-path reference-output-json-path]} spec
        ;; Get module-id from the route params to pass to the new event
        module-id (state/use-sub [:route :path-params :module-id])]
    ($ :div.p-6.text-sm
       ;; NEW: Try Evaluator button at the top
       ($ :div.flex.justify-end.mb-4
          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick (fn []
                         ;; Close the details modal first, then show try modal
                         (state/dispatch [:modal/hide])
                         (js/setTimeout
                          #(state/dispatch [:evaluators/show-try-modal {:evaluator spec :module-id module-id}])
                          100))}
             ($ PlayIcon {:className "h-5 w-5 mr-2"})
             "Try Evaluator"))

       ($ :dl.divide-y.divide-gray-100
          ($ DetailItem {:label "Name"} ($ :span.font-mono name))
          ($ DetailItem {:label "Description"} (if (str/blank? description) ($ :span.italic.text-gray-500 "No description") description))
          ($ DetailItem {:label "Builder"} ($ :code.font-mono.bg-gray-100.px-2.py-1.rounded builder-name))
          ($ DetailItem {:label "Type"}
             ($ :span.inline-flex.px-2.py-0.5.rounded-full.text-xs.font-medium
                {:className (get-evaluator-type-badge-style type)}
                (get-evaluator-type-display type)))

          (when (seq builder-params)
            ($ DetailItem {:label "Parameters"}
               ($ :div.bg-gray-100.rounded-md.p-3.space-y-3
                  (for [[k v] (sort-by key builder-params)]
                    (let [v-str (str v)
                          has-newlines? (str/includes? v-str "\n")]
                      ($ :div {:key (str k)
                               :className (if has-newlines? "flex flex-col gap-1" "flex gap-4 items-center")}
                         ($ :span.text-gray-600.font-medium.font-mono.text-xs (clojure.core/name k))
                         ($ :pre.text-gray-800.bg-white.px-2.py-1.rounded.font-mono.text-xs.whitespace-pre-wrap v-str)))))))

          ($ DetailItem {:label ($ :div.flex.items-center.gap-2 "Input JSONPath" ($ JsonPathTooltip))}
             (if (str/blank? input-json-path)
               ($ :span.italic.text-gray-500 "Not configured")
               ($ :code.font-mono.bg-gray-100.px-2.py-1.rounded input-json-path)))
          ($ DetailItem {:label ($ :div.flex.items-center.gap-2 "Output JSONPath" ($ JsonPathTooltip))}
             (if (str/blank? output-json-path)
               ($ :span.italic.text-gray-500 "Not configured")
               ($ :code.font-mono.bg-gray-100.px-2.py-1.rounded output-json-path)))
          ($ DetailItem {:label ($ :div.flex.items-center.gap-2 "Reference Output JSONPath" ($ JsonPathTooltip))}
             (if (str/blank? reference-output-json-path)
               ($ :span.italic.text-gray-500 "Not configured")
               ($ :code.font-mono.bg-gray-100.px-2.py-1.rounded reference-output-json-path)))))))

(defn show-evaluator-details-modal! [spec]
  (state/dispatch [:modal/show :evaluator-details
                   {:title (str "Evaluator Details: " (:name spec))
                    :component ($ EvaluatorDetailsModal {:spec spec})}]))

;; =============================================================================
;; JSONPATH TOOLTIP COMPONENT
;; =============================================================================

(defui InfoTooltip [{:keys [content]}]
  (let [[open? set-open!] (uix/use-state false)
        [timeout-id set-timeout-id!] (uix/use-state nil)
        hovering-ref (uix/use-ref false)

        clear-close! (fn []
                       (when timeout-id
                         (js/clearTimeout timeout-id)
                         (set-timeout-id! nil)))

        schedule-close (fn schedule-close []
                         (clear-close!)
                         (let [new-timeout (js/setTimeout (fn []
                                                            (if (.-current hovering-ref)
                                                              (schedule-close)
                                                              (do (set-open! false)
                                                                  (set-timeout-id! nil))))
                                                          100)]
                           (set-timeout-id! new-timeout)))]

    ($ :div.relative.inline-flex.items-center
       ($ InformationCircleIcon {:className "h-4 w-4 text-gray-400 hover:text-blue-500 cursor-help"
                                 :onClick (fn [] (set-open! (not open?)))
                                 :tabIndex 0
                                 :onMouseEnter (fn []
                                                 (set! (.-current hovering-ref) true)
                                                 (clear-close!)
                                                 (set-open! true))
                                 :onMouseLeave (fn []
                                                 (set! (.-current hovering-ref) false)
                                                 (schedule-close))})
       (when open?
         ($ :div.absolute.bottom-full.mb-2.w-64.bg-gray-800.text-white.text-xs.rounded.py-2.px-3.shadow-lg.z-50
            {:onMouseEnter (fn []
                             (set! (.-current hovering-ref) true)
                             (clear-close!))
             :onMouseLeave (fn []
                             (set! (.-current hovering-ref) false)
                             (schedule-close))}
            content)))))

(defui JsonPathTooltip []
  ($ InfoTooltip {:content ($ :div
                              "A JSONPath expression to extract a value from the JSON object."
                              ($ :br)
                              ($ :a.text-blue-300.hover:underline.cursor-pointer
                                 {:onClick #(js/window.open "https://en.wikipedia.org/wiki/JSONPath" "_blank")}
                                 "Learn more on Wikipedia."))}))

;; =============================================================================

(defn get-evaluator-type-badge-style [type]
  (case type
    :regular "bg-green-100 text-green-800"
    :comparative "bg-blue-100 text-blue-800"
    :summary "bg-purple-100 text-purple-800"
    "bg-gray-100 text-gray-800"))

(defn get-evaluator-type-display [type]
  (case type
    :regular "Regular"
    :comparative "Comparative"
    :summary "Summary"
    (str type)))

;; =============================================================================
;; CREATE EVALUATOR MODAL COMPONENTS
;; =============================================================================

(defui SelectBuilderStep [{:keys [form-id]}]
  (let [{:keys [set-field! next-step!]} (forms/use-form form-id)
        {:keys [module-id]} (state/use-sub [:forms form-id])
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-builders module-id]
          :sente-event [:evaluators/get-all-builders {:module-id module-id}]})]
    ;; The on-select logic is now integrated here
    (let [handle-select (fn [builder]
                          (set-field! [:selected-builder] builder)
                          (next-step!))]
      (cond
        loading? ($ :div.flex.justify-center.items-center.h-64 ($ common/spinner {:size :large}))
        error ($ :div.text-red-500.text-center.py-8 "Error loading builders: " error)
        (empty? data) ($ :div.text-gray-500.text-center.py-8 "No evaluator builders available.")
        :else
        ($ :div.p-6.space-y-4
           ($ :p.text-sm.text-gray-600.mb-4 "Select an evaluator builder to configure:")
           ($ :div.grid.gap-4.max-h-96.overflow-y-auto
              (for [[builder-name builder-spec] data]
                (let [type (:type builder-spec)
                      description (:description builder-spec "No description available")]
                  ($ :div.bg-white.rounded-lg.p-4.cursor-pointer.hover:bg-gray-50.hover:shadow-md.transition-all.duration-200.border.border-gray-100.shadow-sm
                     {:key builder-name, :onClick #(handle-select {:name builder-name, :spec builder-spec})}
                     ($ :div.flex.justify-between.items-start.mb-2
                        ($ :h3.font-medium.text-gray-900 (str builder-name))
                        ($ :span.inline-flex.px-2.py-1.text-xs.font-medium.rounded-full
                           {:className (get-evaluator-type-badge-style type)}
                           (get-evaluator-type-display type)))
                     ($ :p.text-sm.text-gray-600 (str description)))))))))))

(defui CreateEvaluatorForm [{:keys [form-id]}]
  (let [form-state (forms/use-form form-id)
        {:keys [set-field! field-errors selected-builder params input-json-path output-json-path reference-output-json-path]} form-state
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)
        builder-params (get-in selected-builder [:spec :options :params] {})
        builder-options (get-in selected-builder [:spec :options] {})
        show-input-path? (get builder-options :input-path? true)
        show-output-path? (get builder-options :output-path? true)
        show-ref-output-path? (get builder-options :reference-output-path? true)]

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
                            :rows 2})
       (when (seq builder-params)
         ($ :div.mt-6.pt-4.border-t
            ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Builder Parameters")
            (for [[param-key param-spec] builder-params]
              (let [param-description (:description param-spec)
                    param-value (get params param-key)
                    ;; Convert value to string for display
                    value-str (cond
                                (string? param-value) param-value
                                (nil? param-value) ""
                                :else (str param-value))
                    ;; Check if value contains newlines to determine field type
                    has-newlines? (str/includes? value-str "\n")]
                ($ forms/form-field
                   {:key (str param-key)
                    :label ($ :div.flex.items-center.gap-2
                              (str (name param-key))
                              (when param-description
                                ($ InfoTooltip {:content param-description})))
                    :type (if has-newlines? :textarea :text)
                    :rows (when has-newlines? 6)
                    :value value-str
                    :on-change #(set-field! [:params param-key] %)
                    :error (get-in field-errors [:params param-key])})))))
       (when (or show-input-path? show-output-path? show-ref-output-path?)
         ($ :div.mt-6.pt-4.border-t
            ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "JSONPath Configuration")
            ($ :div.space-y-4
               (when show-input-path?
                 ($ forms/form-field {:label ($ :div.flex.items-center.gap-2 "Input JSON Path" ($ JsonPathTooltip))
                                      :value input-json-path
                                      :on-change #(set-field! [:input-json-path] %)
                                      :error (:input-json-path field-errors)}))
               (when show-output-path?
                 ($ forms/form-field {:label ($ :div.flex.items-center.gap-2 "Output JSON Path" ($ JsonPathTooltip))
                                      :value output-json-path
                                      :on-change #(set-field! [:output-json-path] %)
                                      :error (:output-json-path field-errors)}))
               (when show-ref-output-path?
                 ($ forms/form-field {:label ($ :div.flex.items-center.gap-2 "Reference Output JSON Path" ($ JsonPathTooltip))
                                      :value reference-output-json-path
                                      :on-change #(set-field! [:reference-output-json-path] %)
                                      :error (:reference-output-json-path field-errors)}))))))))

(forms/reg-form
 :create-evaluator
 {:steps [:select-builder :configure]

  :select-builder
  {:initial-fields (fn [props] (merge {:selected-builder nil} props))
   :validators {}
   :ui (fn [{:keys [form-id]}] ($ SelectBuilderStep {:form-id form-id}))
   :modal-props {:title "Select Evaluator Builder"}}

  :configure
  {:initial-fields (fn [current-form-state]
                     ;; Extract defaults from selected builder params
                     (let [builder-params (get-in current-form-state [:selected-builder :spec :options :params] {})
                           default-params (into {} (for [[k v] builder-params]
                                                     [k (:default v)]))]
;; Merge current-form-state FIRST to preserve step 1 data,
;; then override with new defaults to reset step 2 fields
                       (merge current-form-state
                              {:name ""
                               :description ""
                               :input-json-path "$"
                               :output-json-path "$"
                               :reference-output-json-path "$"
                               :params default-params})))
   :validators {:name [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ CreateEvaluatorForm {:form-id form-id}))
   :modal-props (fn [form-state]
                  {:title (str "Create Evaluator: " (get-in form-state [:selected-builder :name]))})}

  :on-submit
  (fn [db form-state] ; Updated to use the new flattened signature!
    (let [{:keys [form-id module-id name description input-json-path output-json-path reference-output-json-path params selected-builder]} form-state]
      (sente/request!
       [:evaluators/create {:module-id module-id
                            :builder-name (:name selected-builder)
                            :name name
                            :description description
                            :params params
                            :input-json-path input-json-path
                            :output-json-path output-json-path
                            :reference-output-json-path reference-output-json-path}]
       15000
       (fn [reply]
         (println "Evaluator create reply:" reply)
         (if (:success reply)
           (do
             (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
             (state/dispatch [:modal/hide])
             (let [decoded-module-id (when module-id (common/url-decode module-id))]
               (state/dispatch [:query/invalidate
                                {:query-key-pattern
                                 [:evaluator-instances decoded-module-id]}]))
             (state/dispatch [:form/clear form-id]))
           (do
             (println "Setting error and stopping spinner in form:" (:error reply) form-id)
             (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
             (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)])))))))})

(defui RunEvaluatorModal [{:keys [module-id dataset-id mode example selected-example-ids pre-selected-evaluator]}]
  (let [;; NEW: If pre-selected, use it. Otherwise, state is nil.
        [selected-evaluator set-selected-evaluator] (uix/use-state pre-selected-evaluator)
        ;; NEW: Initialize textareas from the example if it exists, otherwise empty strings.
        [input-str set-input-str] (uix/use-state #(common/pp-json (or (:input example) "")))
        [ref-output-str set-ref-output-str] (uix/use-state #(common/pp-json (or (:reference-output example) "")))
        [output-str set-output-str] (uix/use-state "") ; For :regular type

        [model-outputs-input set-model-outputs-input] (uix/use-state [{:id (random-uuid) :value ""}]) ; For :comparative type user input
        [evaluation-result set-evaluation-result] (uix/use-state nil) ; For evaluator results
        [error set-error] (uix/use-state nil)
        [loading? set-loading] (uix/use-state false)
        [dropdown-open? set-dropdown-open] (uix/use-state false)

        ;; Fetch all evaluator instances
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
          :enabled? (boolean module-id)})

        ;; Fetch evaluator builders to get their options for conditional rendering
        builders-query (queries/use-sente-query
                        {:query-key [:evaluator-builders module-id]
                         :sente-event [:evaluators/get-all-builders {:module-id module-id}]
                         :enabled? (boolean module-id)})

        builders-by-name (:data builders-query)

        ;; Filter evaluators based on the modal's mode (:single or :multi)
        evaluators (filter
                    (if (= mode :single)
                      #(#{:regular :comparative} (:type %))
                      #(= :summary (:type %)))
                    (or (:items data) []))

        evaluator-type (:type selected-evaluator)

        ;; Determine visibility flags based on selected evaluator's builder options
        builder-options (when selected-evaluator
                          (get-in builders-by-name [(:builder-name selected-evaluator) :options] {}))
        show-input-path? (get builder-options :input-path? true)
        show-output-path? (get builder-options :output-path? true)
        show-ref-output-path? (get builder-options :reference-output-path? true)

        handle-run (fn []
                     (when selected-evaluator
                       (set-loading true)
                       (set-error nil)

                       (try
                         (let [parsed-input (when (and show-input-path? (not (str/blank? input-str)))
                                              (-> input-str js/JSON.parse js->clj))
                               parsed-ref-output (when (and show-ref-output-path? (not (str/blank? ref-output-str)))
                                                   (-> ref-output-str js/JSON.parse js->clj))

                               run-data (case evaluator-type
                                          :regular (cond-> {}
                                                     show-input-path? (assoc :input parsed-input)
                                                     show-ref-output-path? (assoc :referenceOutput parsed-ref-output)
                                                     show-output-path? (assoc :output (when-not (str/blank? output-str)
                                                                                        (-> output-str js/JSON.parse js->clj))))
                                          :comparative (cond-> {}
                                                         show-input-path? (assoc :input parsed-input)
                                                         show-ref-output-path? (assoc :referenceOutput parsed-ref-output)
                                                         show-output-path? (assoc :outputs (mapv #(-> % :value js/JSON.parse js->clj) model-outputs-input)))
                                          :summary {:dataset-id dataset-id
                                                    :example-ids selected-example-ids})]
                           (sente/request!
                            [:evaluators/run {:module-id module-id
                                              :name (:name selected-evaluator)
                                              :type evaluator-type
                                              :run-data run-data}]
                            60000 ; Generous timeout
                            (fn [reply]
                              (set-loading false)
                              (if (:success reply)
                                (set-evaluation-result (:data reply))
                                (set-error (:error reply))))))
                         (catch js/Error e
                           (set-loading false)
                           (set-error (str "Invalid JSON in one of the fields: " (.-message e)))))))]

    ;; Clear evaluation result when evaluator changes
    (uix/use-effect
     (fn []
       (set-evaluation-result nil))
     [selected-evaluator])

    ;; Close dropdown when clicking outside
    (uix/use-effect
     (fn []
       (let [handle-click (fn [e]
                            (when dropdown-open?
                              (set-dropdown-open false)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [dropdown-open?])

    ($ :div.p-6.space-y-6
       ;; 1. Evaluator Selection Dropdown (or display if pre-selected)
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Select Evaluator")
          (cond
            ;; NEW: If pre-selected, just display the name
            pre-selected-evaluator
            ($ :div.p-3.bg-gray-100.rounded-md.border.border-gray-300
               ($ :span.font-medium (:name pre-selected-evaluator))
               ($ :span.ml-2.inline-flex.items-center.px-2.py-0.5.rounded-full.text-xs.font-medium
                  {:className (get-evaluator-type-badge-style (:type pre-selected-evaluator))}
                  (get-evaluator-type-display (:type pre-selected-evaluator))))

            loading? ($ :div.text-sm.text-gray-500 "Loading evaluators...")
            error ($ :div.text-sm.text-red-600 "Error loading evaluators")
            (empty? evaluators) ($ :div.text-sm.text-gray-500 (if (= mode :single) "No regular or comparative evaluators available." "No summary evaluators available."))
            :else
            ($ :div.relative
               ($ :button.inline-flex.items-center.justify-between.w-full.px-3.py-2.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50
                  {:onClick #(set-dropdown-open (not dropdown-open?))}
                  ($ :span.truncate (or (:name selected-evaluator) "Choose an evaluator..."))
                  ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))
               (when dropdown-open?
                 ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
                    ($ :div.py-1
                       (for [evaluator evaluators]
                         ($ common/DropdownRow {:key (:name evaluator)
                                                :label (:name evaluator)
                                                :selected? (= (:name selected-evaluator) (:name evaluator))
                                                :on-select #(do (set-selected-evaluator evaluator) (set-dropdown-open false))
                                                :extra-content ($ :div.px-4.pb-2.text-xs.text-gray-500
                                                                  ($ :span.inline-flex.items-center.px-2.py-0.5.rounded-full.text-xs.font-medium
                                                                     {:className (get-evaluator-type-badge-style (:type evaluator))}
                                                                     (get-evaluator-type-display (:type evaluator))))}))))))))

       ;; 2. Example Input (conditionally rendered) - only for single mode
       (when (and (= mode :single) show-input-path?)
         ($ forms/form-field
            {:label "Input (JSON)"
             :type :textarea
             :rows 4
             :value input-str
             :on-change set-input-str}))

       ;; 3. Reference Output (conditionally rendered) - only for single mode
       (when (and (= mode :single) show-ref-output-path?)
         ($ forms/form-field
            {:label "Reference Output (JSON)"
             :type :textarea
             :rows 4
             :value ref-output-str
             :on-change set-ref-output-str}))

       ;; 4. Dynamic UI based on selection and mode
       (when selected-evaluator
         (case evaluator-type
           :regular
           (when show-output-path?
             ($ forms/form-field
                {:label "Model Output (JSON)"
                 :type :textarea
                 :rows 4
                 :value output-str
                 :on-change set-output-str
                 :placeholder "{\"result\": \"...\"}"}))

           :comparative
           (when show-output-path?
             ($ :div
                ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Model Outputs (One valid JSON per line)")
                (doall (for [output-item model-outputs-input]
                         ($ :div.flex.items-center.gap-2.mb-2 {:key (:id output-item)}
                            ($ :textarea.flex-1.p-2.border.border-gray-300.rounded-md.font-mono.text-xs
                               {:rows 1
                                :value (:value output-item)
                                :onChange #(set-model-outputs-input
                                            (mapv (fn [item]
                                                    (if (= (:id item) (:id output-item))
                                                      (assoc item :value (.. % -target -value))
                                                      item))
                                                  model-outputs-input))})
                            ($ :button.text-red-500.hover:text-red-700
                               {:onClick #(set-model-outputs-input
                                           (filterv (fn [item] (not= (:id item) (:id output-item))) model-outputs-input))}
                               ($ XMarkIcon {:className "h-4 w-4"})))))
                ($ :button.text-sm.text-blue-600.hover:underline {:onClick #(set-model-outputs-input (conj model-outputs-input {:id (random-uuid) :value ""}))} "Add another output")))

           :summary
           (if (zero? (count selected-example-ids))
             ($ :div.p-4.bg-yellow-50.border.border-yellow-200.rounded-md
                ($ :h4.text-sm.font-medium.text-yellow-800
                   "To test a summary evaluator, select examples from a dataset and click the \"Try Summary Evaluator\" button from the selection menu."))
             ($ :div.p-4.bg-blue-50.border.border-blue-200.rounded-md
                ($ :h4.text-sm.font-medium.text-blue-800
                   (str "This will run the summary evaluator '" (:name selected-evaluator) "' on "
                        (count selected-example-ids) " selected examples."))))

           nil))

       ;; 5. Run Button and Output
       ($ :div.flex.justify-center
          ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick handle-run
              :disabled (or (not selected-evaluator)
                            loading?
                            (and (= evaluator-type :summary)
                                 (zero? (count selected-example-ids))))}
             (if loading? "Running..." "Run Evaluator")))

       (when error ($ :div.p-4.bg-red-50.border.border-red-200.rounded-md ($ :p.text-sm.text-red-700 error)))
       (when evaluation-result
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Evaluator Result")
            ($ :div.bg-green-50.rounded-md.p-4.border.border-green-200
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono (js/JSON.stringify (clj->js evaluation-result) nil 2))))))))

;; Event handler to show the Try Evaluator modal with a pre-selected evaluator
(state/reg-event
 :evaluators/show-try-modal
 (fn [_db {:keys [evaluator module-id]}]
   ;; Dispatch the generic :modal/show event with RunEvaluatorModal
   ;; In :single mode with a nil example, which signals empty textareas
   (state/dispatch [:modal/show :run-evaluator
                    {:title (str "Try Evaluator: " (:name evaluator))
                     :component ($ RunEvaluatorModal
                                   {:module-id module-id
                                    :mode :single ;; Testing a single run
                                    :pre-selected-evaluator evaluator ;; Pre-select the evaluator
                                    :example nil ;; nil example means "start from scratch"
                                    :dataset-id nil
                                    :selected-example-ids nil})}])))

 ;; =============================================================================
;; MAIN PAGE COMPONENT
;; =============================================================================

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        ;; Add state for search term and debounce it
        [search-term set-search-term] (useState "")
        [debounced-search-term] (useDebounce search-term 300)

        ;; Add state for type filter
        [selected-type set-selected-type] (useState "all")

        ;; Update the query to use the debounced search term and type filter
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances
                      module-id
                      debounced-search-term
                      selected-type]
          :sente-event [:evaluators/get-all-instances
                        {:module-id module-id
                         :filters (cond-> {}
                                    (not (str/blank? debounced-search-term))
                                    (assoc :search-string debounced-search-term)

                                    (not= selected-type "all")
                                    (assoc :types #{(keyword selected-type)}))}]
          :enabled? (boolean module-id)
          :refetch-interval-ms 5000})

       ;; Destructure the response from the query
        evaluators (get data :items [])

        handle-delete (uix/use-callback
                       (fn [evaluator-name]
                         (when (js/confirm (str "Are you sure you want to delete evaluator '" evaluator-name "'?"))
                           (sente/request! [:evaluators/delete {:name evaluator-name
                                                                :module-id module-id}] 15000
                                           (fn [reply]
                                             (if (:success reply)
                                               (refetch)
                                               (js/alert (str "Failed to delete evaluator: " (:error reply))))))))
                       [module-id refetch])]

    ($ :div.p-6
      ;; Header with search input and type filter
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex.items-center.gap-3
             ($ BeakerIcon {:className "h-8 w-8 text-indigo-600"})
             ;; Search input field
             ($ :div.relative.ml-4
                ($ :div.pointer-events-none.absolute.inset-y-0.left-0.flex.items-center.pl-3
                   ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"}))
                ($ :input
                   {:type "text"
                    :value search-term
                    :onChange #(set-search-term (.. % -target -value))
                    :className "block w-full rounded-md border-0 py-1.5 pl-10 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm sm:leading-6"
                    :placeholder "Search evaluators..."}))

             ;; Type filter dropdown
             ($ :div.m-4
                ($ :select
                   {:value selected-type
                    :onChange #(set-selected-type (.. % -target -value))
                    :className "block rounded-md border-0 py-1.5 pl-3 pr-10 text-gray-900 ring-1 ring-inset ring-gray-300 focus:ring-2 focus:ring-indigo-600 sm:text-sm sm:leading-6"}
                   ($ :option {:value "all"} "All Types")
                   ($ :option {:value "regular"} "Regular")
                   ($ :option {:value "comparative"} "Comparative")
                   ($ :option {:value "summary"} "Summary"))))

          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors.cursor-pointer
             {:onClick #(state/dispatch [:modal/show-form :create-evaluator {:module-id module-id}])}
             ($ PlusIcon {:className "h-5 w-5 mr-2"})
             "Create Evaluator"))

      ;; Content
       (cond
         loading?
         ($ :div.flex.justify-center.items-center.h-64
            ($ common/spinner {:size :large}))

         error
         ($ :div.text-red-500.text-center.py-8
            "Error loading evaluators: " error)

         (empty? evaluators)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400 mb-4"})
            ($ :h3.text-lg.font-medium.text-gray-900.mb-2 "No evaluators yet")
            ($ :p.text-gray-500.mb-6 "Create your first evaluator to get started.")
            ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
               {:onClick #(state/dispatch [:modal/show-form :create-evaluator {:module-id module-id}])}
               ($ PlusIcon {:className "h-5 w-5 mr-2"})
               "Create Evaluator"))

         :else
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Description")
                     ($ :th {:className (:th common/table-classes)} "Builder")
                     ($ :th {:className (:th common/table-classes)} "Type")
                     ($ :th {:className (:th common/table-classes)} "Actions")))
               ($ :tbody
                  (into []
                        (for [spec evaluators]
                          (let [evaluator-name (:name spec)
                                type (:type spec)
                                description (:description spec)
                                builder-name (:builder-name spec)]
                            ($ :tr {:key evaluator-name
                                    :className "hover:bg-gray-50 cursor-pointer"
                                    :onClick #(show-evaluator-details-modal! spec)}
                               ($ :td {:className (:td common/table-classes)} evaluator-name)
                               ($ :td {:className (common/cn (:td common/table-classes) "max-w-sm truncate")}
                                  (if (str/blank? description)
                                    ($ :span.italic.text-gray-400 "—")
                                    description))
                               ($ :td {:className (:td common/table-classes)}
                                  ($ :code.font-mono.text-xs.text-gray-600 builder-name))
                               ($ :td {:className (:td common/table-classes)}
                                  ($ :span.inline-flex.px-2.py-0.5.rounded-full.text-xs.font-medium
                                     {:className (get-evaluator-type-badge-style type)}
                                     (get-evaluator-type-display type)))
                               ($ :td {:className (:td-right common/table-classes)}
                                  ($ :button.inline-flex.items-center.px-2.py-1.text-xs.text-gray-500.hover:text-red-700.cursor-pointer
                                     {:onClick (fn [e]
                                                 (.stopPropagation e) ; Prevent row click
                                                 (handle-delete evaluator-name))}
                                     ($ TrashIcon {:className "h-4 w-4 mr-1"})
                                     "Delete")))))))))))))
