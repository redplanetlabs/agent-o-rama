(ns com.rpl.agent-o-rama.ui.invocations.graph-node
  "Shared helpers for agent invocation graph nodes (used by graph and Gantt views).")

(defn graph-node-data
  "Look up a node in `graph-data`; React Flow may stringify `:node-id` while keys stay UUIDs."
  [graph-data node-id]
  (when (and graph-data node-id)
    (or (get graph-data node-id)
        (get graph-data (str node-id))
        (let [sid (str node-id)]
          (some (fn [[k v]] (when (= (str k) sid) v)) graph-data)))))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))
