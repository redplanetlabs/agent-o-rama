(ns com.rpl.agent-o-rama.ui.invocations.graph-layout
  "Dagre layout and graph traversal helpers for invocation trace views."
  (:require
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]
   ["@dagrejs/dagre" :as Dagre]))

(defn process-graph-data
  "Applies Dagre layout to pre-processed nodes and edges."
  [nodes-map real-edges implicit-edges]
  (let [g (new (.. Dagre -graphlib -Graph))

        nodes (->> nodes-map
                   (map (fn [[id data]]
                          {:id (str id)
                           :type "custom"
                           :draggable false
                           :data (assoc data
                                        :label (str (:node data))
                                        :node-id id)})))

        all-edges (concat real-edges implicit-edges)]

    (.setDefaultEdgeLabel ^js g (fn [] #js {}))
    (.setGraph ^js g #js {})

    (doseq [edge all-edges] (.setEdge ^js g (:source edge) (:target edge)))
    (doseq [node nodes]
      (.setNode ^js g (:id node) (clj->js (merge node {:width 170 :height 40}))))

    (Dagre/layout g)

    (let [nodes-with-layout (for [node nodes
                                  :let [position (.node g (:id node))]]
                              (assoc node :position position))]
      {:nodes nodes-with-layout
       :edges all-edges})))

(defn find-downstream-nodes
  "Find all nodes downstream from the given set of modified node IDs."
  [graph-data modified-node-ids]
  (let [get-downstream-from-node (fn [start-node-id]
                                   (loop [to-visit #{start-node-id}
                                          visited #{}
                                          downstream #{}]
                                     (if (empty? to-visit)
                                       downstream
                                       (let [current (first to-visit)
                                             remaining (disj to-visit current)]
                                         (if (visited current)
                                           (recur remaining visited downstream)
                                           (let [node-data (gn/graph-node-data graph-data current)
                                                 emitted-ids (set (map :invoke-id (:emits node-data)))
                                                 new-downstream (if (= current start-node-id)
                                                                  downstream
                                                                  (conj downstream current))
                                                 new-to-visit (into remaining emitted-ids)]
                                             (recur new-to-visit
                                                    (conj visited current)
                                                    new-downstream)))))))]
    (reduce (fn [all-downstream modified-node-id]
              (into all-downstream (get-downstream-from-node modified-node-id)))
            #{}
            modified-node-ids)))
