(ns com.rpl.agent-o-rama.ui.stats
  (:require
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]

   
   [com.rpl.agent-o-rama.ui.common :as common]))

;; Dummy git SHA data
(def dummy-versions
  [{:sha "a1b2c3d" :message "feat: add new agent functionality" :date "2024-01-15"}
   {:sha "e4f5g6h" :message "fix: resolve connection timeout issues" :date "2024-01-14"}
   {:sha "i7j8k9l" :message "refactor: optimize graph rendering performance" :date "2024-01-13"}
   {:sha "m0n1o2p" :message "feat: implement multi-agent coordination" :date "2024-01-12"}
   {:sha "q3r4s5t" :message "fix: handle edge case in token counting" :date "2024-01-11"}
   {:sha "u6v7w8x" :message "docs: update API documentation" :date "2024-01-10"}])

(defui version-dropdown [{:keys [selected-version set-selected-version]}]
  (let [[is-open set-is-open] (uix/use-state false)]
    ($ :div {:className "relative inline-block text-left mb-6"}
       ($ :div
          ($ :button {:type "button"
                      :className "inline-flex w-full justify-between gap-x-1.5 rounded-md bg-white px-3 py-2 text-sm font-semibold text-gray-900 shadow-sm ring-1 ring-inset ring-gray-300 hover:bg-gray-50 min-w-80"
                      :onClick #(set-is-open (not is-open))}
             ($ :div {:className "flex flex-col items-start"}
                ($ :div {:className "font-mono text-sm"}
                   (str "Version: " (:sha selected-version)))
                ($ :div {:className "text-xs text-gray-500 truncate max-w-64"}
                   (:message selected-version)))
             ($ :svg {:className "h-5 w-5 text-gray-400" :viewBox "0 0 20 20" :fill "currentColor"}
                ($ :path {:fillRule "evenodd" 
                          :d "M5.23 7.21a.75.75 0 011.06.02L10 11.168l3.71-3.938a.75.75 0 111.08 1.04l-4.25 4.5a.75.75 0 01-1.08 0l-4.25-4.5a.75.75 0 01.02-1.06z" 
                          :clipRule "evenodd"}))))
       
       (when is-open
         ($ :div {:className "absolute right-0 z-10 mt-2 w-80 origin-top-right rounded-md bg-white shadow-lg ring-1 ring-black ring-opacity-5 focus:outline-none"}
            ($ :div {:className "py-1"}
               (for [version dummy-versions]
                 ($ :button {:key (:sha version)
                             :className (str "block w-full px-4 py-3 text-left text-sm hover:bg-gray-100 "
                                           (when (= (:sha version) (:sha selected-version))
                                             "bg-gray-50"))
                             :onClick #(do (set-selected-version version)
                                         (set-is-open false))}
                    ($ :div {:className "flex justify-between items-start"}
                       ($ :div {:className "flex-1"}
                          ($ :div {:className "font-mono text-sm font-medium text-gray-900"}
                             (:sha version))
                          ($ :div {:className "text-xs text-gray-600 mt-1 pr-2"}
                             (:message version)))
                       ($ :div {:className "text-xs text-gray-400"}
                          (:date version)))))))))))

;; Generate dummy stats data for a selected node, varying by version
(defn generate-dummy-stats [node-id version]
  (let [version-seed (hash (:sha version))
        base-seed (hash node-id)
        combined-seed (+ version-seed base-seed)]
    {:execution-time (+ 50 (mod (* combined-seed 13) 500)) ; 50-550ms
     :tokens {:input (+ 100 (mod (* combined-seed 17) 1000))
              :output (+ 50 (mod (* combined-seed 19) 800))}
     :store-operations {:reads (mod (* combined-seed 7) 20)
                        :writes (mod (* combined-seed 11) 10)}
     :model-calls (+ 1 (mod (* combined-seed 23) 5))}))

;; Generate dummy overall stats for the entire graph, varying by version
(defn generate-overall-stats [version]
  (let [version-seed (hash (:sha version))]
    {:total-execution-time (+ 500 (mod (* version-seed 29) 2000)) ; 500-2500ms
     :total-tokens {:input (+ 2000 (mod (* version-seed 31) 5000))
                    :output (+ 1000 (mod (* version-seed 37) 4000))}
     :total-store-operations {:reads (+ 50 (mod (* version-seed 41) 100))
                              :writes (+ 20 (mod (* version-seed 43) 50))}
     :total-model-calls (+ 10 (mod (* version-seed 47) 20))
     :nodes-executed (+ 5 (mod (* version-seed 53) 10))}))

(defui stats-panel [{:keys [selected-node selected-version]}]
  (if selected-node
    ;; Show individual node stats
    (let [node-id (.-id selected-node)
          stats (generate-dummy-stats node-id selected-version)]
      ($ :div {:className "mt-6 p-6"}
         ($ :h3 {:className "text-lg font-semibold text-gray-800 mb-4"}
            (str "Stats for Node: " node-id))
         
         ($ :div {:className "grid grid-cols-2 md:grid-cols-4 gap-4"}
            
            ;; Execution Time
            ($ :div {:className "bg-blue-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-blue-600"}
                  "Execution Time")
               ($ :div {:className "text-2xl font-bold text-blue-900"}
                  (str (:execution-time stats) "ms")))
            
            ;; Model Calls
            ($ :div {:className "bg-green-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-green-600"}
                  "Model Calls")
               ($ :div {:className "text-2xl font-bold text-green-900"}
                  (:model-calls stats)))
            
            ;; Tokens
            ($ :div {:className "bg-purple-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-purple-600"}
                  "Tokens")
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "In: " (get-in stats [:tokens :input])))
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "Out: " (get-in stats [:tokens :output]))))
            
            ;; Store Operations
            ($ :div {:className "bg-orange-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-orange-600"}
                  "Store Operations")
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "R: " (get-in stats [:store-operations :reads])))
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "W: " (get-in stats [:store-operations :writes])))))))
    
    ;; Show overall stats when no node is selected
    (let [stats (generate-overall-stats selected-version)]
      ($ :div {:className "mt-6 p-6"}
         ($ :h3 {:className "text-lg font-semibold text-gray-800 mb-2"}
            "Overall Agent Graph Stats")
         ($ :p {:className "text-sm text-gray-600 mb-4"}
            "Aggregate performance metrics across all nodes. Click on a node to see individual stats.")
         
         ($ :div {:className "grid grid-cols-2 md:grid-cols-5 gap-4"}
            
            ;; Total Execution Time
            ($ :div {:className "bg-blue-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-blue-600"}
                  "Total Execution Time")
               ($ :div {:className "text-2xl font-bold text-blue-900"}
                  (str (:total-execution-time stats) "ms")))
            
            ;; Nodes Executed
            ($ :div {:className "bg-indigo-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-indigo-600"}
                  "Nodes Executed")
               ($ :div {:className "text-2xl font-bold text-indigo-900"}
                  (:nodes-executed stats)))
            
            ;; Total Model Calls
            ($ :div {:className "bg-green-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-green-600"}
                  "Total Model Calls")
               ($ :div {:className "text-2xl font-bold text-green-900"}
                  (:total-model-calls stats)))
            
            ;; Total Tokens
            ($ :div {:className "bg-purple-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-purple-600"}
                  "Total Tokens")
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "In: " (get-in stats [:total-tokens :input])))
               ($ :div {:className "text-lg font-bold text-purple-900"}
                  (str "Out: " (get-in stats [:total-tokens :output]))))
            
            ;; Total Store Operations
            ($ :div {:className "bg-orange-50 p-4 rounded-lg"}
               ($ :div {:className "text-sm font-medium text-orange-600"}
                  "Total Store Operations")
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "R: " (get-in stats [:total-store-operations :reads])))
               ($ :div {:className "text-lg font-bold text-orange-900"}
                  (str "W: " (get-in stats [:total-store-operations :writes])))))))))

(defui agent-graph [{:keys [selected-version]}]
  (let [{:strs [module-id agent-id]} (js->clj (wouter/useParams))
        {:keys [data loading?]}
        (common/use-query {:query-key ["agent" module-id agent-id "graph"]
                           :query-url (str "/api/agents/" module-id "/" agent-id "/graph")})
        [selected-node set-selected-node] (uix/use-state nil)]
    (if loading?
      "...loading"
      ($ :div
         ($ agent-graph/graph {:initial-data data
                               :height "500px"
                               :selected-node selected-node
                               :set-selected-node set-selected-node})
         ($ stats-panel {:selected-node selected-node 
                         :selected-version selected-version})))))

(defui stats []
  (let [[selected-version set-selected-version] (uix/use-state (first dummy-versions))]
    ($ :div.p-4
       ($ version-dropdown {:selected-version selected-version 
                            :set-selected-version set-selected-version})
       ($ agent-graph {:selected-version selected-version}))))
