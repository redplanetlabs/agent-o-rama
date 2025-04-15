(ns com.rpl.agent-o-rama-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require [com.rpl.agent-o-rama :as aor]
            [com.rpl.agent-o-rama.impl :as i]
            [com.rpl.test-helpers]
            [loom.attr :as lattr]
            [loom.graph :as graph]
          ))

(defn node->agg [graph]
  (reduce
    (fn [m n]
      (assoc m n (lattr/attr graph n :agg)))
     {}
    (graph/nodes graph)))

(deftest graph-test
  (letlocals
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/node "N1" "N2" [agent-node]
            (println "node"))
          (aor/agg-start-node "N2" "N3" [agent-node]
            (println "node"))
          (aor/node "N3" "N4" [agent-node]
            (println "node"))
          (aor/agg-node "N4" nil
            (on-any [agent-node tuple]
              (println "node"))
            (on-complete [agent-node state]
              ))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (def GRAPH graph)
    ;; TODO: <<<<>>>
    ;;  - test nested subgrahps
    ;;  - test all error cases
    ))
