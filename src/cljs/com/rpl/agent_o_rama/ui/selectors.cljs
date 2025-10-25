(ns com.rpl.agent-o-rama.ui.selectors
  (:require
   [uix.core :as uix :refer [defui $]]
   [com.rpl.agent-o-rama.ui.common :as common]
   [com.rpl.agent-o-rama.ui.queries :as queries]
   [com.rpl.agent-o-rama.ui.evaluators :as evaluators-ui]
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
  "A searchable combobox for selecting an evaluator."
  [{:keys [module-id value on-change error allowed-types disabled? placeholder]}]
  (let [[search-term set-search-term!] (uix/use-state "")
        [debounced-search] (useDebounce search-term 300)
        [is-open? set-open!] (uix/use-state false)
        input-ref (uix/use-ref nil)

        ;; Create a stable string key from filters to avoid schema nesting issues
        filter-key (str (when allowed-types (str/join "," (sort allowed-types)))
                        "|" debounced-search)

        {:keys [data loading? error query-error]}
        (queries/use-sente-query
         {:query-key [:evaluator-instances module-id filter-key]
          :sente-event [:evaluators/get-all-instances
                        {:module-id module-id
                         :filters {:search-string debounced-search
                                   :types allowed-types}}]
          :enabled? (and is-open? (boolean module-id))})

        evaluators (:items data)
        selected-evaluator (first (filter #(= (:name %) value) evaluators))

        handle-select (fn [evaluator]
                        (on-change (:name evaluator))
                        (set-search-term! "")
                        (set-open! false)
                        (when-let [el (.-current input-ref)] (.blur el)))

        handle-blur #(js/setTimeout (fn [] (set-open! false)) 200)]

    (uix/use-effect
     (fn []
       (when is-open?
         (let [handler #(set-open! false)]
           (.addEventListener js/document "click" handler)
           #(.removeEventListener js/document "click" handler))))
     [is-open?])

    ($ :div.relative
       ($ :div.relative
          ($ :div {:className "pointer-events-none absolute inset-y-0 left-0 flex items-center pl-3"}
             ($ MagnifyingGlassIcon {:className "h-5 w-5 text-gray-400"}))
          ($ :input
             {:ref input-ref
              :type "text"
              :className (common/cn "w-full rounded-md border-0 py-2 pl-10 text-gray-900 ring-1 ring-inset ring-gray-300 placeholder:text-gray-400 focus:ring-2 focus:ring-inset focus:ring-indigo-600 sm:text-sm"
                                    {"ring-red-500" error})
              :placeholder (or placeholder "Search evaluators by name...")
              :value (if is-open? search-term (or value ""))
              :onFocus #(set-open! true)
              :onBlur handle-blur
              :onChange #(do (set-search-term! (.. % -target -value))
                             (set-open! true))
              :disabled disabled?}))
       (when is-open?
         ($ :div {:className "absolute z-10 mt-1 w-full rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 max-h-60 overflow-y-auto"}
            (cond
              loading? ($ :div.p-3.text-sm.text-gray-500 "Loading...")
              query-error ($ :div.p-3.text-sm.text-red-500 "Error fetching evaluators.")
              (empty? evaluators) ($ :div.p-3.text-sm.text-gray-500 "No evaluators found.")
              :else (for [e evaluators]
                      ($ :div.p-3.cursor-pointer.hover:bg-gray-100
                         {:key (:name e)
                          :onClick #(handle-select e)}
                         ($ :div.flex.justify-between.items-start
                            ($ :div
                               ($ :p.font-medium.text-gray-900 (:name e))
                               (when (not (str/blank? (:description e)))
                                 ($ :p.text-xs.text-gray-500.mt-1 (:description e))))
                            ($ :span {:className (common/cn "px-2 py-0.5 rounded-full text-xs font-medium"
                                                            (evaluators-ui/get-evaluator-type-badge-style (:type e)))}
                               (evaluators-ui/get-evaluator-type-display (:type e)))))))))
       (when error
         ($ :p.text-sm.text-red-600.mt-1 error)))))
