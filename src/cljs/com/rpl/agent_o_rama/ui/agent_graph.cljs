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

;; Custom edge component that renders ELK-calculated polylines with arrows
(defn elk-edge-component [props]
  (let [{:keys [id sourceX sourceY targetX targetY data markerEnd style]} (js->clj props :keywordize-keys true)
        elk-points (:elkPoints data)
        edge-color (or (:stroke style) "#a5b4fc")
        
        ;; Helper function to create smooth B-spline curve through points
        create-smooth-path (fn [points]
                              (let [pts (vec points)
                                    n (count pts)]
                                (cond
                                  ;; No points or single point
                                  (<= n 1) ""
                                  
                                  ;; Two points - straight line
                                  (= n 2)
                                  (str "M " (:x (first pts)) " " (:y (first pts))
                                       " L " (:x (second pts)) " " (:y (second pts)))
                                  
                                  ;; Three or more points - create B-spline
                                  :else
                                  (let [;; B-spline basis function (cubic)
                                        b-spline-basis (fn [t]
                                                         (let [t2 (* t t)
                                                               t3 (* t2 t)]
                                                           [(/ (+ 1 (* -3 t) (* 3 t2) (* -1 t3)) 6)
                                                            (/ (+ 4 (* 0 t) (* -6 t2) (* 3 t3)) 6)
                                                            (/ (+ 1 (* 3 t) (* 3 t2) (* -3 t3)) 6)
                                                            (/ (+ 0 (* 0 t) (* 0 t2) (* 1 t3)) 6)]))
                                        
                                        ;; Calculate a point on the B-spline curve
                                        calc-spline-point (fn [p0 p1 p2 p3 t]
                                                            (let [[b0 b1 b2 b3] (b-spline-basis t)]
                                                              {:x (+ (* b0 (:x p0))
                                                                     (* b1 (:x p1))
                                                                     (* b2 (:x p2))
                                                                     (* b3 (:x p3)))
                                                               :y (+ (* b0 (:y p0))
                                                                     (* b1 (:y p1))
                                                                     (* b2 (:y p2))
                                                                     (* b3 (:y p3)))}))
                                        
                                        ;; Extend points for proper B-spline (duplicate first and last)
                                        extended-pts (vec (concat [(first pts)] pts [(last pts)]))
                                        
                                        ;; Number of segments between each control point pair
                                        segments-per-curve 10
                                        
                                        ;; Generate interpolated points along the B-spline
                                        curve-points (atom [])]
                                    
                                    ;; Start with the first point explicitly
                                    (swap! curve-points conj (first pts))
                                    
                                    ;; Generate B-spline curve points
                                    (doseq [i (range (- (count extended-pts) 3))]
                                      (let [p0 (nth extended-pts i)
                                            p1 (nth extended-pts (+ i 1))
                                            p2 (nth extended-pts (+ i 2))
                                            p3 (nth extended-pts (+ i 3))]
                                        (doseq [j (range 1 segments-per-curve)]  ;; Start from 1 to avoid duplicating start point
                                          (let [t (/ j segments-per-curve)
                                                pt (calc-spline-point p0 p1 p2 p3 t)]
                                            (swap! curve-points conj pt)))))
                                    
                                    ;; Add the last point explicitly
                                    (swap! curve-points conj (last pts))
                                    
                                    ;; Build SVG path from the interpolated points
                                    (let [path-points @curve-points]
                                      (if (empty? path-points)
                                        ""
                                        (str "M " (:x (first path-points)) " " (:y (first path-points))
                                             (apply str
                                                    (map (fn [pt]
                                                           (str " L " (:x pt) " " (:y pt)))
                                                         (rest path-points))))))))))
        
        ;; Build SVG path from ELK points
        edge-path (if (and elk-points (seq elk-points))
                    (create-smooth-path elk-points)
                    ;; Fallback to straight line if no ELK points
                    (str "M " sourceX " " sourceY " L " targetX " " targetY))
        
        ;; Calculate arrow rotation based on last two DISTINCT points
        points-vec (when elk-points (vec elk-points))
        ;; Find the last two distinct points (skip duplicates)
        [second-last-distinct last-point] 
        (when (and points-vec (>= (count points-vec) 2))
          (let [last-pt (last points-vec)]
            ;; Find the last point that's different from the final point
            (loop [idx (- (count points-vec) 2)]
              (if (>= idx 0)
                (let [pt (nth points-vec idx)]
                  (if (or (not= (:x pt) (:x last-pt))
                          (not= (:y pt) (:y last-pt)))
                    [pt last-pt]
                    (recur (dec idx))))
                [nil last-pt]))))
        
        arrow-angle (when (and second-last-distinct last-point)
                      (let [dx (- (:x last-point) (:x second-last-distinct))
                            dy (- (:y last-point) (:y second-last-distinct))]
                        (* (/ 180 js/Math.PI) (js/Math.atan2 dy dx))))
        
        ;; Arrow position at the end point
        arrow-x (or (:x last-point) targetX)
        arrow-y (or (:y last-point) targetY)]
    
    ($ :g
       ;; Draw the edge path
       ($ :path {:d edge-path
                 :fill "none"
                 :stroke edge-color
                 :strokeWidth (or (:strokeWidth style) 2)})
       ;; Draw arrow at the end
       (when markerEnd
         ($ :g {:transform (str "translate(" arrow-x "," arrow-y ") "
                               (when arrow-angle (str "rotate(" arrow-angle ")")))}
            ($ :path {:d "M 0 0 L -12 -5 L -12 5 z"
                      :fill edge-color}))))))

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
  "Extract nodes, edges, and start node from graph data without layout"
  (let [start-id (:start-node graph)
        ;; Build nodes with extra metadata for coloring (node-type and start?)
        nodes (->> (get graph :node-map)
                   (map (fn [[k v]]
                          {:id k
                           :type "custom"
                           :draggable false
                           :data {:label k
                                  :node-id k
                                  :node-type (:node-type v)
                                  :is-start? (= k start-id)}
                           :width 170
                           :height 40})))
        
        edges (s/select
               [:node-map
                s/ALL
                (s/collect-one s/FIRST)
                s/LAST
                :output-nodes
                s/ALL]
               graph)]
    {:nodes nodes
     :edges (for [[frm to] edges]
              {:id (str frm "-" to) :source frm :target to
               :markerEnd {:type "arrowclosed" :width 20 :height 20}})
     :start-id start-id}))

(defn process-elk-edge [edge]
  "Process an ELK edge to extract routing points for React Flow"
  (let [sections (or (:sections edge) [])
        ;; ELK provides edge routing as sections with start, end, and bend points
        raw-points (when (seq sections)
                     (mapcat (fn [section]
                               (concat 
                                [(:startPoint section)]
                                (:bendPoints section [])
                                [(:endPoint section)]))
                             sections))
        ;; Remove consecutive duplicate points
        edge-points (when raw-points
                      (reduce (fn [acc point]
                                (if (or (empty? acc)
                                        (let [last-pt (last acc)]
                                          (or (not= (:x last-pt) (:x point))
                                              (not= (:y last-pt) (:y point)))))
                                  (conj acc point)
                                  acc))
                              []
                              raw-points))]
    (-> edge
        ;; Add the routing points to the edge data
        (assoc :data {:elkPoints edge-points})
        ;; Set edge type to use our custom rendering
        (assoc :type "elk-edge"))))

(defn get-layouted-elements [nodes edges options start-id]
  "Layout nodes and edges using ELK.js"
  (let [is-horizontal false
        ;; Create a map of original edges by ID for merging later
        edge-map (into {} (map (fn [e] [(:id e) e]) edges))
        graph #js {:id "root"
                   :layoutOptions options
                   :children (clj->js 
                             (map (fn [node]
                                    (-> node
                                         (cond-> (= start-id (:id node))
                                           (assoc :layoutOptions {"elk.layered.layering.layerConstraint" "FIRST"}))
                                        (assoc :targetPosition (if is-horizontal "left" "top"))
                                        (assoc :sourcePosition (if is-horizontal "right" "bottom"))
                                        (assoc :width 170)
                                        (assoc :height 40)))
                                  nodes))
                   :edges (clj->js edges)}]
    (-> (.layout elk graph)
        (.then (fn [layouted-graph]
                 (let [layouted-nodes (-> (.-children layouted-graph)
                                         (js->clj :keywordize-keys true)
                                         (->> (map (fn [node]
                                                    (-> node
                                                        (assoc :position {:x (:x node) :y (:y node)})
                                                        (dissoc :x :y))))))
                       layouted-edges (-> (.-edges layouted-graph)
                                         (js->clj :keywordize-keys true)
                                         (->> (map (fn [elk-edge]
                                                     ;; Merge original edge properties with ELK results
                                                     (let [original-edge (get edge-map (:id elk-edge))
                                                           processed-edge (process-elk-edge elk-edge)]
                                                       (merge original-edge processed-edge))))))]
                   #js {:nodes layouted-nodes
                        :edges layouted-edges})))
        (.catch js/console.error))))

(defui graph-flow [{:keys [initial-data height selected-node set-selected-node]}]
  (let [;; Extract initial nodes and edges
        {:keys [nodes edges start-id]} (extract-graph-elements initial-data)
        
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
           (-> (get-layouted-elements nodes edges opts start-id)
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
                                        node-type (:node-type data)
                                        selected (= (when selected-node (.-id selected-node)) id)
                                        base-classes (cond
                                                       (= "agg-start-node" node-type)
                                                       ["bg-green-500" "text-white" "border-2" "border-green-600"]
                                                       (= "agg-node" node-type)
                                                       ["bg-yellow-500" "text-white" "border-2" "border-yellow-600"]
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
                                       ($ Handle {:type "target" :position "top" :style {:display "none"}})
                                       ($ Handle {:type "source" :position "bottom" :style {:display "none"}})))))})
                     :edgeTypes
                     (clj->js {"elk-edge" (uix.core/as-react elk-edge-component)})
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
