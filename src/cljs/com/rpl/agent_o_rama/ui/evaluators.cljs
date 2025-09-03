(ns com.rpl.agent-o-rama.ui.evaluators
  (:require
   [uix.core :as uix :refer [defui $]]
   ["@heroicons/react/24/outline" :refer [PlusIcon BeakerIcon TrashIcon EllipsisVerticalIcon]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.queries :as queries]
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
      ($ :div.space-y-4
         ($ :p.text-sm.text-gray-600.mb-4
            "Select an evaluator builder to configure:")

         ($ :div.grid.gap-4.max-h-96.overflow-y-auto
            (into []
                  (for [[builder-name builder-spec] data]
                    (let [type (:type builder-spec)
                          description (:description builder-spec "No description available")]
                      ($ :div.border.rounded-lg.p-4.cursor-pointer.hover:bg-gray-50.transition-colors
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
                {:className (str "ml-2 h-4 w-4 transform transition-transform "
                                 (if show-advanced? "rotate-180" ""))
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
                   :placeholder "e.g., $.expected.answer"}))))

       #_($ forms/form-error {:error error}))))

;; =============================================================================
;; MODAL WORKFLOW
;; =============================================================================

(defui CreateEvaluatorModal [{:keys [module-id on-success]}]
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
                                                                     :builder-name (:name builder)
                                                                     :on-success on-success}]}])
                                   ;; Update modal to show form with submit button
                                   (state/dispatch [:modal/show :create-evaluator
                                                    {:title (str "Create " (get-in builder [:spec :options :description] "Evaluator"))
                                                     :form-id :create-evaluator
                                                     :submit-text "Create Evaluator"
                                                     :component ($ CreateEvaluatorModal
                                                                   {:module-id module-id
                                                                    :on-success on-success})}])))
                               [module-id on-success])]

    (case step
      :select-builder
      ($ SelectBuilderStep
         {:module-id module-id
          :on-select handle-builder-select})

      :configure
      ($ CreateEvaluatorForm
         {:form-id :create-evaluator
          :selected-builder selected-builder}))))

(defn show-create-evaluator-modal! [module-id on-success]
  (state/dispatch [:modal/show :create-evaluator
                   {:title "Select Evaluator Builder"
                    :component ($ CreateEvaluatorModal
                                  {:module-id module-id
                                   :on-success on-success})}]))

;; =============================================================================
;; EVALUATOR INSTANCES LIST
;; =============================================================================

(defui EvaluatorCard [{:keys [evaluator-name evaluator-spec on-delete]}]
  (let [type (:type evaluator-spec)
        description (:description evaluator-spec)
        params (get-in evaluator-spec [:options :params] {})]

    ($ :div.bg-white.border.rounded-lg.p-4.shadow-sm
       ($ :div.flex.justify-between.items-start.mb-3
          ($ :div
             ($ :h3.font-medium.text-gray-900.mb-1 evaluator-name)
             (when (seq params)
               ($ :p.text-sm.text-gray-500
                  (str (count params) " parameter" (when (> (count params) 1) "s")))))

          ($ :div.flex.items-center.gap-2
             ($ :span.inline-flex.px-2.py-1.text-xs.font-medium.rounded-full
                {:className (get-evaluator-type-badge-style type)}
                (get-evaluator-type-display type))

             ($ :div.relative
                ($ :button.p-1.text-gray-400.hover:text-gray-600
                   {:onClick #(println "Actions menu for" evaluator-name)} ; TODO: Implement dropdown
                   ($ EllipsisVerticalIcon {:className "h-5 w-5"})))))

       (when description
         ($ :p.text-sm.text-gray-600.mb-3 description))

       ;; Show parameters if any
       (when (seq params)
         ($ :div.mb-3
            ($ :h4.text-sm.font-medium.text-gray-700.mb-2 "Parameters:")
            ($ :div.space-y-1
               (into []
                     (for [[param-key param-spec] params]
                       ($ :div.text-xs.text-gray-600
                          {:key (str param-key)}
                          ($ :span.font-medium (name param-key))
                          (when-let [default (:default param-spec)]
                            ($ :span.text-gray-500 " (default: " default ")"))))))))

       ($ :div.flex.justify-end.gap-2
          ($ :button.text-sm.text-blue-600.hover:text-blue-800
             {:onClick #(println "Try evaluator" evaluator-name)} ; TODO: Implement try modal
             "Try...")

          ($ :button.text-sm.text-red-600.hover:text-red-800
             {:onClick #(on-delete evaluator-name)}
             "Delete")))))

;; =============================================================================
;; MAIN PAGE COMPONENT
;; =============================================================================

(defui index []
  (let [{:keys [module-id]} (state/use-sub [:route :path-params])
        {:keys [data loading? error refetch]}
        (queries/use-sente-query
         {:query-key [:evaluators module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
          :enabled? (boolean module-id)
          :refetch-interval-ms 5000})

        handle-delete (uix/use-callback
                       (fn [evaluator-name]
                         (when (js/confirm (str "Are you sure you want to delete evaluator '" evaluator-name "'?"))
                           (queries/send-sente-event!
                            [:evaluators/delete {:name evaluator-name}]
                            {:on-success #(refetch)})))
                       [refetch])]

    ($ :div.p-6
       ;; Header
       ($ :div.flex.justify-between.items-center.mb-6
          ($ :div.flex.items-center.gap-3
             ($ BeakerIcon {:className "h-8 w-8 text-indigo-600"})
             ($ :h1.text-2xl.font-bold.text-gray-900
                "Evaluators for "
                ($ :span.text-indigo-600 (common/url-decode module-id))))

          ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
             {:onClick #(show-create-evaluator-modal! module-id refetch)}
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

         (empty? data)
         ($ :div.text-center.py-12
            ($ BeakerIcon {:className "mx-auto h-12 w-12 text-gray-400 mb-4"})
            ($ :h3.text-lg.font-medium.text-gray-900.mb-2 "No evaluators yet")
            ($ :p.text-gray-500.mb-6 "Create your first evaluator to get started.")
            ($ :button.inline-flex.items-center.px-4.py-2.bg-blue-600.text-white.rounded-md.hover:bg-blue-700.transition-colors
               {:onClick #(show-create-evaluator-modal! module-id refetch)}
               ($ PlusIcon {:className "h-5 w-5 mr-2"})
               "Create Evaluator"))

         :else
         ($ :div.grid.gap-4.md:grid-cols-2.lg:grid-cols-3
            (into []
                  (for [[evaluator-name evaluator-spec] data]
                    ($ EvaluatorCard
                       {:key evaluator-name
                        :evaluator-name evaluator-name
                        :evaluator-spec evaluator-spec
                        :on-delete handle-delete}))))))))
