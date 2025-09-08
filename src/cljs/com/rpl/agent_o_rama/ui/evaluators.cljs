(ns com.rpl.agent-o-rama.ui.evaluators
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [PlusIcon BeakerIcon TrashIcon EllipsisVerticalIcon ChevronDownIcon XMarkIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [clojure.string :as str]))

;; =============================================================================
;; HELPER FUNCTIONS
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

(defui SelectBuilderStep [{:keys [module-id on-select]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-builders module-id]
          :sente-event [:evaluators/get-all-builders {:module-id module-id}]})]

    (cond
      loading?
      ($ :div.flex.justify-center.items-center.h-64
         ($ common/spinner {:size :large}))

      error
      ($ :div.text-red-500.text-center.py-8
         "Error loading evaluator builders: " error)

      (empty? data)
      ($ :div.text-gray-500.text-center.py-8
         "No evaluator builders available for this module.")

      :else
      ($ :div.p-6.space-y-4
         ($ :p.text-sm.text-gray-600.mb-4
            "Select an evaluator builder to configure:")

         ($ :div.grid.gap-4.max-h-96.overflow-y-auto
            (into []
                  (for [[builder-name builder-spec] data]
                    (let [type (:type builder-spec)
                          description (:description builder-spec "No description available")]
                      ($ :div.bg-white.rounded-lg.p-4.cursor-pointer.hover:bg-gray-50.hover:shadow-md.transition-all.duration-200.border.border-gray-100.shadow-sm
                         {:key builder-name
                          :onClick #(on-select {:name builder-name
                                                :spec builder-spec})}

                         ($ :div.flex.justify-between.items-start.mb-2
                            ($ :h3.font-medium.text-gray-900 builder-name)
                            ($ :span.inline-flex.px-2.py-1.text-xs.font-medium.rounded-full
                               {:className (get-evaluator-type-badge-style type)}
                               (get-evaluator-type-display type)))

                         ($ :p.text-sm.text-gray-600 description))))))))))

(defui CreateEvaluatorForm [{:keys [form-id selected-builder]}]
  (let [{:keys [error get-field set-field field-errors]} (forms/use-centralized-form form-id)
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)
        input-json-path-field (forms/use-form-field form-id :input-json-path)
        output-json-path-field (forms/use-form-field form-id :output-json-path)
        reference-output-json-path-field (forms/use-form-field form-id :reference-output-json-path)
        [show-advanced? set-show-advanced!] (uix/use-state false)

        builder-params (get-in selected-builder [:spec :options :params] {})]

    ($ forms/form
       ;; Static fields
       ($ forms/form-field
          {:label "Name"
           :value (:value name-field)
           :on-change (:on-change name-field)
           :error (:error name-field)
           :required? true
           :placeholder "e.g., my-llm-judge"})

       ($ forms/form-field
          {:label "Description"
           :type :textarea
           :value (:value description-field)
           :on-change (:on-change description-field)
           :error (:error description-field)
           :placeholder "Optional description of this evaluator instance"
           :rows 2})

       ;; Dynamic parameter fields
       (when (seq builder-params)
         ($ :div.mt-6.pt-4.border-t
            ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Builder Parameters")

            (into []
                  (for [[param-key param-spec] builder-params]
                    (let [field-path [:params param-key]
                          param-value (get-field field-path)
                          param-error (get field-errors field-path)
                          param-description (:description param-spec "")]
                      ($ forms/form-field
                         {:key (str param-key)
                          :label (str (name param-key))
                          :value param-value
                          :on-change #(set-field field-path %)
                          :error param-error
                          :placeholder param-description}))))))

       ;; Advanced options (collapsible)
       ($ :div.mt-6.pt-4.border-t
          ($ :button.flex.items-center.text-sm.font-medium.text-gray-700.hover:text-gray-900
             {:type "button"
              :onClick #(set-show-advanced! (not show-advanced?))}
             "Advanced Options"
             ($ :svg
                {:className (common/cn
                             "ml-2 h-4 w-4 transform transition-transform"
                             {"rotate-180" show-advanced?})
                 :fill "none"
                 :viewBox "0 0 24 24"
                 :stroke "currentColor"}
                ($ :path
                   {:strokeLinecap "round"
                    :strokeLinejoin "round"
                    :strokeWidth 2
                    :d "M19 9l-7 7-7-7"})))

          (when show-advanced?
            ($ :div.mt-4.space-y-4
               ($ forms/form-field
                  {:label "Input JSON Path"
                   :value (:value input-json-path-field)
                   :on-change (:on-change input-json-path-field)
                   :error (:error input-json-path-field)
                   :placeholder "e.g., $.input.text"})

               ($ forms/form-field
                  {:label "Output JSON Path"
                   :value (:value output-json-path-field)
                   :on-change (:on-change output-json-path-field)
                   :error (:error output-json-path-field)
                   :placeholder "e.g., $.output.result"})

               ($ forms/form-field
                  {:label "Reference Output JSON Path"
                   :value (:value reference-output-json-path-field)
                   :on-change (:on-change reference-output-json-path-field)
                   :error (:error reference-output-json-path-field)
                   :placeholder "e.g., $.expected.answer"})))))))

;; =============================================================================
;; MODAL WORKFLOW
;; =============================================================================

(defui CreateEvaluatorModal [{:keys [module-id]}]
  (let [[step set-step!] (uix/use-state :select-builder)
        [selected-builder set-selected-builder!] (uix/use-state nil)

        handle-builder-select (uix/use-callback
                               (fn [builder]
                                 (set-selected-builder! builder)
                                 (set-step! :configure)
                                 ;; Initialize form when we move to configure step
                                 (let [params (get-in builder [:spec :options :params] {})
                                       initial-params (reduce-kv
                                                       (fn [acc k v]
                                                         (assoc-in acc [:params k] (:default v "")))
                                                       {}
                                                       params)
                                       initial-fields (merge {:name "" :description ""
                                                              :input-json-path ""
                                                              :output-json-path ""
                                                              :reference-output-json-path ""}
                                                             initial-params)]
                                   (state/dispatch [:form/init :create-evaluator
                                                    {:fields initial-fields
                                                     :validators {:name [forms/required]}
                                                     :submit-event [:evaluators/create
                                                                    {:module-id module-id
                                                                     :builder-name (:name builder)}]}])
                                   ;; Update modal to show form with submit button
                                   (state/dispatch [:modal/show :create-evaluator
                                                    {:title (str "Create " (get-in builder [:spec :options :description] "Evaluator"))
                                                     :form-id :create-evaluator
                                                     :submit-text "Create Evaluator"
                                                     :component ($ CreateEvaluatorModal
                                                                   {:module-id module-id})}])))
                               [module-id])]

    (case step
      :select-builder
      ($ SelectBuilderStep
         {:module-id module-id
          :on-select handle-builder-select})

      :configure
      ($ CreateEvaluatorForm
         {:form-id :create-evaluator
          :selected-builder selected-builder}))))

(defn show-create-evaluator-modal! [module-id]
  (state/dispatch [:modal/show :create-evaluator
                   {:title "Select Evaluator Builder"
                    :component ($ CreateEvaluatorModal
                                  {:module-id module-id})}]))

;; =============================================================================
;; EVALUATOR INSTANCES LIST
;; =============================================================================

(defui EvaluatorCard [{:keys [evaluator-name instance-spec on-delete]}]
  (let [type (:type instance-spec)
        description (:description instance-spec)
        builder-name (:builder-name instance-spec)
        params (:builder-params instance-spec {})]

    ($ :div.bg-white.border.rounded-lg.p-4.shadow-sm
       ($ :div.flex.justify-between.items-start.mb-3
          ($ :div
             ($ :h3.font-medium.text-gray-900.mb-1 evaluator-name)
             ($ :p.text-xs.text-gray-500 "Builder: " ($ :code.font-mono builder-name)))

          ($ :div.flex.items-center.gap-2
             ($ :span.inline-flex.px-2.py-1.text-xs.font-medium.rounded-full
                {:className (get-evaluator-type-badge-style type)}
                (get-evaluator-type-display type))))

       (when description
         ($ :p.text-sm.text-gray-600.mb-3 description))

       ;; Show parameters if any
       (when (seq params)
         ($ :div.mb-3
            ($ :h4.text-sm.font-medium.text-gray-700.mb-2 "Parameters:")
            ($ :div.space-y-1
               (into []
                     (for [[param-key param-value] (sort-by key params)]
                       ($ :div.text-xs.text-gray-600.flex.justify-between.items-center
                          {:key (str param-key)}
                          ($ :span.font-medium (name param-key))
                          ($ :code.font-mono.bg-gray-100.px-2.py-0.5.rounded (str param-value))))))))

       ($ :div.flex.justify-end.gap-2
          ($ :button.text-sm.text-red-600.hover:text-red-800.cursor-pointer
             {:onClick #(on-delete evaluator-name)}
             "Delete")))))

;; =============================================================================
;; MAIN PAGE COMPONENT
;; =============================================================================

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
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
       ;; Header
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex.items-center.gap-3
             ($ BeakerIcon {:className "h-8 w-8 text-indigo-600"}))

          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick #(show-create-evaluator-modal! module-id)}
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
               {:onClick #(show-create-evaluator-modal! module-id)}
               ($ PlusIcon {:className "h-5 w-5 mr-2"})
               "Create Evaluator"))

         :else
         ($ :div {:className (:container common/table-classes)}
            ($ :table {:className (:table common/table-classes)}
               ($ :thead {:className (:thead common/table-classes)}
                  ($ :tr
                     ($ :th {:className (:th common/table-classes)} "Name")
                     ($ :th {:className (:th common/table-classes)} "Builder")
                     ($ :th {:className (:th common/table-classes)} "Type")
                     ($ :th {:className (:th common/table-classes)} "Parameters")
                     ($ :th {:className (:th common/table-classes)} "Actions")))
               ($ :tbody
                  (into []
                        (for [spec evaluators]
                          (let [evaluator-name (:name spec)
                                type (:type spec)
                                builder-name (:builder-name spec)
                                params (:builder-params spec {})]
                            ($ :tr {:key evaluator-name :className "hover:bg-gray-50"}
                               ($ :td {:className (:td common/table-classes)} evaluator-name)
                               ($ :td {:className (:td common/table-classes)}
                                  ($ :code.font-mono.text-xs.text-gray-600 builder-name))
                               ($ :td {:className (:td common/table-classes)}
                                  ($ :span.inline-flex.px-2.py-0.5.rounded-full.text-xs.font-medium
                                     {:className (get-evaluator-type-badge-style type)}
                                     (get-evaluator-type-display type)))
                               ($ :td {:className (:td common/table-classes)}
                                  (if (seq params)
                                    ($ :div.text-xs.text-gray-600
                                       (into []
                                             (->> (sort-by key params)
                                                  (map (fn [[k v]]
                                                         ($ :span.mr-2 {:key (str k)}
                                                            ($ :span.font-medium (name k)) ": " (str v)))))))
                                    ($ :span.text-xs.text-gray-400.italic "—")))
                               ($ :td {:className (:td-right common/table-classes)}
                                  ($ :button.text-sm.text-red-600.hover:text-red-800.cursor-pointer
                                     {:onClick #(handle-delete evaluator-name)}
                                     "Delete")))))))))))))

(defui RunEvaluatorModal [{:keys [module-id dataset-id mode example selected-example-ids]}]
  (let [[selected-evaluator set-selected-evaluator] (uix/use-state nil)
        ;; Separate input state from result state
        [model-output-input set-model-output-input] (uix/use-state "") ; For :regular type user input
        [model-outputs-input set-model-outputs-input] (uix/use-state [{:id (random-uuid) :value ""}]) ; For :comparative type user input
        [evaluation-result set-evaluation-result] (uix/use-state nil) ; For evaluator results
        [error set-error] (uix/use-state nil)
        [loading? set-loading] (uix/use-state false)
        [dropdown-open? set-dropdown-open] (uix/use-state false)

        ;; Fetch all evaluator instances
        {:keys [data evaluators-loading? evaluators-error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
          :enabled? (boolean module-id)})

        ;; Filter evaluators based on the modal's mode (:single or :multi)
        evaluators (filter
                    (if (= mode :single)
                      #(#{:regular :comparative} (:type %))
                      #(= :summary (:type %)))
                    (or (:items data) []))

        evaluator-type (:type selected-evaluator)

        handle-run (fn []
                     (when selected-evaluator
                       (set-loading true)
                       (set-error nil)

                       (let [run-data (case evaluator-type
                                        :regular {:input (js/JSON.stringify (clj->js (:input example)))
                                                  :referenceOutput (js/JSON.stringify (clj->js (:reference-output example)))
                                                  :output model-output-input}
                                        :comparative {:input (js/JSON.stringify (clj->js (:input example)))
                                                      :referenceOutput (js/JSON.stringify (clj->js (:reference-output example)))
                                                      :outputs (mapv :value model-outputs-input)}
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
                              (set-error (:error reply))))))))]

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
       ;; 1. Evaluator Selection Dropdown
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Select Evaluator")
          (cond
            evaluators-loading? ($ :div.text-sm.text-gray-500 "Loading evaluators...")
            evaluators-error ($ :div.text-sm.text-red-600 "Error loading evaluators")
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

       ;; 2. Example Input (read-only) - only for single mode
       (when (= mode :single)
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Input")
            ($ :div.bg-gray-50.rounded-md.p-4.border
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                  (js/JSON.stringify (clj->js (:input example)) nil 2)))))

       ;; 3. Reference Output (read-only) - only for single mode
       (when (and (= mode :single) (:reference-output example))
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Reference Output")
            ($ :div.bg-gray-50.rounded-md.p-4.border
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono
                  (js/JSON.stringify (clj->js (:reference-output example)) nil 2)))))

       ;; 4. Dynamic UI based on selection and mode
       (when selected-evaluator
         (case evaluator-type
           :regular
           ($ :div
              ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Model Output (JSON)")
              ($ :textarea.w-full.p-3.border.border-gray-300.rounded-md.font-mono.text-sm
                 {:rows 3, :placeholder "{\"result\": \"...\"}", :value model-output-input, :onChange #(set-model-output-input (.. % -target -value))}))

           :comparative
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
              ($ :button.text-sm.text-blue-600.hover:underline {:onClick #(set-model-outputs-input (conj model-outputs-input {:id (random-uuid) :value ""}))} "Add another output"))

           :summary
           ($ :div.p-4.bg-blue-50.border.border-blue-200.rounded-md
              ($ :h4.text-sm.font-medium.text-blue-800
                 (str "This will run the summary evaluator '" (:name selected-evaluator) "' on "
                      (count selected-example-ids) " selected examples.")))

           nil))

       ;; 5. Run Button and Output
       ($ :div.flex.justify-center
          ($ :button.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.disabled:opacity-50.disabled:cursor-not-allowed.cursor-pointer
             {:onClick handle-run, :disabled (or (not selected-evaluator) loading?)}
             (if loading? "Running..." "Run Evaluator")))

       (when error ($ :div.p-4.bg-red-50.border.border-red-200.rounded-md ($ :p.text-sm.text-red-700 error)))
       (when evaluation-result
         ($ :div
            ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Evaluator Result")
            ($ :div.bg-green-50.rounded-md.p-4.border.border-green-200
               ($ :pre.text-sm.text-gray-900.whitespace-pre-wrap.font-mono (js/JSON.stringify (clj->js evaluation-result) nil 2))))))))
