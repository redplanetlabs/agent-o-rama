(ns com.rpl.agent-o-rama.ui.experiments.forms
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.state :as state]
   [clojure.string :as str]
   ["@heroicons/react/24/outline" :refer [PlusIcon TrashIcon]]))

;; =============================================================================
;; WIZARD STEP COMPONENTS (Each is now a simple, self-contained form)
;; =============================================================================

(defui BasicInfoStep [{:keys [form-id]}]
  (let [name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)]
    ($ forms/form
       ($ forms/form-field
          {:label "Experiment Name"
           :value (:value name-field)
           :on-change (:on-change name-field)
           :error (:error name-field)
           :required? true
           :placeholder "e.g., Test new prompt for summary agent"})
       ($ forms/form-field
          {:label "Description (Optional)"
           :type :textarea
           :value (:value description-field)
           :on-change (:on-change description-field)
           :error (:error description-field)
           :rows 3}))))

(defui DataSelectionStep [{:keys [form-id]}]
  (let [{:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        {:keys [data]} (queries/use-sente-query
                        {:query-key [:snapshot-names module-id dataset-id]
                         :sente-event [:datasets/get-snapshot-names {:module-id module-id :dataset-id dataset-id}]})
        snapshot-names (or (sort data) [])

        snapshot-field (forms/use-form-field form-id :snapshot)
        selector-type-field (forms/use-form-field form-id [:selector :type])
        selector-tag-field (forms/use-form-field form-id [:selector :tag])]

    ($ forms/form
       ($ :div
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-1 "Snapshot")
          ($ :select.w-full.p-2.border.border-gray-300.rounded-md
             {:value (or (:value snapshot-field) "")
              :on-change #((:on-change snapshot-field) (.. % -target -value))}
             ($ :option {:value ""} "Latest (Working Copy)")
             (for [name snapshot-names]
               ($ :option {:key name :value name} name))))

       ($ :div.mt-4
          ($ :label.block.text-sm.font-medium.text-gray-700 "Examples to run on")
          ($ :div.mt-2.space-y-2
             ($ :div.flex.items-center
                ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                   {:type "radio" :id "all-examples" :name "selector-type"
                    :checked (= (:value selector-type-field) :all)
                    :on-change #((:on-change selector-type-field) :all)})
                ($ :label.ml-3.block.text-sm.text-gray-700 {:htmlFor "all-examples"}
                   "All examples in snapshot"))
             ($ :div.flex.items-center
                ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                   {:type "radio" :id "tag-examples" :name "selector-type"
                    :checked (= (:value selector-type-field) :tag)
                    :on-change #((:on-change selector-type-field) :tag)})
                ($ :label.ml-3.block.text-sm.text-gray-700 {:htmlFor "tag-examples"}
                   "Only examples with tag:"))
             (when (= (:value selector-type-field) :tag)
               ($ :div.pl-8
                  ($ forms/form-field
                     {:value (:value selector-tag-field)
                      :on-change (:on-change selector-tag-field)
                      :error (:error selector-tag-field)
                      :placeholder "e.g., hard-case"}))))))))

(defui TargetEditor [{:keys [form-id index]}]
  (let [path [:spec :targets index]
        form (forms/use-form form-id)
        target-spec-type-field (forms/use-form-field form-id (conj path :target-spec :type))
        agent-name-field (forms/use-form-field form-id (conj path :target-spec :agent-name))
        node-name-field (forms/use-form-field form-id (conj path :target-spec :node))
        input-mappings (or (get-in form (conj path :input->args)) [])]

    ($ :div.p-4.bg-gray-50.border.rounded-lg
       ($ :h4.text-md.font-semibold.mb-3 (str "Target " (inc index)))
       ($ :div.flex.items-center.gap-4.mb-4
          ($ :label.text-sm.font-medium "Target Type:")
          ($ :select.p-1.border.border-gray-300.rounded-md
             {:value (or (:value target-spec-type-field) :agent)
              :on-change #((:on-change target-spec-type-field) (keyword (.. % -target -value)))}
             ($ :option {:value "agent"} "Agent")
             ($ :option {:value "node"} "Node")))

       ($ forms/form-field
          {:label "Agent Name" :required? true
           :value (:value agent-name-field) :on-change (:on-change agent-name-field)})
       (when (= (:value target-spec-type-field) :node)
         ($ forms/form-field
            {:label "Node Name" :required? true
             :value (:value node-name-field) :on-change (:on-change node-name-field)}))

       ($ :div.mt-4
          ($ :label.block.text-sm.font-medium.text-gray-700 "Input Mappings")
          ($ :p.text-xs.text-gray-500.mb-2 "Map dataset input fields to agent/node arguments using JSONPath.")
          (if (empty? input-mappings)
            ($ :div.text-xs.text-gray-500.italic.py-2 "No mappings yet. Add one to provide arguments.")
            ($ :div.space-y-2
               (for [[i mapping] (map-indexed vector input-mappings)]
                 ($ :div.flex.items-center.gap-2 {:key i}
                    ($ :input.flex-1.p-1.border.border-gray-300.rounded-md.font-mono.text-sm
                       {:value mapping
                        :on-change (fn [e] (state/dispatch [:form/update-field form-id (conj path :input->args i) (.. e -target -value)]))})
                    ($ :button.p-1.text-red-500.hover:text-red-700
                       {:type "button"
                        :onClick (fn [] (state/dispatch [:form/update-field form-id (conj path :input->args) (vec (remove (fn [m] (= m mapping)) input-mappings))]))}
                       ($ TrashIcon {:className "h-4 w-4"}))))))
          ($ :button.mt-2.text-sm.text-blue-600.hover:underline
             {:type "button"
              :onClick (fn [] (state/dispatch [:form/update-field form-id (conj path :input->args) (conj input-mappings "\"$\"")]))}
             "Add Mapping")))))

(defui TargetConfigStep [{:keys [form-id]}]
  (let [form (forms/use-form form-id)
        spec-type-field (forms/use-form-field form-id [:spec :type])
        targets (or (get-in form [:spec :targets]) [])]
    ($ forms/form
       ($ :div.mb-4
          ($ :label.block.text-sm.font-medium.text-gray-700 "Experiment Type")
          ($ :div.mt-2.space-y-2
             ($ :div.flex.items-center
                ($ :input {:type "radio" :id "regular-exp" :name "exp-type"
                           :checked (= (:value spec-type-field) :regular)
                           :on-change #((:on-change spec-type-field) :regular)})
                ($ :label.ml-3 {:htmlFor "regular-exp"} "Regular (Single Target)"))
             ($ :div.flex.items-center
                ($ :input {:type "radio" :id "comp-exp" :name "exp-type"
                           :checked (= (:value spec-type-field) :comparative)
                           :on-change #((:on-change spec-type-field) :comparative)})
                ($ :label.ml-3 {:htmlFor "comp-exp"} "Comparative (A/B Test Multiple Targets)"))))
       ($ :div.space-y-4
          (let [num-targets (if (= (:value spec-type-field) :regular) 1 (count targets))]
            (for [i (range num-targets)]
              ($ TargetEditor {:key i :form-id form-id :index i}))))

       (when (= (:value spec-type-field) :comparative)
         ($ :button.mt-4.flex.items-center.gap-2.text-sm.text-blue-600.hover:underline
            {:type "button"
             :onClick (fn [] (state/dispatch [:form/update-field form-id [:spec :targets] (conj targets {})]))}
            ($ PlusIcon {:className "h-4 w-4"})
            "Add Another Target")))))

(defui EvaluatorSelectionStep [{:keys [form-id]}]
  ($ forms/form
     ($ :div.p-4.text-center.text-gray-500 "UI for selecting evaluators coming soon...")))

(defui SettingsAndReviewStep [{:keys [form-id]}]
  ($ forms/form
     ($ :div.p-4.text-center.text-gray-500 "UI for final settings and review coming soon...")))

;; =============================================================================
;; FORM REGISTRATION
;; =============================================================================
(forms/reg-form
 :create-experiment
 {:steps [:basic-info :data-selection :target-config :evaluator-selection :settings-review]

  :basic-info
  {:initial-fields (fn [props] (merge {:name "", :description ""} props))
   :validators {:name [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ BasicInfoStep {:form-id form-id}))
   :modal-props {:title "New Experiment (1/5): Basic Information"}}

  :data-selection
  {:initial-fields (fn [current-state]
                     (merge {:snapshot "" :selector {:type :all :tag ""}}
                            current-state))
   :validators {}
   :ui (fn [{:keys [form-id]}] ($ DataSelectionStep {:form-id form-id}))
   :modal-props {:title "New Experiment (2/5): Data Selection"}}

  :target-config
  {:initial-fields (fn [current-state]
                     (merge {:spec {:type :regular
                                    :targets [{:target-spec {:type :agent}
                                               :input->args []}]}}
                            current-state))
   :validators {}
   :ui (fn [{:keys [form-id]}] ($ TargetConfigStep {:form-id form-id}))
   :modal-props {:title "New Experiment (3/5): Target Configuration"}}

  :evaluator-selection
  {:initial-fields (fn [current-state] (merge {:evaluators []} current-state))
   :validators {}
   :ui (fn [{:keys [form-id]}] ($ EvaluatorSelectionStep {:form-id form-id}))
   :modal-props {:title "New Experiment (4/5): Select Evaluators"}}

  :settings-review
  {:initial-fields (fn [current-state] (merge {:num-repetitions 1 :concurrency 1} current-state))
   :validators {}
   :ui (fn [{:keys [form-id]}] ($ SettingsAndReviewStep {:form-id form-id}))
   :modal-props {:title "New Experiment (5/5): Settings & Review"
                 :submit-text "Run Experiment"}}

  :on-submit
  (fn [_db form-state]
    (println "Wizard submitted! Final state:" (clj->js form-state))
    ;; TODO: Construct and send the :experiments/start Sente event here
    )})