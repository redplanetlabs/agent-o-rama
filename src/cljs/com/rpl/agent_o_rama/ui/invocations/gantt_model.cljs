(ns com.rpl.agent-o-rama.ui.invocations.gantt-model
  "Pure row model for the invocation Gantt/timeline view."
  (:require
   [com.rpl.agent-o-rama.ui.invocations.graph-node :as gn]))

(defn canonical-node-id
  "Resolve React Flow string ids back to graph-data keys when possible."
  [graph-data node-id]
  (or (gn/resolve-graph-key graph-data node-id)
      node-id))

(defn- canonical-edge
  [graph-data {:keys [source target] :as edge}]
  (let [source-id (canonical-node-id graph-data source)
        target-id (canonical-node-id graph-data target)]
    (when (and source-id target-id)
      (assoc edge :source source-id :target target-id))))

(defn- children-by-parent
  [graph-data edges]
  (reduce (fn [m edge]
            (if-let [{:keys [source target]} (canonical-edge graph-data edge)]
              (update m source (fnil conj []) target)
              m))
          {}
          edges))

(defn- remove-child
  [children child-id]
  (vec (remove #(= child-id %) (or children []))))

(defn- remove-child-from-all-parents
  [children-map child-id]
  (into {}
        (map (fn [[parent children]]
               [parent (remove-child children child-id)]))
        children-map))

(defn- conj-distinct
  [children child-id]
  (let [children (vec (or children []))]
    (if (some #(= child-id %) children)
      children
      (conj children child-id))))

(defn- agg-node-ids
  [graph-data]
  (->> graph-data
       (keep (fn [[node-id data]]
               (when (gn/agg-node? data)
                 node-id)))))

(defn- place-agg-finalizers
  "Move each visible aggregation finalizer under its real start node.

  React Flow needs branch -> agg implicit edges to draw fan-in. A tree timeline
  does not: those edges make the finalizer appear under an arbitrary branch.
  The agg state records `:agg-start-invoke-id`, which is the semantic owner of
  the aggregation region."
  [graph-data children-map]
  (reduce (fn [m agg-id]
            (let [agg-data (gn/graph-node-data graph-data agg-id)
                  start-id (some->> (:agg-start-invoke-id agg-data)
                                    (canonical-node-id graph-data))]
              (if (and start-id
                       (not= start-id agg-id)
                       (gn/graph-node-data graph-data start-id))
                (-> m
                    (remove-child-from-all-parents agg-id)
                    (update start-id conj-distinct agg-id))
                m)))
          children-map
          (agg-node-ids graph-data)))

(defn gantt-children-map
  "Build the parent -> children map used by the timeline tree."
  [graph-data real-edges implicit-edges]
  (let [edges (concat (or real-edges []) (or implicit-edges []))]
    (->> edges
         (children-by-parent graph-data)
         (place-agg-finalizers graph-data))))

(defn- child-sort-key
  [graph-data node-id]
  (let [node-data (gn/graph-node-data graph-data node-id)]
    [(or (:start-time-millis node-data) js/Number.POSITIVE_INFINITY)
     (str node-id)]))

(defn sort-child-ids
  [graph-data child-ids]
  (sort-by #(child-sort-key graph-data %) child-ids))

(defn- node-time-summary
  [data]
  (let [start (:start-time-millis data)
        finish (:finish-time-millis data)]
    (when start
      {:start-time-millis start
       :finish-time-millis finish
       :in-progress? (nil? finish)})))

(defn- merge-time-summary
  [a b]
  (cond
    (nil? a) b
    (nil? b) a
    :else
    {:start-time-millis (min (:start-time-millis a)
                             (:start-time-millis b))
     :finish-time-millis (cond
                           (and (:finish-time-millis a) (:finish-time-millis b))
                           (max (:finish-time-millis a) (:finish-time-millis b))

                           (:finish-time-millis a)
                           (:finish-time-millis a)

                           :else
                           (:finish-time-millis b))
     :in-progress? (or (:in-progress? a) (:in-progress? b))}))

(defn descendant-time-summary
  "Timing summary for descendants of `node-id`, excluding the node itself."
  [graph-data children-map node-id]
  (letfn [(subtree-summary [id]
            (when-let [data (gn/graph-node-data graph-data id)]
              (reduce merge-time-summary
                      (node-time-summary data)
                      (map subtree-summary (get children-map id [])))))]
    (reduce merge-time-summary
            nil
            (map subtree-summary (get children-map node-id [])))))

(defn collect-visible-rows
  "DFS flattening of the invocation tree.

  `collapsed` is a set of node ids whose children are hidden."
  [graph-data children-map root-id collapsed]
  (letfn [(walk [node-id depth]
            (when-let [data (gn/graph-node-data graph-data node-id)]
              (let [children (->> (get children-map node-id [])
                                  distinct
                                  (sort-child-ids graph-data)
                                  vec)
                    row {:node-id node-id
                         :depth depth
                         :label (str (or (:node data) "?"))
                         :data data
                         :child-ids children
                         :descendant-time-summary (descendant-time-summary graph-data
                                                                            children-map
                                                                            node-id)}
                    child-rows (when-not (contains? collapsed node-id)
                                 (mapcat #(walk % (inc depth)) children))]
                (cons row child-rows))))]
    (vec (walk (canonical-node-id graph-data root-id) 0))))

(defn build-row-model
  [graph-data real-edges implicit-edges root-id collapsed]
  (let [children-map (gantt-children-map graph-data real-edges implicit-edges)]
    {:children-map children-map
     :rows (if (and graph-data root-id)
             (collect-visible-rows graph-data children-map root-id collapsed)
             [])}))

(defn trace-time-bounds
  "Returns [t0-ms t1-ms] covering all rows that have at least a start time."
  [rows now-ms]
  (let [row-time-summaries (map (fn [r]
                                  (node-time-summary (:data r)))
                                rows)
        descendant-time-summaries (map :descendant-time-summary rows)
        time-summaries (concat row-time-summaries descendant-time-summaries)
        starts (keep :start-time-millis time-summaries)
        ends (for [{:keys [start-time-millis finish-time-millis in-progress?]} time-summaries]
               (or (when (and start-time-millis in-progress?) now-ms)
                   finish-time-millis
                   start-time-millis))
        t0 (when (seq starts) (apply min starts))
        t1 (when (seq ends) (apply max ends))]
    (when (and t0 t1 (>= t1 t0))
      (if (= t0 t1)
        [t0 (+ t0 1)]
        [t0 t1]))))

(defn total-root-ms
  [rows]
  (when-let [root (first rows)]
    (let [d (:data root)
          s (:start-time-millis d)
          f (:finish-time-millis d)]
      (when (and s f)
        (- f s)))))
