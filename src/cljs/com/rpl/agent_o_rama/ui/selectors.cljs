(ns com.rpl.agent-o-rama.ui.selectors
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]))

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
           "Agent-level (all nodes)"))
     ($ :div.flex.items-center
        ($ :input.h-4.w-4.border-gray-300.text-indigo-600.focus:ring-indigo-500
           {:type "radio" :id "scope-node" :name "scope-type"
            :checked (= value :node)
            :on-change #(on-change :node)})
        ($ :label.ml-3.block.text-sm.text-gray-700 {:htmlFor "scope-node"}
           "Node-specific"))))

(defui NodeSelectorDropdown
  "A dropdown that fetches and displays nodes for a given agent."
  [{:keys [module-id agent-name value on-change disabled? error]}]
  (let [{:keys [data loading? error]}
        (queries/use-sente-query
         {:query-key [:graph module-id agent-name]
          :sente-event [:invocations/get-graph {:module-id module-id :agent-name agent-name}]
          :enabled? (boolean (and module-id agent-name))})

        nodes (when-let [graph (:graph data)]
                (sort (keys (:node-map graph))))

        select-classes (common/cn "w-full p-3 border rounded-md text-sm transition-colors"
                                  (if error
                                    "border-red-300 focus:ring-red-500 focus:border-red-500"
                                    "border-gray-300 focus:ring-blue-500 focus:border-blue-500"))]
    ($ :div.space-y-1
       ($ :label.block.text-sm.font-medium.text-gray-700
          "Node" ($ :span.text-red-500.ml-1 "*"))
       ($ :select {:className select-classes
                   :value (or value "")
                   :disabled (or disabled? loading?)
                   :onChange #(on-change (.. % -target -value))}
          ($ :option {:value ""}
             (cond
               (not agent-name) "← Select an agent first"
               loading? "Loading nodes..."
               :else "Select a node..."))
          (when nodes
            (for [node-name nodes]
              ($ :option {:key node-name :value node-name}
                 node-name))))
       (if error
         ($ :p.text-sm.text-red-600.mt-1 error)
         ($ :div.mt-1.h-5)))))
