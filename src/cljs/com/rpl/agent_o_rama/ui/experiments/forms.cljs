(ns com.rpl.agent-o-rama.ui.experiments.forms
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.forms :as forms]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.sente :as sente]
   [com.rpl.agent-o-rama.ui.experiments.events]
   [com.rpl.agent-o-rama.ui.datasets.snapshot-selector :as snapshot-selector]
   [clojure.string :as str]
   [com.rpl.agent-o-rama.ui.evaluators :as evaluators]
   [reitit.frontend.easy :as rfe]
   ["@heroicons/react/24/outline" :refer [PlusIcon TrashIcon ChevronDownIcon XMarkIcon]]
   ["react-dom" :refer [createPortal]]))

 ;; =============================================================================
;; NEW: TRANSFORMATION FUNCTION
;; =============================================================================
(defn experiment-info->form-state
  "Transforms a backend :experiment-info map into the initial state
   for the :create-experiment form."
  [info]
  (let [spec (:spec info)
        is-regular? (contains? spec :target)
        targets (if is-regular? [(:target spec)] (:targets spec))
        normalize-mappings (fn [args]
                             (->> (or args [])
                                  (mapv (fn [a]
                                          (if (and (map? a) (contains? a :id) (contains? a :value))
                                            a
                                            {:id (random-uuid)
                                             :value (if (string? a) a (str a))})))))]
    {;; Add a custom title for the modal when re-running
     :title (str "Re-run Experiment: " (:name info))
     ;; Prepend name to indicate it's a copy
     :name (str "Copy of " (:name info))
     :description (get info :description "")
     :snapshot (get info :snapshot "")
     :selector (if-let [selector (:selector info)]
                 ;; Handle tag selector
                 (if (:tag selector)
                   {:type :tag, :tag (:tag selector)}
                   ;; Handle other selectors by defaulting to 'all'
                   {:type :all, :tag ""})
                 ;; Default to 'all' if selector is nil
                 {:type :all, :tag ""})
     :spec {:type (if is-regular? :regular :comparative)
            :targets (mapv
                      (fn [t]
                        (let [ts (:target-spec t)]
                          {:target-spec (if (:node ts)
                                          (assoc ts :type :node)
                                          (assoc ts :type :agent))
                           :input->args (normalize-mappings (:input->args t))}))
                      targets)}
     :evaluators (:evaluators info)
     :num-repetitions (:num-repetitions info)
     :concurrency (:concurrency info)}))

;; =============================================================================
;; SINGLE-STEP EXPERIMENT FORM
;; =============================================================================

;; =============================================================================
;; REUSABLE SUB-COMPONENTS FOR THE FORM
;; =============================================================================

(defui AgentSelectorDropdown [{:keys [module-id selected-agent on-select-agent disabled?]}]
  (println "AgentSelectorDropdown - selected-agent:" selected-agent "type:" (type selected-agent) "nil?:" (nil? selected-agent))
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
                       :let [decoded-name (common/url-decode (:agent-name agent))]
                       :when (not= decoded-name "_aor-evaluator")]
                   ($ common/DropdownRow {:key decoded-name
                                          :label decoded-name
                                          :selected? (= selected-agent decoded-name)
                                          :on-select #(handle-select decoded-name)}))
                 ($ :div.px-4.py-2.text-sm.text-gray-500 "No agents found in this module."))))))))

(defui EvaluatorSelector [{:keys [module-id selected-evaluators on-change]}]
  (let [[dropdown-open? set-dropdown-open] (uix/use-state false)
        trigger-ref (uix/use-ref nil)
        [position set-position] (uix/use-state nil)
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
          :enabled? (boolean module-id)})
        all-evaluators (or (:items data) [])
        selected-names (set (map :name selected-evaluators))
        available-evaluators (remove #(contains? selected-names (:name %)) all-evaluators)
        recalc-position (fn []
                          (when-let [el (.-current trigger-ref)]
                            (let [rect (.getBoundingClientRect el)
                                  top (+ (.-bottom rect) (.-scrollY js/window))
                                  left (+ (.-left rect) (.-scrollX js/window))
                                  width (.-width rect)]
                              (set-position {:top top :left left :width width}))))]

    (uix/use-effect
     (fn []
       (when dropdown-open?
         (recalc-position)
         (let [handle-click (fn [_] (set-dropdown-open false))
               handle-recalc (fn [_] (recalc-position))]
           (.addEventListener js/document "click" handle-click)
           (.addEventListener js/window "scroll" handle-recalc true)
           (.addEventListener js/window "resize" handle-recalc)
           #(do
              (.removeEventListener js/document "click" handle-click)
              (.removeEventListener js/window "scroll" handle-recalc true)
              (.removeEventListener js/window "resize" handle-recalc)))))
     [dropdown-open?])

    ($ :<>
       ($ :div

          ($ :div.flex.flex-wrap.gap-2.mb-2
             (if (seq selected-evaluators)
               (for [e selected-evaluators
                     :let [evaluator-info (first (filter #(= (:name %) (:name e)) all-evaluators))]]
                 ($ :div.inline-flex.items-center.gap-2.px-3.py-2.rounded-lg.text-xs.font-medium.bg-indigo-100.text-indigo-800.border.border-indigo-200
                    {:key (:name e)}
                    ($ :<>
                       ($ :div.flex.flex-col.gap-1
                          ($ :div.flex.items-center.gap-2
                             ($ :span.font-semibold (:name e))
                             (when evaluator-info
                               ($ :span.inline-flex.px-2.py-0.5.rounded-full.text-xs.font-medium
                                  {:className (evaluators/get-evaluator-type-badge-style (:type evaluator-info))}
                                  (evaluators/get-evaluator-type-display (:type evaluator-info)))))
                          (when (and evaluator-info (not (str/blank? (:description evaluator-info))))
                            ($ :span.text-indigo-600.max-w-xs.truncate (:description evaluator-info))))
                       ($ :button.p-1.rounded-full.hover:bg-indigo-200.transition-colors
                          {:onClick #(on-change (vec (remove (fn [sel] (= (:name sel) (:name e))) selected-evaluators)))}
                          ($ XMarkIcon {:className "h-3 w-3"})))))
               ($ :div.text-sm.text-gray-500.italic "No evaluators selected."))))
       ($ :div.relative
          ($ :button.inline-flex.items-center.gap-2.text-sm.text-blue-600.hover:underline
             {:type "button"
              :ref #(set! (.-current trigger-ref) %)
              :onClick (fn [e]
                         (.stopPropagation e)
                         (if dropdown-open?
                           (set-dropdown-open false)
                           (set-dropdown-open true)))}
             ($ PlusIcon {:className "h-4 w-4"})
             "Add Evaluator")

          (when (and dropdown-open? position)
            (createPortal
             ($ :div
                {:className "origin-top-left rounded-md shadow-lg bg-white ring-1 ring-black ring-opacity-5 z-50 w-80"
                 :style {:position "fixed"
                         :top (+ (:top position) 8)
                         :left (:left position)}}
                ($ :div.py-1.max-h-60.overflow-y-auto
                   (cond
                     loading? ($ :div.px-4.py-2.text-sm.text-gray-500 "Loading...")
                     error ($ :div.px-4.py-2.text-sm.text-red-500 "Error")
                     (empty? available-evaluators) ($ :div.px-4.py-2.text-sm.text-gray-500 "No more evaluators to add.")
                     :else (for [e available-evaluators]
                             ($ :div.px-3.py-2.hover:bg-gray-50.cursor-pointer.border-b.border-gray-100.last:border-b-0
                                {:key (:name e)
                                 :onClick #(do
                                             (on-change (conj selected-evaluators {:name (:name e) :remote? false}))
                                             (set-dropdown-open false))}
                                ($ :div.flex.flex-col.gap-1
                                   ($ :div.flex.items-center.justify-between
                                      ($ :span.font-medium.text-gray-900 (:name e))
                                      ($ :span.inline-flex.px-2.py-0.5.rounded-full.text-xs.font-medium
                                         {:className (evaluators/get-evaluator-type-badge-style (:type e))}
                                         (evaluators/get-evaluator-type-display (:type e))))
                                   (when-not (str/blank? (:description e))
                                     ($ :span.text-sm.text-gray-600.max-w-xs.truncate (:description e)))
                                   ($ :span.text-xs.text-gray-500.font-mono (:builder-name e))))))))
             (.-body js/document)))))))

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
             {:value (name (or (:value target-spec-type-field) :agent))
              :onChange #(state/dispatch [:form/set-experiment-target-type form-id index (keyword (.. % -target -value))])}
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
               (for [[i {:keys [id value]}] (map-indexed vector input-mappings)]
                 ($ :div.flex.items-center.gap-2 {:key id}
                    ($ :input.flex-1.p-1.border.border-gray-300.rounded-md.font-mono.text-sm
                       {:value value
                        :on-change (fn [e] (state/dispatch [:form/update-field form-id (conj path :input->args i :value) (.. e -target -value)]))})
                    ($ :button.p-1.text-red-500.hover:text-red-700
                       {:type "button"
                        :onClick (fn [] (state/dispatch [:form/update-field form-id (conj path :input->args) (vec (remove #(= (:id %) id) input-mappings))]))}
                       ($ TrashIcon {:className "h-4 w-4"}))))))
          ($ :button.mt-2.text-sm.text-blue-600.hover:underline
             {:type "button"
              :onClick (fn [] (state/dispatch [:form/update-field form-id (conj path :input->args) (conj input-mappings {:id (random-uuid) :value "$"})]))}
             "Add Mapping")))))

(defui CreateExperimentForm [{:keys [form-id]}]
  (let [{:keys [module-id dataset-id]} (state/use-sub [:route :path-params])

        ;; Basic info fields
        name-field (forms/use-form-field form-id :name)
        ;; Data selection fields
        snapshot-field (forms/use-form-field form-id :snapshot)
        selector-type-field (forms/use-form-field form-id [:selector :type])
        selector-tag-field (forms/use-form-field form-id [:selector :tag])

        ;; Get selected examples from global state
        selected-example-ids (or (state/use-sub [:ui :datasets :selected-examples dataset-id]) #{})
        selection-count (count selected-example-ids)

        ;; Target config fields
        form (forms/use-form form-id)
        spec-type (get-in form [:spec :type])
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
              :placeholder "e.g., Test new prompt for summary agent"}))

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
                         :placeholder "e.g., hard-case"})))
                ;; NEW: Add the "Selected examples" option
                ($ :div.flex.items-center
                   ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                      {:type "radio" :id "selected-examples" :name "selector-type"
                       :checked (= (:value selector-type-field) :example-ids)
                       :disabled (zero? selection-count) ;; Disable if nothing is selected
                       :on-change #((:on-change selector-type-field) :example-ids)})
                   ($ :label.ml-3.block.text-sm.text-gray-700
                      {:htmlFor "selected-examples"
                       :className (when (zero? selection-count) "text-gray-400 cursor-not-allowed")
                       :title (when (zero? selection-count) "Select examples from the list to enable this option.")}
                      (if (pos? selection-count)
                        (str "Only the " selection-count " selected examples")
                        "Only selected examples (none selected)"))))))

       ;; Target Configuration Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Target Configuration")

          ($ :div.space-y-4
             (let [num-targets (if (= spec-type :regular)
                                 1
                                 (count targets))]
               (for [i (range num-targets)
                     :let [is-comparative? (= spec-type :comparative)]]
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

          (when (= spec-type :comparative)
            ($ :button.mt-4.flex.items-center.gap-2.text-sm.text-blue-600.hover:underline
               {:type "button"
                :onClick (fn [] (state/dispatch [:form/update-field form-id [:spec :targets] (conj targets {:target-spec {:type :agent :agent-name nil} :input->args [{:id (random-uuid) :value "$"}]})]))}
               ($ PlusIcon {:className "h-4 w-4"})
               "Add Another Target")))

       ;; Evaluation Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Evaluation")
          (let [evaluators-field (forms/use-form-field form-id :evaluators)]
            ($ EvaluatorSelector
               {:module-id module-id
                :selected-evaluators (:value evaluators-field)
                :on-change (:on-change evaluators-field)})))

       ;; Execution Settings Section
       ($ :div.mb-8
          ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Execution Settings")
          ($ :div.grid.grid-cols-2.gap-4
             ($ forms/form-field
                {:label "Number of Repetitions"
                 :type :number
                 :value (or (get form :num-repetitions) 1)
                 :on-change #(state/dispatch [:form/update-field form-id :num-repetitions (js/parseInt %)])
                 :placeholder "1"})
             ($ forms/form-field
                {:label "Concurrency Level"
                 :type :number
                 :value (or (get form :concurrency) 1)
                 :on-change #(state/dispatch [:form/update-field form-id :concurrency (js/parseInt %)])
                 :placeholder "1"}))))))

;; =============================================================================
;; FORM REGISTRATION
;; =============================================================================
(forms/reg-form
 :create-experiment
 {:steps [:main]

  :main
  {:initial-fields
   (fn [props]
     (let [base {:name ""
                 :snapshot ""
                 :selector {:type :all :tag ""}
                 :spec {:type :regular
                        :targets [{:target-spec {:type :agent :agent-name nil}
                                   :input->args [{:id (random-uuid) :value "$"}]}]}
                 :evaluators []
                 :num-repetitions 1
                 :concurrency 1}
           merged (merge base props)
           merged-spec (merge (:spec base) (:spec props))]
       (assoc merged :spec merged-spec)))
   :validators
   {:name [forms/required]
    :evaluators [(fn [v] (when (empty? v) "At least one evaluator is required"))]}
   :ui (fn [{:keys [form-id]}] ($ CreateExperimentForm {:form-id form-id}))
      ;; NEW: Modal props is now a function to allow dynamic titles
   :modal-props (fn [props]
                  {:title (get props :title "Create New Experiment")
                   :submit-text "Run Experiment"})}

  :on-submit
  (fn [db form-state]
    (let [{:keys [form-id module-id dataset-id spec]} form-state
          spec-type (get spec :type)
          ;; Get selected IDs from the DB at submission time
          selected-ids (get-in db [:ui :datasets :selected-examples dataset-id])

          ;; Add selected IDs to the selector if that option was chosen
          form-with-selection (if (= (get-in form-state [:selector :type]) :example-ids)
                                (assoc-in form-state [:selector :example-ids] (vec selected-ids))
                                form-state)

          ;; Convert input->args back to simple strings for the backend
          cleaned-form-state (update-in form-with-selection [:spec :targets]
                                        (fn [targets]
                                          (mapv (fn [target]
                                                  (update target :input->args (fn [args] (mapv :value args))))
                                                targets)))]
      (sente/request!
       [:experiments/start
        {:module-id module-id
         :dataset-id dataset-id
         :form-data cleaned-form-state}]
       15000
       (fn [reply]
         (state/dispatch [:db/set-value [:forms form-id :submitting?] false])
         (if (:success reply)
           (do
             (state/dispatch [:modal/hide])
             (state/dispatch [:query/invalidate {:query-key-pattern [:experiments module-id dataset-id]}])
             (let [eid (get-in reply [:data :experiment-id])]
               (if (and eid (not= spec-type :comparative))
                 (rfe/push-state :module/dataset-detail.experiment-detail
                                 {:module-id module-id :dataset-id dataset-id :experiment-id eid})
                 (if (= spec-type :comparative)
                   (rfe/push-state :module/dataset-detail.comparative-experiments
                                   {:module-id module-id :dataset-id dataset-id})
                   (rfe/push-state :module/dataset-detail.experiments
                                   {:module-id module-id :dataset-id dataset-id}))))
             (state/dispatch [:form/clear form-id]))
           (state/dispatch [:db/set-value [:forms form-id :error] (:error reply)]))))))})
