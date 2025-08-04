(ns com.rpl.agent-o-rama.impl.ui)

(defonce system (atom {}))

(defn get-object [k]
  (if-let [v (get @system k)]
    v
    (throw (ex-info "not found or nil" {:key k :availible-keys (keys @system)}))))
