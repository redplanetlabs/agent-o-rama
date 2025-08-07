(ns com.rpl.agent-o-rama.ui.agent-graph
  (:require
   [com.rpl.agent-o-rama.ui.common :as common]
   [clojure.string :as str]
   [goog.i18n.DateTimeFormat :as dtf]
   [goog.date.UtcDateTime    :as utc-dt]
   
   [uix.core :as uix :refer [defui defhook $]]
   
   [com.rpl.specter :as s]

   ["react" :refer [useState useCallback useEffect useLayoutEffect]]
   ["@xyflow/react" :refer [ReactFlow Background Controls useNodesState useEdgesState Handle useReactFlow ReactFlowProvider]]
   ["elkjs/lib/elk.bundled.js" :default ELK]))

;; ELK instance
(def elk (ELK.))

;; ELK layout options
(def elk-options
  #js {"elk.algorithm" "layered"
       "elk.layered.spacing.nodeNodeBetweenLayers" "100"
       "elk.spacing.nodeNode" "80"
       "elk.direction" "DOWN"
       "feedbackEdges" "true"
       "edgeRouting" "POLYLINE"
       "spacing.edgeEdgeBetweenLayers" "100"
       "crossingMinimization.strategy" "LAYER_SWEEP"
       "nodePlacement.strategy" "BRANDES_KOEPF"})

(defn extract-graph-elements [{:keys [graph]}]
  "Extract nodes and edges from graph data without layout"
  (let [nodes (s/select [:node-map
                         s/MAP-KEYS
                         (s/view
                          (fn [k]
                            {:id k
                             :type "custom"
                             :draggable false
                             :data {:label k :node-id k}
                             :width 170
                             :height 40}))]
                        graph)
        
        edges (s/select
               [:node-map
                s/ALL
                (s/collect-one s/FIRST)
                s/LAST
                :output-nodes
                s/ALL]
               graph)]
    {:nodes nodes
     :edges (for [[frm to] edges] {:id (str frm "-" to) :source frm :target to
     :markerEnd {:type "arrowclosed" :width 20 :height 20}
     })}))

(defn get-layouted-elements [nodes edges options]
  "Layout nodes and edges using ELK.js"
  (let [is-horizontal false
        graph #js {:id "root"
                   :layoutOptions options
                   :children (clj->js 
                             (map (fn [node]
                                    (-> node
                                        (assoc :targetPosition (if is-horizontal "left" "top"))
                                        (assoc :sourcePosition (if is-horizontal "right" "bottom"))
                                        (assoc :width 170)
                                        (assoc :height 40)))
                                  nodes))
                   :edges (clj->js edges)}]
    (-> (.layout elk graph)
        (.then (fn [layouted-graph]
                 #js {:nodes (-> (.-children layouted-graph)
                                (js->clj :keywordize-keys true)
                                (->> (map (fn [node]
                                           (-> node
                                               (assoc :position {:x (:x node) :y (:y node)})
                                               (dissoc :x :y))))))
                      :edges (-> (.-edges layouted-graph)
                                (js->clj :keywordize-keys true))}))
        (.catch js/console.error))))

(defui graph-flow [{:keys [initial-data height selected-node set-selected-node]}]
  (let [;; Extract initial nodes and edges
        {:keys [nodes edges]} (extract-graph-elements initial-data)
        
        ;; Use React Flow's state management hooks
        [flow-nodes set-nodes on-nodes-change] (useNodesState #js [])
        [flow-edges set-edges on-edges-change] (useEdgesState #js [])
        
        ;; Get React Flow instance with fitView function
        react-flow-instance (useReactFlow)
        fit-view (when react-flow-instance (.-fitView react-flow-instance))
        
        ;; Track if initial layout has been done
        [initial-layout-done? set-initial-layout-done] (useState false)]
    
    ;; Calculate initial layout on mount - only when data changes
    (useLayoutEffect
     (fn []
       (when (and nodes edges (not initial-layout-done?))
         (let [opts elk-options]
           (-> (get-layouted-elements nodes edges opts)
               (.then (fn [result]
                        (let [layouted-nodes (clj->js (.-nodes result))
                              layouted-edges (clj->js (.-edges result))]
                          (set-nodes layouted-nodes)
                          (set-edges layouted-edges)
                          (set-initial-layout-done true)
                          (when (fn? fit-view) 
                            (fit-view))))))))
       js/undefined)
     #js [nodes edges initial-layout-done? fit-view])
    
    ($ :div {:style {:width "100%" :height height}}
       ($ ReactFlow {:nodes flow-nodes 
                     :edges flow-edges
                     :onNodesChange on-nodes-change
                     :onEdgesChange on-edges-change
                     :proOptions (clj->js {:hideAttribution true})
                     :nodeTypes
                     (clj->js {"custom"
                               (uix.core/as-react
                                (fn [{:keys [data id]}]
                                  (let [data (js->clj data :keywordize-keys true)
                                        label (:label data)
                                        node-id (:node-id data)
                                        selected (= (when selected-node (.-id selected-node)) id)
                                        base-classes (cond
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
                                       ($ Handle {:type "target" :position "top"})
                                       ($ Handle {:type "source" :position "bottom"})))))})
                     :defaultEdgeOptions {:style {:strokeWidth 2 :stroke "#a5b4fc"}
                                          :markerEnd {:type "arrowclosed" :width 20 :height 20}}
                     :onNodeClick (fn [_ node]
                                    (if (and selected-node (= (.-id node) (.-id selected-node)))
                                      (set-selected-node nil)
                                      (set-selected-node node)))}
          ($ Background {:variant "dots" :gap 12 :size 1 :color "#e0e0e0"})
          ($ Controls {:className "fill-gray-500 stroke-gray-500"})))))

(defui graph [props]
  ($ ReactFlowProvider
     ($ graph-flow props)))
