(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState]]
   ["@dagrejs/dagre" :as Dagre]
   ["axios" :as axios]))

(defui custom-node [props]
  (js/console.log "x" props)
  (let [data (.-data props)
        has-more? (.-has-more data)
        node-id (.-node-id data)]
    ($ :div {:className "relative"}
       ($ :div {:className "bg-indigo-500 text-white p-3 rounded-md shadow-lg"}
          {:style {:width "170px" :height "40px"}}
          (.-label data))
       (when has-more?
         ($ :button {:className (str "absolute bottom-0 right-0 transform translate-x-1/2 translate-y-1/2 "
                                     " text-white rounded-full w-6 h-6 text-xs font-bold shadow-md")
                     :onClick (fn [e]
                                (.stopPropagation e)
                                #_(handle-paginate-node node-id))})))))

(def node-types (clj->js {"custom" custom-node}))

(defn process-graph-data 
  "Process raw graph data into nodes and edges for React Flow"
  [data]
  (let [g (new (.. Dagre -graphlib -Graph))
        
        nodes (s/select [s/ALL
                         (s/selected? s/LAST (s/must :emits))
                         
                         (s/view (fn [[id data]]
                                   {:id (str id)
                                    :data (assoc data 
                                                 :label (str (:node data))
                                                 :node-id id)}))]
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
                                            paginated-children))))]

    (.setDefaultEdgeLabel g (fn [] #js {}))
    (.setGraph g #js {})

    (doall (for [edge edges] (.setEdge g (:source edge) (:target edge))))
    (doall (for [node nodes]
             (.setNode g (:id node) (clj->js
                                     (merge node {:width 170 :height 40})))))
    
    (Dagre/layout g)
    
    (let [nodes-with-layout (for [node nodes
                                  :let [position (.node g (:id node))
                                        node-id (-> node :data :node-id)
                                        has-more? (has-paginated-children? node-id)]]
                              (assoc node 
                                     :position position
                                     :data (assoc (:data node) :has-more has-more?)))]
      {:nodes nodes-with-layout
       :edges edges})))

(defui graph [{:keys [initial-data api-url module-id agent-id invoke-id]}]
  (let [[selected-node set-selected-node] (uix/use-state nil)
        [loading-nodes set-loading-nodes] (uix/use-state #{})
        
        ;; Process initial data
        {:keys [nodes edges]} (process-graph-data initial-data)
        
        ;; Use React Flow's state management hooks
        [flow-nodes set-nodes on-nodes-change] (useNodesState (clj->js nodes))
        [flow-edges set-edges on-edges-change] (useEdgesState (clj->js edges))
        
        handle-paginate-node (uix/use-callback
                              (fn [node-id]
                                (when-not (contains? @loading-nodes node-id)
                                  (set-loading-nodes #(conj % node-id))
                                  (-> (.get axios (str api-url 
                                                       "?depth=3&start-node-id=" node-id))
                                      (.then (fn [response]
                                               (let [new-data (-> response .-data .-invokes-map js->clj)
                                                     {:keys [nodes edges]} (process-graph-data new-data)
                                                     
                                                     ;; Get current nodes/edges as CLJS data structures
                                                     current-nodes (js->clj flow-nodes :keywordize-keys true)
                                                     current-edges (js->clj flow-edges :keywordize-keys true)
                                                     
                                                     ;; Create sets of existing IDs
                                                     existing-node-ids (set (map :id current-nodes))
                                                     existing-edge-ids (set (map :id current-edges))
                                                     
                                                     ;; Filter out duplicates
                                                     new-nodes (remove #(existing-node-ids (:id %)) nodes)
                                                     new-edges (remove #(existing-edge-ids (:id %)) edges)]
                                                 
                                                 ;; Update nodes and edges
                                                 (set-nodes #(clj->js (concat (js->clj % :keywordize-keys true) new-nodes)))
                                                 (set-edges #(clj->js (concat (js->clj % :keywordize-keys true) new-edges)))
                                                 (set-loading-nodes #(disj % node-id))))
                                             (.catch (fn [error]
                                                       (js/console.error "Failed to load paginated data:" error)
                                                       (set-loading-nodes #(disj % node-id))))))))
                              [flow-nodes flow-edges api-url loading-nodes set-nodes set-edges])]
    
    ($ :<>
       ($ :div {:className "rounded-lg overflow-hidden"}
          ($ :h2 {:className "text-2xl font-semibold mb-4 text-gray-700"} "Agent Invocation Graph")
          ($ :div {:style {:width "100%" :height "500px"}}
             ($ ReactFlow {:nodes flow-nodes 
                           :edges flow-edges
                           :onNodesChange on-nodes-change
                           :onEdgesChange on-edges-change
                           :proOptions {:hideAttribution true}
                           :nodeTypes node-types
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

