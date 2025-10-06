(ns hooks.trace-analytics-e2e-test
  (:require [clj-kondo.hooks-api :as api]))

(defn with-webdriver
  [{:keys [node]}]
  (let [[binding-vec & body] (rest (:children node))
        [driver-sym] (:children binding-vec)]
    (when-not driver-sym
      (throw (ex-info "with-webdriver requires a binding symbol" {})))
    {:node (api/list-node
            (list
             (api/token-node 'let)
             (api/vector-node [driver-sym (api/token-node 'nil)])
             (api/list-node body)))}))
