(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle]]
   ["@dagrejs/dagre" :as Dagre]))

(defn process-graph-data 
  "Process raw graph data into nodes and edges for React Flow"
  [data]
  (let [g (new (.. Dagre -graphlib -Graph))
        
        nodes (s/select [s/ALL
                         (s/selected? s/LAST (s/must :emits))
                         
                         (s/view (fn [[id data]]
                                   {:id (str id)
                                    :type "custom"
                                    :data (assoc data 
                                                 :label (str (:node data))
                                                 :node-id id
                                                 :is-phantom false)}))]
                        data)
        implicit->real (into {}
                             (s/select [s/ALL
                                        (s/collect-one s/FIRST)
                                        s/LAST
                                        (s/must :invoked-agg-invoke-id)] data))
        edges (for [[from to]
                    (s/select [s/ALL
                               (s/collect-one s/FIRST)
                               s/LAST
                               (s/must :emits)
                               s/ALL
                               :invoke-id] data)]
                
                {:id (str from to)
                 :source (str from)
                 :target (str (get implicit->real to to))})
        
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
                                                         "?depth=3&start-node-id=" node-id))
                                      (.then (fn [response]
                                               (let [new-data (:invokes-map response)
                                                     
                                                     ;; Merge new data with existing graph data
                                                     combined-data (merge graph-data new-data)
                                                     
                                                     ;; Re-process the entire combined graph with dagre layout
                                                     {:keys [nodes edges]} (process-graph-data combined-data)]
                                                 
                                                 ;; Update the graph data state
                                                 (set-graph-data combined-data)
                                                 
                                                 ;; Replace all nodes and edges with the re-laid out versions
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
                                                 (fn [{:keys [data]}]
                                                   (let [data (js->clj data :keywordize-keys true)]
                                                     ($ :div {:className "relative"}
                                                        ($ :div {:className "bg-indigo-500 text-white p-3 rounded-md shadow-lg"
                                                                 :style {:width "170px" :height "40px"}}
                                                           (:label data))
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
                           
                           :onPaneClick
                           (fn [_] (set-selected-node nil))}
                ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
                ($ Controls {:className "fill-gray-500 stroke-gray-500"}))))
       (when selected-node
         ($ :div {:className "mt-6 bg-white p-6 rounded-lg shadow"}
            ($ :h3 {:className "text-xl font-semibold mb-3 text-gray-700"} "Selected Node Details")
            ($ :pre {:className "bg-gray-50 p-4 rounded-md text-sm overflow-x-auto border border-gray-200"}
               (common/pp selected-node)))))))

