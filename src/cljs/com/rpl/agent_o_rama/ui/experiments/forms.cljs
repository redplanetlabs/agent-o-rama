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
   [com.rpl.agent-o-rama.ui.selectors :as selectors]
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
     :use-remote-evaluators (some :remote? (:evaluators info))
     :num-repetitions (:num-repetitions info)
     :concurrency (:concurrency info)}))

;; =============================================================================
;; SINGLE-STEP EXPERIMENT FORM
;; =============================================================================

;; =============================================================================
;; REUSABLE SUB-COMPONENTS FOR THE FORM
;; =============================================================================

(defui AgentSelectorDropdown [{:keys [module-id selected-agent on-select-agent disabled? data-testid]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:module-agents module-id]
          :sente-event [:agents/get-for-module {:module-id module-id}]
          :enabled? (boolean module-id)})
        agent-items (->> data
                         (keep (fn [agent]
                                 (let [decoded-name (common/url-decode (:agent-name agent))]
                                   (when (not= decoded-name "_aor-evaluator")
                                     {:key decoded-name
                                      :label decoded-name
                                      :selected? (= selected-agent decoded-name)
                                      :on-select #(on-select-agent decoded-name)}))))
                         vec)
        display-text (cond
                       loading? "Loading agents..."
                       selected-agent selected-agent
                       :else "Select an agent")
        dropdown-disabled? (or disabled? loading? (not module-id))
        empty-content ($ :div.px-4.py-2.text-sm.text-gray-500 "No agents found in this module.")]

    ($ common/Dropdown
       {:label "Agent"
        :disabled? dropdown-disabled?
        :display-text display-text
        :items agent-items
        :loading? loading?
        :error? error
        :empty-content empty-content
        :data-testid data-testid})))

(defui EvaluatorMultiSelector
  "A multi-select component for evaluators using the new searchable selector."
  [{:keys [module-id selected-evaluators on-change filter-type use-remote?]}]
  (let [[remote-eval-name set-remote-eval-name] (uix/use-state "")

        ;; Fetch all evaluators to get info for display
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id]
          :sente-event [:evaluators/get-all-instances {:module-id module-id}]
          :enabled? (boolean module-id)})
        all-evaluators (or (:items data) [])
        filtered-evaluators (if filter-type
                              (filter #(= (:type %) filter-type) all-evaluators)
                              all-evaluators)]

    ($ :div
       ;; Conditional rendering based on use-remote?
       (if use-remote?
         ;; Remote evaluator text input
         ($ :div.space-y-3
            ;; List of selected remote evaluators
            ($ :div.flex.flex-wrap.gap-2
               (if (seq selected-evaluators)
                 (for [e selected-evaluators]
                   ($ :div
                      {:key (:name e)
                       :className "inline-flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium bg-purple-100 text-purple-800 border border-purple-200"}
                      ($ :span.uppercase.font-bold.text-purple-600.text-xs "REMOTE")
                      ($ :span.font-semibold (:name e))
                      ($ :button.p-1.rounded-full.transition-colors.hover:bg-purple-200
                         {:type "button"
                          :onClick #(on-change (vec (remove (fn [sel] (= (:name sel) (:name e))) selected-evaluators)))}
                         ($ XMarkIcon {:className "h-3 w-3"}))))
                 ($ :div.text-sm.text-gray-500.italic "No evaluators selected.")))

            ($ :div.flex.gap-2.items-center
               ($ :input.flex-1.px-3.py-2.border.border-gray-300.rounded-md.text-sm
                  {:type "text"
                   :value remote-eval-name
                   :on-change #(set-remote-eval-name (.. % -target -value))
                   :placeholder "Enter remote evaluator name (e.g., aor/conciseness)"})
               ($ :button.inline-flex.items-center.gap-2.px-3.py-2.text-sm.bg-indigo-600.text-white.rounded-md.hover:bg-indigo-700
                  {:type "button"
                   :disabled (str/blank? remote-eval-name)
                   :onClick (fn []
                              (when-not (str/blank? remote-eval-name)
                                (on-change (conj selected-evaluators {:name remote-eval-name :remote? true}))
                                (set-remote-eval-name "")))}
                  ($ PlusIcon {:className "h-4 w-4"})
                  "Add Remote Evaluator")))

         ;; Local evaluator searchable selector
         ($ :div.space-y-3
            ;; List of selected evaluators as badges
            ($ :div.flex.flex-wrap.gap-2
               (if (seq selected-evaluators)
                 (for [e selected-evaluators
                       :let [is-remote-eval? (:remote? e)
                             evaluator-info (when-not is-remote-eval?
                                              (first (filter #(= (:name %) (:name e)) filtered-evaluators)))]]
                   ($ :div
                      {:key (:name e)
                       :className (common/cn "inline-flex items-center gap-2 px-3 py-2 rounded-lg text-xs font-medium"
                                             (if is-remote-eval?
                                               "bg-purple-100 text-purple-800 border border-purple-200"
                                               "bg-indigo-100 text-indigo-800 border border-indigo-200"))}
                      ($ :div.flex.flex-col.gap-1
                         ($ :div.flex.items-center.gap-2
                            (when is-remote-eval?
                              ($ :span.uppercase.font-bold.text-purple-600.text-xs "REMOTE"))
                            ($ :span.font-semibold (:name e))
                            (when evaluator-info
                              ($ :span
                                 {:className (common/cn
                                              "inline-flex px-2 py-0.5 rounded-full text-xs font-medium"
                                              (evaluators/get-evaluator-type-badge-style (:type evaluator-info)))}
                                 (evaluators/get-evaluator-type-display (:type evaluator-info)))))
                         (when (and evaluator-info (not (str/blank? (:description evaluator-info))))
                           ($ :span.text-indigo-600.max-w-xs.truncate (:description evaluator-info))))
                      ($ :button.p-1.rounded-full.transition-colors
                         {:className (if is-remote-eval? "hover:bg-purple-200" "hover:bg-indigo-200")
                          :type "button"
                          :onClick #(on-change (vec (remove (fn [sel] (= (:name sel) (:name e))) selected-evaluators)))}
                         ($ XMarkIcon {:className "h-3 w-3"}))))
                 ($ :div.text-sm.text-gray-500.italic "No evaluators selected.")))

            ;; The searchable selector
            ($ :div
               ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Search and add evaluators")
               ($ selectors/EvaluatorSelector
                  {:module-id module-id
                   :value "" ;; Always empty since we're adding to a list
                   :on-change (fn [selected-name]
                                (when selected-name
                                  (on-change (conj selected-evaluators {:name selected-name :remote? false}))))
                   :filter-type filter-type
                   :placeholder "Search evaluators by name..."
                   :disabled? false})))))))

;; =============================================================================
;; MAIN EXPERIMENT FORM COMPONENTS
;; ============================================================================= 

(defui TargetEditor [{:keys [form-id index]}]
  (let [path [:spec :targets index]
        {:keys [module-id] :as form} (forms/use-form form-id)
        target-spec-type-field (forms/use-form-field form-id (conj path :target-spec :type))
        agent-name-field (forms/use-form-field form-id (conj path :target-spec :agent-name))
        node-name-field (forms/use-form-field form-id (conj path :target-spec :node))
        metadata-field (forms/use-form-field form-id (conj path :metadata))
        input-mappings (or (get-in form (conj path :input->args)) [])
        is-comparative? (= :comparative (get-in form [:spec :type]))

        selected-agent-name (:value agent-name-field)
        handle-select-agent (fn [agent-name]
                              ((:on-change agent-name-field) agent-name)
                              ;; Reset node selection whenever agent changes
                              (when (not= selected-agent-name agent-name)
                                ((:on-change node-name-field) nil)))]

    ($ :div.p-4.bg-gray-50.border.rounded-lg
       ($ :h4.text-md.font-semibold.mb-3 (str "Target " (inc index)))
       ($ :div.mb-4
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-2 "Target Type")
          ($ selectors/ScopeSelector
             {:value (or (:value target-spec-type-field) :agent)
              :on-change #(state/dispatch [:form/set-experiment-target-type form-id index %])}))

       ($ :div.mb-4
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-1 "Agent Name")
          ($ AgentSelectorDropdown
             {:module-id module-id
              :selected-agent selected-agent-name
              :on-select-agent handle-select-agent
              :data-testid "agent-name-dropdown"})
          (when (:error agent-name-field)
            ($ :p.text-sm.text-red-600.mt-1 (:error agent-name-field))))

       ;; Conditionally render node name dropdown
       (when (= (:value target-spec-type-field) :node)
         ($ :div.mt-4
            ($ selectors/NodeSelectorDropdown
               {:module-id module-id
                :agent-name selected-agent-name
                :value (:value node-name-field)
                :on-change (:on-change node-name-field)
                :error (:error node-name-field)
                :disabled? (not selected-agent-name)
                :data-testid "node-name-dropdown"})))

       ($ :div.mt-4
          ($ :label.block.text-sm.font-medium.text-gray-700.mb-1 "Metadata (JSON map, optional)")
          ($ :textarea.w-full.p-2.border.border-gray-300.rounded-md.text-sm.font-mono
             {:placeholder "{ \"key\": \"value\" }"
              :value (or (:value metadata-field) "")
              :onChange (:on-change metadata-field)
              :rows 3})
          (when (:error metadata-field)
            ($ :p.text-sm.text-red-600.mt-1 (:error metadata-field))))

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

        ;; Check if dataset is remote
        {:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:dataset-props module-id dataset-id]
          :sente-event [:datasets/get-props {:module-id module-id :dataset-id dataset-id}]
          :enabled? (and (boolean module-id) (boolean dataset-id))})
        is-remote-dataset? (:module-name data)

        ;; Basic info fields
        name-field (forms/use-form-field form-id :name)
        ;; Data selection fields
        snapshot-field (forms/use-form-field form-id :snapshot)
        selector-type-field (forms/use-form-field form-id [:selector :type])
        selector-tag-field (forms/use-form-field form-id [:selector :tag])

        ;; Remote evaluator fields
        use-remote-evaluators-field (forms/use-form-field form-id :use-remote-evaluators)
        use-remote-evaluators? (:value use-remote-evaluators-field)

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
          ($ :<>
             ($ :h3.text-lg.font-medium.text-gray-900.mb-4 "Target Configuration")
             ;; Display validation error for targets
             (when-let [targets-error (get-in form [:field-errors :spec :targets])]
               ($ :div.mb-4.p-3.bg-red-50.border.border-red-200.rounded-md
                  ($ :p.text-sm.text-red-600 targets-error))))

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

          ;; Show "Use remote evaluators" checkbox only for remote datasets
          (when is-remote-dataset?
            ($ :div.mb-4.flex.items-center
               ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
                  {:type "checkbox"
                   :id "use-remote-evaluators"
                   :checked use-remote-evaluators?
                   :on-change #(do
                                 ;; Toggle the checkbox
                                 ((:on-change use-remote-evaluators-field) (.. % -target -checked))
                                 ;; Clear evaluators when toggling
                                 (state/dispatch [:form/update-field form-id :evaluators []]))})
               ($ :label.ml-2.block.text-sm.text-gray-700
                  {:htmlFor "use-remote-evaluators"}
                  "Use remote evaluators")))

          (let [evaluators-field (forms/use-form-field form-id :evaluators)]
            ($ :div
               ($ EvaluatorMultiSelector
                  {:module-id module-id
                   :selected-evaluators (:value evaluators-field)
                   :on-change (:on-change evaluators-field)
                   :filter-type spec-type
                   :use-remote? use-remote-evaluators?})
               (when (:error evaluators-field)
                 ($ :p.text-sm.text-red-600.mt-1 (:error evaluators-field))))))

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
                                   :metadata ""
                                   :input->args [{:id (random-uuid) :value "$"}]}]}
                 :evaluators []
                 :use-remote-evaluators false
                 :num-repetitions 1
                 :concurrency 1}
           merged (merge base props)
           merged-spec (merge (:spec base) (:spec props))]
       (assoc merged :spec merged-spec)))
   :validators
   {:name [forms/required]
    :evaluators [(fn [v] (when (empty? v) "At least one evaluator is required"))]
    [:spec :targets] [(fn [targets]
                        (let [missing-agents (filter #(nil? (get-in % [:target-spec :agent-name])) targets)
                              missing-nodes (filter #(and (= :node (get-in % [:target-spec :type]))
                                                          (str/blank? (get-in % [:target-spec :node])))
                                                    targets)]
                          (cond
                            (seq missing-agents) "All targets must have an agent selected"
                            (seq missing-nodes) "All node targets must have a node selected"
                            :else nil)))]}
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

          ;; Parse metadata and convert input->args back to simple strings for the backend
          cleaned-form-state (update-in form-with-selection [:spec :targets]
                                        (fn [targets]
                                          (mapv (fn [target]
                                                  (-> target
                                                      ;; TODO this is questionable..
                                                      (update :metadata #(if (str/blank? %) {} (-> % js/JSON.parse js->clj)))
                                                      (update :input->args (fn [args] (mapv :value args)))))
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
