(ns com.rpl.agent-o-rama.ui.invocations.graph-node
  "Shared helpers for agent invocation graph nodes (used by graph and Gantt views).")

(defn node-ids-equal?
  "Compare invocation ids that may be UUIDs or strings (React Flow / URL / re-frame)."
  [a b]
  (boolean (and a b (= (str a) (str b)))))

(defn canonical-node-id
  "Return the key from `graph-data` that matches `node-id`, or `node-id` if none."
  [graph-data node-id]
  (when node-id
    (or (when (contains? graph-data node-id) node-id)
        (some (fn [[k _]] (when (node-ids-equal? k node-id) k)) graph-data)
        node-id)))

(defn graph-node-data
  "Look up a node in `graph-data`; React Flow may stringify `:node-id` while keys stay UUIDs."
  [graph-data node-id]
  (when (and graph-data node-id)
    (or (get graph-data node-id)
        (get graph-data (str node-id))
        (let [sid (str node-id)]
          (some (fn [[k v]] (when (= (str k) sid) v)) graph-data)))))

(defn flow-node-for-selection
  "Build a React Flow–shaped node map for the details panel (works without React Flow mounted)."
  [graph-data node-id]
  (when-let [data (graph-node-data graph-data node-id)]
    (let [canonical (canonical-node-id graph-data node-id)]
      #js {:id (str canonical)
           :type "custom"
           :draggable false
           :data (clj->js (assoc data
                                 :label (str (or (:node data) "?"))
                                 :node-id canonical))})))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))
