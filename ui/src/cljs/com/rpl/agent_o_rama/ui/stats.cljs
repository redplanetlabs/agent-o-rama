(ns com.rpl.agent-o-rama.ui.stats
  (:require
   [com.rpl.agent-o-rama.ui.agent-graph :as agent-graph]
   
   [uix.core :as uix :refer [defui defhook $]]
   ["axios" :as axios]
   ["wouter" :as wouter :refer [useLocation]]

   
   [com.rpl.agent-o-rama.ui.common :as common]))

;; Generate dummy stats data for a selected node
(defn generate-dummy-stats [node-id]
  {:execution-time (+ 50 (rand-int 500)) ; 50-550ms
   :tokens {:input (+ 100 (rand-int 1000))
            :output (+ 50 (rand-int 800))}
   :store-operations {:reads (rand-int 20)
                      :writes (rand-int 10)}
   :model-calls (+ 1 (rand-int 5))})

;; Generate dummy overall stats for the entire graph
(defn generate-overall-stats []
  {:total-execution-time (+ 500 (rand-int 2000)) ; 500-2500ms
   :total-tokens {:input (+ 2000 (rand-int 5000))
                  :output (+ 1000 (rand-int 4000))}
   :total-store-operations {:reads (+ 50 (rand-int 100))
                            :writes (+ 20 (rand-int 50))}
   :total-model-calls (+ 10 (rand-int 20))
   :nodes-executed (+ 5 (rand-int 10))})

(defui stats-panel [{:keys [selected-node]}]
  (if selected-node
    ;; Show individual node stats
    (let [node-id (.-id selected-node)
          stats (generate-dummy-stats node-id)]
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
    (let [stats (generate-overall-stats)]
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

(defui agent-graph []
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
         ($ stats-panel {:selected-node selected-node})))))

(defui stats []
  ($ :div.p-4
     ($ agent-graph)))
