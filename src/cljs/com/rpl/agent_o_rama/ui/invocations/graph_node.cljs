(ns com.rpl.agent-o-rama.ui.invocations.graph-node
  "Shared helpers for agent invocation graph nodes (used by graph and Gantt views).")

(def ^:private uuid-key-pattern
  #"^[0-9a-fA-F]{8}-(?:[0-9a-fA-F]{4}-?){3}[0-9a-fA-F]{12}$")

(defn- coerce-invoke-key [k]
  (cond
    (uuid? k) k
    (and (string? k) (re-matches uuid-key-pattern k)) (uuid k)
    (keyword? k) (let [n (name k)]
                     (if (re-matches uuid-key-pattern n) (uuid n) k))
    :else k))

(defn resolve-graph-key
  "Canonical key in `graph-data` for `node-id` (UUID vs string vs keyword keys)."
  [graph-data node-id]
  (when (and graph-data node-id)
    (cond
      (contains? graph-data node-id) node-id
      :else (let [sid (str node-id)]
              (or (when (contains? graph-data sid) sid)
                  (some (fn [[k _]] (when (= (str k) sid) k)) graph-data))))))

(defn graph-node-data
  "Look up a node in `graph-data`; React Flow may stringify `:node-id` while keys stay UUIDs."
  [graph-data node-id]
  (when-let [k (resolve-graph-key graph-data node-id)]
    (get graph-data k)))

(defn normalize-raw-nodes-map
  "Coerce invoke-id map keys and emit targets to UUID for consistent graph traversal."
  [nodes-map]
  (when nodes-map
    (into {}
          (map (fn [[k v]]
                 (let [node (if (map? v)
                              (update v :emits
                                      (fn [emits]
                                        (when emits
                                          (mapv (fn [e]
                                                  (if (map? e)
                                                    (update e :invoke-id coerce-invoke-key)
                                                    e))
                                                emits))))
                              v)]
                   [(coerce-invoke-key k) node]))
               nodes-map))))

(defn starter-node? [node]
  (not (nil? (:started-agg? node))))

(defn agg-node? [node]
  (not (nil? (:agg-state node))))
