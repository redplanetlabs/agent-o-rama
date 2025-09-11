(ns com.rpl.agent-o-rama.ui.experiments.forms
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.datasets.snapshot-selector :as snapshot-selector]
   [clojure.string :as str]
   ["@heroicons/react/24/outline" :refer [PlusIcon TrashIcon ChevronDownIcon]]))

;; =============================================================================
;; SINGLE-STEP EXPERIMENT FORM
;; =============================================================================

;; =============================================================================
;; REUSABLE SUB-COMPONENTS FOR THE FORM
;; =============================================================================

(defui AgentSelectorDropdown [{:keys [module-id selected-agent on-select-agent disabled?]}]
  (let [[dropdown-open? set-dropdown-open] (uix/use-state false)
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:module-agents module-id]
          :sente-event [:agents/get-for-module {:module-id module-id}]
          :enabled? (boolean module-id)})
        agents (or data [])
        handle-select (fn [agent-name]
                        (set-dropdown-open false)
                        (on-select-agent agent-name))]

    (uix/use-effect
     (fn []
       (let [handle-click (fn [e] (when dropdown-open? (set-dropdown-open false)))]
         (.addEventListener js/document "click" handle-click)
         #(.removeEventListener js/document "click" handle-click)))
     [dropdown-open?])

    ($ :div.relative.inline-block.text-left
       ($ :button.inline-flex.items-center.justify-between.w-full.px-3.py-2.text-sm.bg-white.border.border-gray-300.rounded-md.shadow-sm.hover:bg-gray-50.disabled:bg-gray-100.cursor-pointer
          {:type "button"
           :onClick (fn [e] (.stopPropagation e) (set-dropdown-open (not dropdown-open?)))
           :disabled (or loading? disabled?)}
          ($ :span.truncate (if loading? "Loading agents..." (or selected-agent "Select an agent")))
          ($ ChevronDownIcon {:className "ml-2 h-4 w-4 text-gray-400"}))

       (when dropdown-open?
         ($ :div.origin-top-right.absolute.right-0.mt-1.w-full.rounded-md.shadow-lg.bg-white.ring-1.ring-black.ring-opacity-5.z-50
            {:onClick #(.stopPropagation %)}
            ($ :div.py-1.max-h-60.overflow-y-auto
               (if (seq agents)
                 (for [agent agents
                       :let [decoded-name (common/url-decode (:agent-name agent))]]
                   ($ common/DropdownRow {:key decoded-name
                                          :label decoded-name
                                          :selected? (= selected-agent decoded-name)
                                          :on-select #(handle-select decoded-name)}))
                 ($ :div.px-4.py-2.text-sm.text-gray-500 "No agents found in this module."))))))))

;; =============================================================================
;; MAIN EXPERIMENT FORM COMPONENTS
;; ============================================================================= 

(defui TargetEditor [{:keys [form-id index]}]
  (let [path [:spec :targets index]
        {:keys [module-id] :as form} (forms/use-form form-id)
        target-spec-type-field (forms/use-form-field form-id (conj path :target-spec :type))
        agent-name-field (forms/use-form-field form-id (conj path :target-spec :agent-name))
        node-name-field (forms/use-form-field form-id (conj path :target-spec :node))
        input-mappings (or (get-in form (conj path :input->args)) [])
        is-comparative? (= :comparative (get-in form [:spec :type]))]

    ($ :div.p-4.bg-gray-50.border.rounded-lg
       ($ :h4.text-md.font-semibold.mb-3 (str "Target " (inc index)))
       ($ :div.flex.items-center.gap-4.mb-4
          ($ :label.text-sm.font-medium "Target Type:")
          ($ :select.p-1.border.border-gray-300.rounded-md
             {:value (or (:value target-spec-type-field) :agent)
              :on-change #(state/dispatch [:form/set-experiment-target-type form-id index (keyword (.. % -target -value))])}
             ($ :option {:value "agent"} "Agent")
             ($ :option {:value "node"} "Node")))

       ($ :div.mb-4
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-1 "Agent Name")
          ($ AgentSelectorDropdown
             {:module-id module-id
              :selected-agent (:value agent-name-field)
              :on-select-agent (:on-change agent-name-field)}))

       (when (= (:value target-spec-type-field) :node)
         ($ :div.mt-4
            ($ forms/form-field
               {:label "Node Name" :required? true
                :value (:value node-name-field)
                :on-change (:on-change node-name-field)
                :error (:error node-name-field)})))

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

(defui CreateExperimentForm [{:keys [form-id]}]
  (let [{:keys [module-id dataset-id]} (state/use-sub [:route :path-params])
        ;; --- DELETED ---
        ;; The query for snapshot names is no longer needed here.
        ;; The SnapshotManager component will handle it.

        ;; Basic info fields
        name-field (forms/use-form-field form-id :name)
        description-field (forms/use-form-field form-id :description)

        ;; Data selection fields
        snapshot-field (forms/use-form-field form-id :snapshot)
        selector-type-field (forms/use-form-field form-id [:selector :type])
        selector-tag-field (forms/use-form-field form-id [:selector :tag])

        ;; Target config fields
        form (forms/use-form form-id)
        spec-type-field (forms/use-form-field form-id [:spec :type])
        targets (or (get-in form [:spec :targets]) [])]

    ($ forms/form
       ;; Basic Information Section
       ($ :div.mb
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Basic Information")
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
              :rows 3}))

       ;; Data Selection Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Data Selection")
          ($ :div.mb-4
             ($ :label.block.text-sm.font-medium.text-gray-700.mb-1 "Snapshot")
             ;; --- REPLACED ---
             ;; Replace the old <select> with the new component
             ($ snapshot-selector/SnapshotManager
                {:module-id module-id
                 :dataset-id dataset-id
                 :selected-snapshot (:value snapshot-field)
                 :on-select-snapshot (:on-change snapshot-field)
                 :read-only? true})) ;; Snapshots are always editable for experiments

          ($ :div
             ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Examples to run on")
             ($ :div.space-y-2
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
                         :placeholder "e.g., hard-case"}))))))

       ;; Target Configuration Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Target Configuration")
          ($ :div.mb-4
             ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Experiment Type")
             ($ :div.space-y-2
                ($ :div.flex.items-center
                   ($ :input {:type "radio" :id "regular-exp" :name "exp-type"
                              :checked (or (= (:value spec-type-field) :regular) (nil? (:value spec-type-field)))
                              :on-change #(state/dispatch [:form/set-experiment-target-type form-id 0 :regular])})
                   ($ :label.ml-3 {:htmlFor "regular-exp"} "Regular (Single Target)"))
                ($ :div.flex.items-center
                   ($ :input {:type "radio" :id "comp-exp" :name "exp-type"
                              :checked (= (:value spec-type-field) :comparative)
                              :on-change #(state/dispatch [:form/set-experiment-target-type form-id 0 :comparative])})
                   ($ :label.ml-3 {:htmlFor "comp-exp"} "Comparative (A/B Test Multiple Targets)"))))

          ($ :div.space-y-4
             (let [num-targets (if (or (= (:value spec-type-field) :regular) (nil? (:value spec-type-field)))
                                 1
                                 (count targets))]
               (for [i (range num-targets)
                     :let [is-comparative? (= (:value spec-type-field) :comparative)]]
                 ($ :div.relative.pt-6 {:key i}
                    (when (and is-comparative? (> i 0))
                      ($ :div.border-t.my-4))
                    ($ TargetEditor {:form-id form-id :index i})
                    (when (and is-comparative? (> num-targets 1))
                      ($ :button.absolute.top-0.right-0.p-1.text-red-500.hover:text-red-700
                         {:type "button"
                          :title "Remove Target"
                          :onClick (fn []
                                     (let [new-targets (vec (remove #(= % (get targets i)) targets))]
                                       (state/dispatch [:form/update-field form-id [:spec :targets] new-targets])))}
                         ($ TrashIcon {:className "h-4 w-4"})))))))

          (when (= (:value spec-type-field) :comparative)
            ($ :button.mt-4.flex.items-center.gap-2.text-sm.text-blue-600.hover:underline
               {:type "button"
                :onClick (fn [] (state/dispatch [:form/update-field form-id [:spec :targets] (conj targets {})]))}
               ($ PlusIcon {:className "h-4 w-4"})
               "Add Another Target")))

       ;; Execution Settings Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Execution Settings")
          ($ :div.grid.grid-cols-2.gap-4
             ($ forms/form-field
                {:label "Number of Repetitions"
                 :type :number
                 :value (or (get form :num-repetitions) 1)
                 :on-change #(state/dispatch [:form/update-field form-id :num-repetitions (js/parseInt (.. % -target -value))])
                 :placeholder "1"})
             ($ forms/form-field
                {:label "Concurrency Level"
                 :type :number
                 :value (or (get form :concurrency) 1)
                 :on-change #(state/dispatch [:form/update-field form-id :concurrency (js/parseInt (.. % -target -value))])
                 :placeholder "1"}))))))

;; =============================================================================
;; FORM REGISTRATION
;; =============================================================================
(forms/reg-form
 :create-experiment
 {:steps [:main]

  :main
  {:initial-fields (fn [props]
                     (merge {:name ""
                             :description ""
                             :snapshot ""
                             :selector {:type :all :tag ""}
                             :spec {:type :regular
                                    :targets [{:target-spec {:type :agent}
                                               :input->args []}]}
                             :num-repetitions 1
                             :concurrency 1}
                            props))
   :validators {:name [forms/required]}
   :ui (fn [{:keys [form-id]}] ($ CreateExperimentForm {:form-id form-id}))
   :modal-props {:title "Create New Experiment"
                 :submit-text "Run Experiment"}}

  :on-submit (fn [db form-state]
               (let [{:keys [form-id module-id dataset-id name description snapshot selector spec num-repetitions concurrency]} form-state
                   ;; Helper to clean up the spec for the backend
                     clean-spec (fn [spec]
                                  (-> spec
                                    ;; Convert spec type to the expected record name
                                      (update :type #(case % :regular "RegularExperiment" :comparative "ComparativeExperiment" %))
                                      (update :targets
                                              (fn [targets]
                                                (mapv (fn [target]
                                                        (-> target
                                                            (update-in [:target-spec :type] #(case % :agent "AgentTarget" :node "NodeTarget" %))
                                                          ;; Remove :node key if type is agent
                                                            (cond-> (= (get-in target [:target-spec :type]) "AgentTarget")
                                                              (update :target-spec dissoc :node))))
                                                      targets)))))]
                 (sente/request!
                  [:experiments/start {:module-id module-id, :dataset-id dataset-id, :form-data (assoc form-state :spec (clean-spec spec))}]
                  15000
                  (fn [reply]
                  ;; This would be the place to handle success/failure of starting the experiment
                    (println "Start experiment reply:" reply)
                    (state/dispatch [:modal/hide])
                    (state/dispatch [:form/clear form-id])))))})