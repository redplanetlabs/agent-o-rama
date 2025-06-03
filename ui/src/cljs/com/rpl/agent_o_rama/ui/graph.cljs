(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle]]
   ["@dagrejs/dagre" :as Dagre]))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))

(defui selected-node-component [{:keys [selected-node graph-data handle-paginate-node loading-nodes flow-nodes set-selected-node set-nodes]}]
  (let [data (when selected-node 
               (js->clj (.-data selected-node) :keywordize-keys true))
        node-id (str (:node-id data))
        node-name (:node data)
        input (:input data)
        result (:result data)
        start-time (:start-time-millis data)
        finish-time (:finish-time-millis data)
        duration (when (and start-time finish-time)
                   (- finish-time start-time))
        emits (:emits data)
        has-paginated (:has-paginated-children data)]
    
    (when selected-node
      ($ :div {:className "mt-6 bg-white shadow-lg rounded-lg border border-gray-200 max-w-4xl"}
         ($ :div {:className "p-6"}
            ;; Node Info Section
            ($ :div {:className "grid grid-cols-1 md:grid-cols-2 lg:grid-cols-3 gap-4"}
               ($ :div {:className "bg-indigo-50 p-3 rounded-md"}
                  ($ :div {:className "flex justify-between items-center"}
                     ($ :span {:className "text-sm font-medium text-indigo-700"} "Node")
                     ($ :span {:className "text-sm text-indigo-600 font-mono"} node-name))
                  ($ :div {:className "flex justify-between items-center mt-1"}
                     ($ :span {:className "text-sm font-medium text-indigo-700"} "ID")
                     ($ :span {:className "text-xs text-indigo-500 font-mono"} node-id)))
               
               ;; Input Section
               (when input
                 ($ :div {:className "bg-green-50 p-3 rounded-md"}
                    ($ :div {:className "text-sm font-medium text-green-700 mb-1"} "Input")
                    ($ :div {:className "text-sm text-green-600 font-mono break-words"}
                       (if (array? input)
                         (pr-str (js->clj input))
                         (str input)))))
               
               ;; Result Section - only show if result is not nil
               (when result
                 ($ :div {:className "bg-blue-50 p-3 rounded-md"}
                    ($ :div {:className "text-sm font-medium text-blue-700 mb-1"} "Result")
                    ($ :div {:className "text-sm text-blue-600 font-mono break-words"}
                       (pr-str (js->clj result)))))
               
               ;; Timing Section
               (when (and start-time finish-time)
                 ($ :div {:className "bg-yellow-50 p-3 rounded-md"}
                    ($ :div {:className "text-sm font-medium text-yellow-700 mb-2"} "Timing")
                    ($ :div {:className "space-y-1"}
                       ($ :div {:className "flex justify-between"}
                          ($ :span {:className "text-xs text-yellow-600"} "Duration")
                          ($ :span {:className "text-xs text-yellow-600 font-mono"} (str duration "ms")))
                       ($ :div {:className "flex justify-between"}
                          ($ :span {:className "text-xs text-yellow-600"} "Started")
                          ($ :span {:className "text-xs text-yellow-600 font-mono"} 
                             (.toLocaleTimeString (js/Date. start-time))))
                       ($ :div {:className "flex justify-between"}
                          ($ :span {:className "text-xs text-yellow-600"} "Finished")
                          ($ :span {:className "text-xs text-yellow-600 font-mono"} 
                             (.toLocaleTimeString (js/Date. finish-time))))))))
            
            ;; Emits Section (full width)
            (when (and emits (> (count emits) 0))
              ($ :div {:className "mt-4 bg-purple-50 p-3 rounded-md"}
                 ($ :div {:className "text-sm font-medium text-purple-700 mb-2"} 
                    (str "Emits (" (count emits) ")"))
                 ($ :div {:className "grid grid-cols-1 md:grid-cols-2 gap-2"}
                    (for [emit (js->clj emits :keywordize-keys true)]
                      (let [emit-id (str (:invoke-id emit))
                            is-loaded (contains? graph-data (:invoke-id emit))
                            is-loading (contains? loading-nodes emit-id)
                            border-class (if is-loaded "border-purple-200" "border-dashed border-purple-300")
                            cursor-class (if is-loading "cursor-wait" "cursor-pointer")
                            bg-class (if is-loaded "bg-gray-50" "bg-white hover:bg-purple-50")]
                        ($ :div {:key emit-id
                                 :className (str bg-class " p-2 rounded border " border-class " " cursor-class " transition-colors")
                                 :onClick (fn [e]
                                            (.stopPropagation e)
                                            (when-not is-loading
                                              (if is-loaded
                                                ;; Find and select the loaded node
                                                (let [nodes (js->clj flow-nodes :keywordize-keys true)
                                                      target-node (->> nodes
                                                                       (filter #(= (-> % :data :node-id) (:invoke-id emit)))
                                                                       first)]
                                                  
                                                  (set-selected-node (clj->js target-node)))
                                                ;; Load the unloaded node
                                                (when handle-paginate-node
                                                  (handle-paginate-node emit-id)))))}
                           ($ :div {:className "text-xs text-purple-600"}
                              ($ :div (str "→ " (:node-name emit)))
                              (when (:args emit)
                                ($ :div {:className "text-purple-500 mt-1 break-words"}
                                   (pr-str (js->clj (:args emit)))))
                              ($ :div {:className "text-purple-400 mt-1 font-mono text-xs"}
                                 (str "ID: " emit-id))
                              (when is-loading
                                ($ :div {:className "text-purple-400 mt-1 text-xs italic"}
                                   "Loading..."))))))))))))))

(defn process-graph-data 
  "Process raw graph data into nodes and edges for React Flow"
  [data]
  (let [g (new (.. Dagre -graphlib -Graph))
        
        nodes (s/select [s/ALL
                         (s/selected? s/LAST (s/must :emits))
                         
                         (s/view (fn [[id data]]
                                   {:id (str id)
                                    :type "custom"
                                    :draggable false
                                    :data (assoc data 
                                                 :label (str (:node data))
                                                 :node-id id
                                                 :is-phantom false)}))]
                        data)
        
        edges (for [[from to]
                    (s/select [s/ALL
                               (s/collect-one s/FIRST)
                               s/LAST
                               (s/must :emits)
                               s/ALL
                               :invoke-id] data)]
                
                {:id (str from to)
                 :source (str from)
                 :target (str to)})
        
        ;; Check if a node's children are paginated (not all loaded)
        has-paginated-children? (fn [node-id]
                                  (when-let [node-data (get data node-id)]
                                    (let [emitted-ids (set (map :invoke-id (:emits node-data)))
                                          paginated-children (:has-paginated-children node-data)]
                                      (some #(and (contains? emitted-ids %)
                                                  (not (contains? data %)))
                                            paginated-children))))
        
        ;; Create phantom nodes for pagination
        phantom-nodes (for [node nodes
                            :let [node-id (-> node :data :node-id)]
                            :when (has-paginated-children? node-id)]
                        {:id (str "phantom-" node-id)
                         :type "phantom"
                         :data {:label "Click to paginate"
                                :parent-node-id node-id
                                :is-phantom true}})
        
        ;; Create edges from parent nodes to their phantom children
        phantom-edges (for [phantom phantom-nodes
                            :let [parent-id (-> phantom :data :parent-node-id)]]
                        {:id (str parent-id "->" (:id phantom))
                         :source (str parent-id)
                         :target (:id phantom)})
        
        all-nodes (concat nodes phantom-nodes)
        all-edges (concat edges phantom-edges)]

    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {})

    (doall (for [edge all-edges] (.setEdge g (:source edge) (:target edge))))
    (doall (for [node all-nodes]
             (.setNode g (:id node) (clj->js
                                     (merge node {:width 170 :height 40})))))
    
    (Dagre/layout g)
    
    (let [nodes-with-layout (for [node all-nodes
                                  :let [position (.node g (:id node))]]
                              (assoc node 
                                     :position position))]
      {:nodes nodes-with-layout
       :edges all-edges})))

(defui graph [{:keys [initial-data api-url module-id agent-id invoke-id]}]
  (let [[selected-node set-selected-node] (uix/use-state nil)
        [loading-nodes set-loading-nodes] (uix/use-state #{})
        [graph-data set-graph-data] (uix/use-state initial-data)
        
        ;; Process current graph data
        {:keys [nodes edges]} (process-graph-data graph-data)
        
        ;; Use React Flow's state management hooks
        [flow-nodes set-nodes on-nodes-change] (useNodesState (clj->js nodes))
        [flow-edges set-edges on-edges-change] (useEdgesState (clj->js edges))
        
        handle-paginate-node (uix/use-callback
                              (fn [node-id]
                                (when-not (contains? loading-nodes node-id)
                                  (set-loading-nodes #(conj % node-id))
                                  (-> (common/fetch (str api-url 
                                                         "?depth=1&start-node-id=" node-id))
                                      (.then (fn [response]
                                               (let [new-data (:invokes-map response)
                                                     
                                                     ;; Merge new data with existing graph data
                                                     combined-data (merge graph-data new-data)
                                                     
                                                     ;; Re-process the entire combined graph with dagre layout
                                                     {:keys [nodes edges]} (process-graph-data combined-data)]
                                                 
                                                 ;; Update the graph data state
                                                 (set-graph-data combined-data)
                                                 
                                                 ;; Replace all nodes and edges with the re-laid out versions, keepign the selection
                                                 (set-nodes (clj->js nodes))
                                                 (set-edges (clj->js edges))
                                                 (set-loading-nodes #(disj % node-id)))))
                                      (.catch (fn [error]
                                                (js/console.error "Failed to load paginated data:" error)
                                                (set-loading-nodes #(disj % node-id)))))))
                              [graph-data api-url loading-nodes set-nodes set-edges])]
    
    ($ :<>
       ($ :div {:className "rounded-lg overflow-hidden"}
          ($ :h2 {:className "text-2xl font-semibold mb-4 text-gray-700"} "Agent Invocation Graph")
          ($ :div {:style {:width "100%" :height "500px"}}
             ($ ReactFlow {:nodes flow-nodes 
                           :edges flow-edges
                           :onNodesChange on-nodes-change
                           :onEdgesChange on-edges-change
                           :proOptions {:hideAttribution true}
                           :nodeTypes (clj->js {"custom"
                                                (uix.core/as-react
                                                 (fn [{:keys [data id]}]
                                                   (println "data" data)
                                                   (let [data (js->clj data :keywordize-keys true)
                                                         label (:label data)
                                                         selected (= (when selected-node (.-id selected-node)) id)
                                                         base-classes (cond
                                                                        ;; TODO special handling for root node once we wire up query topology
                                                                        #_(str/starts-with? label "start")
                                                                        #_["bg-green-500" "text-white" "border-2" "border-green-600"]
                                                                        
                                                                        (agg-node? data)
                                                                        ["bg-yellow-500" "text-white" "border-2" "border-yellow-600"]

                                                                        (starter-node? data)
                                                                        ["bg-green-500" "text-white" "border-2" "border-green-600"]

                                                                        :else
                                                                        ["bg-white" "text-gray-800" "border-2" "border-gray-300"])
                                                         selection-classes (if selected
                                                                             ["ring-4" "ring-blue-400" "ring-opacity-75" "shadow-2xl" "transform" "scale-105"]
                                                                             ["shadow-lg"])
                                                         common-classes ["p-3" "rounded-md" "transition-all" "duration-200"]
                                                         node-className (str/join " " (concat base-classes selection-classes common-classes))]
                                                     ($ :div {:className "relative"}
                                                        ($ :div {:className node-className
                                                                 :style {:width "170px" :height "40px"}}
                                                           label)
                                                        (when (:result data)
                                                          ($ :div {:className "absolute -top-1 -right-1 w-3 h-3 bg-green-500 rounded-full border-2 border-white shadow-sm"}))
                                                        ($ Handle {:type "target" :position "top"})
                                                        ($ Handle {:type "source" :position "bottom"})))))
                                                
                                                "phantom"
                                                (uix.core/as-react
                                                 (fn [{:keys [data]}]
                                                   (let [data (js->clj data :keywordize-keys true)
                                                         parent-node-id (:parent-node-id data)]
                                                     ($ :div {:className "relative cursor-pointer"
                                                              :onClick (fn [e]
                                                                         (.stopPropagation e)
                                                                         (handle-paginate-node parent-node-id))}
                                                        ($ :div {:className "bg-gray-100 text-gray-600 p-3 rounded-md shadow-lg border-2 border-dashed border-gray-400 hover:bg-gray-200 transition-colors"
                                                                 :style {:width "170px" :height "40px"}}
                                                           (:label data))
                                                        ($ Handle {:type "target" :position "top"})))))})
                           :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}}
                           
                           :onNodeClick
                           (fn [_ node] (set-selected-node node))
                           
                           #_#_:onPaneClick
                           (fn [_] (set-selected-node nil))}
                ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
                ($ Controls {:className "fill-gray-500 stroke-gray-500"}))))
       (when selected-node
         ($ selected-node-component {:selected-node selected-node
                                     :graph-data graph-data
                                     :handle-paginate-node handle-paginate-node
                                     :loading-nodes loading-nodes
                                     :flow-nodes flow-nodes
                                     :set-nodes set-nodes
                                     :set-selected-node set-selected-node})))))

