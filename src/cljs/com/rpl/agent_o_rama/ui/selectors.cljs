(ns com.rpl.agent-o-rama.ui.selectors
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.searchable-selector :as ss]
   [clojure.string :as str]
   ["use-debounce" :refer [useDebounce]]
   ["@heroicons/react/24/outline" :refer [MagnifyingGlassIcon]]))

(defui ScopeSelector
  "A simple component with radio buttons to select a scope: Agent or Node."
  [{:keys [value on-change]}]
  ($ :div.space-y-2
     ($ :div.flex.items-center
        ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
           {:type "radio" :id "scope-agent" :name "scope-type"
            :checked (= value :agent)
            :on-change #(on-change :agent)})
        ($ :label.ml-3.block.text-sm.text-gray-700 {:htmlFor "scope-agent"}
           "Agent"))
     ($ :div.flex.items-center
        ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
           {:type "radio" :id "scope-node" :name "scope-type"
            :checked (= value :node)
            :on-change #(on-change :node)})
        ($ :label.ml-3.block.text-sm.text-gray-700 {:htmlFor "scope-node"}
           "Node"))))

(defui NodeSelectorDropdown
  "A dropdown that fetches and displays nodes for a given agent."
  [{:keys [module-id agent-name value on-change disabled? error data-testid]}]
  (let [{:keys [data loading? error query-error]}
        (queries/use-sente-query
         {:query-key [:graph module-id agent-name]
          :sente-event [:invocations/get-graph {:module-id module-id :agent-name agent-name}]
          :enabled? (boolean (and module-id agent-name))})

        nodes (when-let [graph (:graph data)]
                (sort (keys (:node-map graph))))

        node-items (mapv (fn [node-name]
                           {:key node-name
                            :label node-name
                            :selected? (= value node-name)
                            :on-select #(on-change node-name)})
                         (or nodes []))

        display-text (cond
                       (not agent-name) "← Select an agent first"
                       loading? "Loading nodes..."
                       (not (str/blank? value)) value
                       :else "Select a node...")

        empty-content ($ :div.px-4.py-2.text-sm.text-gray-500 "No nodes found for this agent.")]

    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          "Node" ($ :span.text-red-500.ml-1 "*"))
       ($ common/Dropdown
          {:label "Node"
           :disabled? (or disabled? (not agent-name))
           :display-text display-text
           :items node-items
           :loading? loading?
           :error? query-error
           :empty-content empty-content
           :data-testid data-testid})
       (if error
         ($ :p.text-sm.text-red-600.mt-1 error)
         ($ :div.mt-1.h-5)))))

(defui EvaluatorSelector
  "A searchable combobox for selecting an evaluator.
   
   Wrapper around SearchableSelector with evaluator-specific logic."
  [{:keys [module-id value on-change error allowed-types disabled? placeholder data-testid]
    :or {data-testid "evaluator-selector"}}]
  ($ ss/SearchableSelector
     {:module-id module-id
      :value value
      :on-change on-change
      :sente-event-fn (fn [module-id search-string]
                       [:evaluators/get-all-instances
                        {:module-id module-id
                         :filters {:search-string search-string
                                   :types allowed-types}}])
      :items-key :items
      :item-id-fn :name
      :item-label-fn :name
      :item-sublabel-fn :description
      :placeholder (or placeholder "Search evaluators by name...")
      :label "Evaluator"
      :hide-label? true  ; EvaluatorSelector historically doesn't show a label
      :error error
      :disabled? disabled?
      :with-icon? true
      :data-testid data-testid}))
