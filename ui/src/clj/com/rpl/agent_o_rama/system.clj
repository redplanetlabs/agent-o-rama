(ns com.rpl.agent-o-rama.system)

(defonce system (atom {}))

(defn get-object [k]
  (if-let [v (get @system k)]
    v
    (throw (ex-info "not found" {:key k :availible-keys (keys @system)})))))
