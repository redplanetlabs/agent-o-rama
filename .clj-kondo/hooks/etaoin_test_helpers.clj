(ns hooks.etaoin-test-helpers
  (:require [clj-kondo.hooks-api :as api]))

(defn with-system
  [{:keys [node]}]
  (let [[binding-vec & body] (rest (:children node))
        [agent-module opts] (:children binding-vec)]
    (when-not agent-module
      (throw (ex-info "with-system requires an agent module" {})))
    {:node (api/list-node
            (list*
             (api/token-node 'do)
             agent-module
             (when opts opts)
             body))}))
