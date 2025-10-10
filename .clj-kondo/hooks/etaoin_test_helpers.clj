(ns hooks.etaoin-test-helpers
  (:require
   [clj-kondo.hooks-api :as api]))

(defn with-system
  [{:keys [node]}]
  (let [[binding-vec & body] (rest (:children node))
        [system-atom agent-module opts] (:children binding-vec)]
    (when-not system-atom
      (throw (ex-info "with-system requires a system-atom" {})))
    (when-not agent-module
      (throw (ex-info "with-system requires an agent module" {})))
    {:node (api/list-node
            (list*
             (api/token-node 'do)
             system-atom
             agent-module
             (if opts opts (api/token-node 'nil))
             body))}))

(defn with-webdriver
  [{:keys [node]}]
  (let [[binding-vec & body]     (rest (:children node))
        [system-atom driver-sym] (:children binding-vec)]
    (when-not system-atom
      (throw (ex-info "with-webdriver requires a system-atom" {})))
    (when-not driver-sym
      (throw (ex-info "with-webdriver requires a binding symbol" {})))
    {:node (api/list-node
            (list*
             (api/token-node 'let)
             (api/vector-node [driver-sym system-atom])
             body))}))
