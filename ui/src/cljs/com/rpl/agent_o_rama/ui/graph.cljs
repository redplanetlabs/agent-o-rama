(ns com.rpl.agent-o-rama.ui.graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useOnSelectionChange]]
   ["@dagrejs/dagre" :as Dagre]))

(defui graph [{:keys [data on-paginate-node]}]
  (let [[selected-node set-selected-node] (uix/use-state nil)
        
        g (new (.. Dagre -graphlib -Graph))
        
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
        
        ;; Find nodes that have paginated children
        nodes-with-pagination (into #{}
                                   (s/select [s/ALL
                                             (s/selected? s/LAST :has-paginated-children)
                                             s/FIRST]
                                            data))
        
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
    
    (let [nodes (for [node nodes
                      :let [position (.node g (:id node))
                            node-id (-> node :data :node-id)
                            has-more? (has-paginated-children? node-id)]]
                  (assoc node 
                         :position position
                         :data (assoc (:data node) :has-more has-more?)))]
      
      ($ :<>
       ($ :div.rounded-lg.overflow-hidden
        ($ :h2.text-2xl.font-semibold.mb-4.text-gray-700 "Agent Invocation Graph")
        ($ :div {:style {:width "100%" :height "500px"}}
         ($ ReactFlow {:nodes (clj->js nodes) 
                       :edges (clj->js edges)
                       :proOptions {:hideAttribution true}
                       :nodeTypes {:custom (fn [props]
                                             (let [data (.-data props)
                                                   has-more? (.-has-more data)
                                                   node-id (.-node-id data)]
                                               (js/console.log "data" data)
                                               ($ :div {:className "relative"}
                                                  ($ :div {:className "bg-indigo-500 text-white p-3 rounded-md shadow-lg"}
                                                     {:style {:width "170px" :height "40px"}}
                                                     (.-label data)))
                                               (when has-more?
                                                 ($ :button {:className "absolute bottom-0 right-0 transform translate-x-1/2 translate-y-1/2 bg-blue-500 text-white rounded-full w-6 h-6 text-xs font-bold shadow-md hover:bg-blue-600"}
                                                    {:onClick (fn [e]
                                                                (.stopPropagation e)
                                                                (when on-paginate-node
                                                                  (on-paginate-node node-id)))}
                                                    "+"))))}
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
            (common/pp selected-node))))))))

