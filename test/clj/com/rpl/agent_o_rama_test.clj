(ns com.rpl.agent-o-rama-test
  (:use [clojure.test]
        [com.rpl.test-helpers])
  (:require [com.rpl.agent-o-rama :as aor]
            [com.rpl.agent-o-rama.impl :as i]
            [com.rpl.rama.aggs :as aggs]
            [loom.attr :as lattr]
            [loom.graph :as graph])
  (:import [com.rpl.agent_o_rama.types Node NodeAgg NodeAggStart]))

(defn node->agg [graph]
  (reduce
    (fn [m n]
      (assoc m n (lattr/attr graph n :agg)))
     {}
    (graph/nodes graph)))

(deftest graph-test
  (letlocals
    (bind res (volatile! []))
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/node "N1" "N2" [agent-node]
            (vswap! res conj "N1"))
          (aor/agg-start-node "N2" "N3" [agent-node]
            (vswap! res conj "N2"))
          (aor/node "N3" "N4" [agent-node arg1]
            (vswap! res conj "N3"))
          (aor/agg-node "N4" nil aggs/+sum
            [agent-node agg node-start-res]
            (vswap! res conj "N4"))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (is (= "N1" (:start-node graph)))
    (is (some? (:uuid graph)))
    (is (some? (java.util.UUID/fromString (:uuid graph))))
    (bind node-map (:node-map graph))
    (is (= #{"N1" "N2" "N3" "N4"} (-> node-map keys set)))
    (let [node (get node-map "N1")]
      (is (= #{"N2"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil)
      (is (= ["N1"] @res))
      (vreset! res []))
    (let [node (get node-map "N2")]
      (is (= #{"N3"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (instance? NodeAggStart (:node node)))
      (is (= "N4" (-> node :node :agg-node-name)))
      ((-> node :node :node-fn) nil)
      (is (= ["N2"] @res))
      (vreset! res []))
    (let [node (get node-map "N3")]
      (is (= #{"N4"} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil 1)
      (is (= ["N3"] @res))
      (vreset! res []))
    (let [node (get node-map "N4")]
      (is (= #{} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? NodeAgg (:node node)))
      (is (= 0 ((-> node :node :init-fn))))
      (is (= 14 ((-> node :node :update-fn) 3 11)))
      ((-> node :node :node-fn) nil nil nil)
      (is (= ["N4"] @res))
      (vreset! res []))


    ;; test nested aggs
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/node "N1" "N2" [agent-node]
            (vswap! res conj "N1"))
          (aor/agg-start-node "N2" "N3" [agent-node]
            (vswap! res conj "N2"))
          (aor/node "N3" "N4" [agent-node arg1]
            (vswap! res conj "N3"))
          (aor/agg-start-node "N4" "N5" [agent-node]
            (vswap! res conj "N4"))
          (aor/agg-node "N5" "N6" aggs/+sum
            [agent-node agg node-start-res]
            (vswap! res conj "N5"))
          (aor/agg-start-node "N6" "N7" [agent-node]
            (vswap! res conj "N6"))
          (aor/node "N7" "N8" [agent-node]
            (vswap! res conj "N7"))
          (aor/agg-node "N8" "N9" aggs/+vec-agg
            [agent-node agg node-start-res]
            (vswap! res conj "N8"))
          (aor/agg-node "N9" nil aggs/+sum
            [agent-node agg node-start-res]
            (vswap! res conj "N9"))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (is (= "N1" (:start-node graph)))
    (is (some? (:uuid graph)))
    (is (some? (java.util.UUID/fromString (:uuid graph))))
    (bind node-map (:node-map graph))
    (is (= #{"N1" "N2" "N3" "N4" "N5" "N6" "N7" "N8" "N9"} (-> node-map keys set)))
    (let [node (get node-map "N1")]
      (is (= #{"N2"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil)
      (is (= ["N1"] @res))
      (vreset! res []))
    (let [node (get node-map "N2")]
      (is (= #{"N3"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (instance? NodeAggStart (:node node)))
      (is (= "N9" (-> node :node :agg-node-name)))
      ((-> node :node :node-fn) nil)
      (is (= ["N2"] @res))
      (vreset! res []))
    (let [node (get node-map "N3")]
      (is (= #{"N4"} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil 1)
      (is (= ["N3"] @res))
      (vreset! res []))
    (let [node (get node-map "N4")]
      (is (= #{"N5"} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? NodeAggStart (:node node)))
      (is (= "N5" (-> node :node :agg-node-name)))
      ((-> node :node :node-fn) nil)
      (is (= ["N4"] @res))
      (vreset! res []))
    (let [node (get node-map "N5")]
      (is (= #{"N6"} (:output-nodes node)))
      (is (= "N4" (:agg-context node)))
      (is (instance? NodeAgg (:node node)))
      (is (= 0 ((-> node :node :init-fn))))
      (is (= 14 ((-> node :node :update-fn) 3 11)))
      ((-> node :node :node-fn) nil nil nil)
      (is (= ["N5"] @res))
      (vreset! res []))
    (let [node (get node-map "N6")]
      (is (= #{"N7"} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? NodeAggStart (:node node)))
      (is (= "N8" (-> node :node :agg-node-name)))
      ((-> node :node :node-fn) nil)
      (is (= ["N6"] @res))
      (vreset! res []))
    (let [node (get node-map "N7")]
      (is (= #{"N8"} (:output-nodes node)))
      (is (= "N6" (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil)
      (is (= ["N7"] @res))
      (vreset! res []))
    (let [node (get node-map "N8")]
      (is (= #{"N9"} (:output-nodes node)))
      (is (= "N6" (:agg-context node)))
      (is (instance? NodeAgg (:node node)))
      (is (= [] ((-> node :node :init-fn))))
      (is (= [1 2 3] ((-> node :node :update-fn) [1 2] 3)))
      ((-> node :node :node-fn) nil nil nil)
      (is (= ["N8"] @res))
      (vreset! res []))
    (let [node (get node-map "N9")]
      (is (= #{} (:output-nodes node)))
      (is (= "N2" (:agg-context node)))
      (is (instance? NodeAgg (:node node)))
      (is (= 0 ((-> node :node :init-fn))))
      (is (=  12 ((-> node :node :update-fn) 5 7)))
      ((-> node :node :node-fn) nil nil nil)
      (is (= ["N9"] @res))
      (vreset! res []))


    ;; starting with aggStartNode
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "N10" "N1" [agent-node arg1 arg2 arg3]
            (vswap! res conj "N10"))
          (aor/node "N1" "N2" [agent-node]
            (vswap! res conj "N1"))
          (aor/agg-node "N2" nil aggs/+sum
            [agent-node agg node-start-res]
            (vswap! res conj "N2"))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (is (= "N10" (:start-node graph)))
    (is (some? (:uuid graph)))
    (is (some? (java.util.UUID/fromString (:uuid graph))))
    (bind node-map (:node-map graph))
    (is (= #{"N1" "N2" "N10"} (-> node-map keys set)))
    (let [node (get node-map "N10")]
      (is (= #{"N1"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (instance? NodeAggStart (:node node)))
      (is (= "N2" (-> node :node :agg-node-name)))
      ((-> node :node :node-fn) nil 1 2 3)
      (is (= ["N10"] @res))
      (vreset! res []))
    (let [node (get node-map "N1")]
      (is (= #{"N2"} (:output-nodes node)))
      (is (= "N10" (:agg-context node)))
      (is (instance? Node (:node node)))
      ((-> node :node :node-fn) nil)
      (is (= ["N1"] @res))
      (vreset! res []))
    (let [node (get node-map "N2")]
      (is (= #{} (:output-nodes node)))
      (is (= "N10" (:agg-context node)))
      (is (instance? NodeAgg (:node node)))
      (is (= 0 ((-> node :node :init-fn))))
      (is (= 14 ((-> node :node :update-fn) 3 11)))
      ((-> node :node :node-fn) nil nil nil)
      (is (= ["N2"] @res))
      (vreset! res []))
    ))

(deftest branching-graph-test

  )

;; TODO: <<<<>>>
;;  - test nested subgraphs
;;  - test all error cases
;;    - need to error if have startaggnode without a corresponding aggnode
;;      - can error when converting the loom graph
;;    - error if looping back to agg start node whether it's in another agg context or not
;;    - error if going directly to aggnode or looping from aggnode to another node in same context...
;;    - specifically test aggnode looping on itself
