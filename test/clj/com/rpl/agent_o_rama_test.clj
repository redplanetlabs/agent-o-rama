(ns com.rpl.agent-o-rama-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require [com.rpl.agent-o-rama :as aor]
            [com.rpl.agent-o-rama.helpers :as h]
            [com.rpl.agent-o-rama.impl :as i]
            [com.rpl.agent-o-rama.types :as aor-types]
            [com.rpl.rama.aggs :as aggs]
            [com.rpl.rama.test :as rtest]
            [loom.attr :as lattr]
            [loom.graph :as graph])
  (:import [com.rpl.agentorama BuiltIn]
           [com.rpl.agent_o_rama.types Node NodeAgg NodeAggStart]
           [com.rpl.rama.ops
             RamaAccumulatorAgg0
             RamaAccumulatorAgg2
             RamaCombinerAgg]))

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
          (aor/node "N1" "N2"
            (fn [agent-node]
              (vswap! res conj "N1")))
          (aor/agg-start-node "N2" "N3"
            (fn [agent-node]
              (vswap! res conj "N2")))
          (aor/node "N3" "N4"
            (fn [agent-node arg1]
              (vswap! res conj "N3")))
          (aor/agg-node "N4" nil aggs/+sum
            (fn [agent-node agg node-start-res]
              (vswap! res conj "N4")))
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
          (aor/node "N1" "N2"
            (fn [agent-node]
              (vswap! res conj "N1")))
          (aor/agg-start-node "N2" "N3"
            (fn [agent-node]
              (vswap! res conj "N2")))
          (aor/node "N3" "N4"
            (fn [agent-node arg1]
              (vswap! res conj "N3")))
          (aor/agg-start-node "N4" "N5"
            (fn [agent-node]
              (vswap! res conj "N4")))
          (aor/agg-node "N5" "N6" aggs/+sum
            (fn [agent-node agg node-start-res]
              (vswap! res conj "N5")))
          (aor/agg-start-node "N6" "N7"
            (fn [agent-node]
              (vswap! res conj "N6")))
          (aor/node "N7" "N8"
            (fn [agent-node]
              (vswap! res conj "N7")))
          (aor/agg-node "N8" "N9" aggs/+vec-agg
            (fn [agent-node agg node-start-res]
              (vswap! res conj "N8")))
          (aor/agg-node "N9" nil aggs/+sum
            (fn [agent-node agg node-start-res]
              (vswap! res conj "N9")))
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
          (aor/agg-start-node "N10" "N1"
            (fn [agent-node arg1 arg2 arg3]
              (vswap! res conj "N10")))
          (aor/node "N1" "N2"
            (fn [agent-node]
              (vswap! res conj "N1")))
          (aor/agg-node "N2" nil aggs/+sum
            (fn [agent-node agg node-start-res]
              (vswap! res conj "N2")))
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
  (letlocals
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/node "N1" ["A1" "B1"] (fn [agent-node] ))
          (aor/node "A1" "A2" (fn [agent-node] ))
          (aor/node "A2" ["A3" "A4"] (fn [agent-node] ))
          (aor/node "A3" nil (fn [agent-node] ))
          (aor/node "A4" nil (fn [agent-node] ))

          (aor/node "B1" ["B2" "B3"] (fn [agent-node] ))
          (aor/agg-start-node "B2" "B4" (fn [agent-node] ))
          (aor/agg-node "B4" nil aggs/+sum (fn [agent-node agg node-start-res] ))
          (aor/node "B3" nil (fn [agent-node] ))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (is (= "N1" (:start-node graph)))
    (is (some? (:uuid graph)))
    (is (some? (java.util.UUID/fromString (:uuid graph))))
    (bind node-map (:node-map graph))
    (is (= #{"N1" "A1" "A2" "A3" "A4" "B1" "B2" "B3" "B4"} (-> node-map keys set)))
    (let [node (get node-map "N1")]
      (is (= #{"A1" "B1"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A1")]
      (is (= #{"A2"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A2")]
      (is (= #{"A3" "A4"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A3")]
      (is (= #{} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A4")]
      (is (= #{} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "B1")]
      (is (= #{"B2" "B3"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "B2")]
      (is (= #{"B4"} (:output-nodes node)))
      (is (nil? (:agg-context node)))
      (is (= "B4" (-> node :node :agg-node-name))))
    (let [node (get node-map "B4")]
      (is (= #{} (:output-nodes node)))
      (is (= "B2" (:agg-context node))))
    (let [node (get node-map "B3")]
      (is (= #{} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    ))

(deftest looping-graph-test
  (letlocals
    (bind ag
      (-> (i/mk-agent-graph)
          (aor/node "N1" ["A1" "B1"] (fn [agent-node] ))
          (aor/node "A1" "A2" (fn [agent-node] ))
          (aor/node "A2" "A3" (fn [agent-node] ))
          (aor/node "A3" ["A1" "A2"] (fn [agent-node] ))

          (aor/agg-start-node "B1" "B2" (fn [agent-node] ))
          (aor/node "B2" "B3" (fn [agent-node] ))
          (aor/node "B3" ["B2" "B4"] (fn [agent-node] ))
          (aor/agg-node "B4" "B1" aggs/+sum (fn [agent-node agg node-start-res] ))
          ))
    (bind graph (i/resolve-agent-graph ag))
    (is (= "N1" (:start-node graph)))
    (is (some? (:uuid graph)))
    (is (some? (java.util.UUID/fromString (:uuid graph))))
    (bind node-map (:node-map graph))
    (is (= #{"N1" "A1" "A2" "A3" "B1" "B2" "B3" "B4"} (-> node-map keys set)))
    (let [node (get node-map "N1")]
      (is (= #{"A1" "B1"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A1")]
      (is (= #{"A2"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A2")]
      (is (= #{"A3"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "A3")]
      (is (= #{"A1" "A2"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "B1")]
      (is (= #{"B2"} (:output-nodes node)))
      (is (nil? (:agg-context node))))
    (let [node (get node-map "B2")]
      (is (= #{"B3"} (:output-nodes node)))
      (is (= "B1" (:agg-context node))))
    (let [node (get node-map "B3")]
      (is (= #{"B2" "B4"} (:output-nodes node)))
      (is (= "B1" (:agg-context node))))
    (let [node (get node-map "B4")]
      (is (= #{"B1"} (:output-nodes node)))
      (is (= "B1" (:agg-context node))))
  ))

(deftest graph-error-cases
  (ex-info-thrown? #"Undefined node.*" {:node "N2" :path ["N1"]}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/node "N1" "N2" (fn [agent-node] ))
          )))
  (ex-info-thrown? #"No corresponding agg node.*" {:start-agg-node "N1"}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "N1" nil (fn [agent-node] ))
          )))
  (ex-info-thrown? #"Invalid loop to different agg context.*" {:agg1 "N1" :agg2 nil :node "N1" :path ["N1" "N2"]}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "N1" "N2" (fn [agent-node] ))
          (aor/node "N2" ["N1" "N3"] (fn [agent-node] ))
          (aor/agg-node "N3" nil aggs/+sum (fn [agent-node agg node-start-res] ))
          )))
  (ex-info-thrown? #"Invalid loop to different agg context.*" {:agg1 "N1" :agg2 "A1" :node "N1" :path ["A1" "N1" "N2"]}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "A1" "N1" (fn [agent-node] ))
          (aor/agg-start-node "N1" "N2" (fn [agent-node] ))
          (aor/node "N2" ["N1" "N3"] (fn [agent-node] ))
          (aor/agg-node "N3" "A2" aggs/+sum (fn [agent-node agg node-start-res] ))
          (aor/agg-node "A2" nil aggs/+sum (fn [agent-node agg node-start-res] ))
          )))
  (ex-info-thrown? #"Reached AggNode outside of agg context.*" {:name "N1" :path []}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/agg-node "N1" nil aggs/+sum (fn [agent-node agg node-start-res] ))
          )))
  (ex-info-thrown? #"Invalid loop to different agg context.*" {:agg1 nil :agg2 "C1" :node "N3" :path ["N1" "N2"]}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/node "N1" ["C1" "N2"] (fn [agent-node] ))
          (aor/agg-start-node "C1" "N3" (fn [agent-node agg node-start-res] ))
          (aor/node "N3" "N4" (fn [agent-node] ))
          (aor/agg-node "N4" nil aggs/+sum (fn [agent-node agg node-start-res] ))

          (aor/node "N2" "N3" (fn [agent-node] ))
          )))
  (ex-info-thrown? #"Invalid loop to different agg context.*" {:agg1 nil :agg2 "N1" :node "N2" :path ["N1" "N2"]}
    (i/resolve-agent-graph
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "N1" "N2" (fn [agent-node] ))
          (aor/agg-node "N2" "N2" aggs/+sum (fn [agent-node agg node-start-res] ))
          )))
    )

(deftest agg-types-test
  (letlocals
    (bind get-agg-node
      (fn [agg]
        (-> (i/mk-agent-graph)
            (aor/agg-start-node "N1" "N2" (fn [agent-node] ))
            (aor/agg-node "N2" nil agg (fn [agent-node] ))
            i/resolve-agent-graph
            :node-map
            (get "N2")
            :node)
      ))

    (bind jaccum1
      (reify RamaAccumulatorAgg2
        (initVal [this] 10)
        (accumulate [this val arg1 arg2]
          (* arg2 (+ val arg1)))))

    (bind jaccum2
      (reify RamaAccumulatorAgg0
        (initVal [this] 11)
        (accumulate [this val]
          (* val 2))))

    (bind jcombiner
      (reify RamaCombinerAgg
        (zeroVal [this] 99)
        (combine [this val1 val2]
          (inc (* val1 val2)))))

    (bind node (get-agg-node aggs/+sum))
    (is (= 0 ((:init-fn node))))
    (is (= 11 ((:update-fn node) 3 8)))

    (bind node (get-agg-node aggs/+vec-agg))
    (is (= [] ((:init-fn node))))
    (is (= [1 2 5] ((:update-fn node) [1 2] 5)))

    (bind node (get-agg-node BuiltIn/SUM_AGG))
    (is (= 0 ((:init-fn node))))
    (is (= 23 ((:update-fn node) 11 12)))

    (bind node (get-agg-node jaccum1))
    (is (= 10 ((:init-fn node))))
    (is (= 35 ((:update-fn node) 3 4 5)))

    (bind node (get-agg-node jaccum2))
    (is (= 11 ((:init-fn node))))
    (is (= 200 ((:update-fn node) 100)))

    (bind node (get-agg-node jcombiner))
    (is (= 99 ((:init-fn node))))
    (is (= 13 ((:update-fn node) 3 4)))

    (bind node
      (get-agg-node
        (aor/multi-agg
          (init [] "10")
          (on "abc" [curr a b]
            (str curr "-" a "-" b))
          (on "def" [curr a]
            (str curr "!" a )))))
    (is (= "10" ((:init-fn node))))
    (is (= "111-1-2" ((:update-fn node) "111" "abc" 1 2)))
    (is (= "111!3" ((:update-fn node) "111" "def" 3)))
    (ex-info-thrown? #"Invalid dispatch name for MultiAgg.*" {:valid-names ["abc" "def"] :name "not-a-dispatch"}
      ((:update-fn node) "111" "not-a-dispatch"))
    (is (thrown? clojure.lang.ArityException
          ((:update-fn node) "111" "abc" 1 2 3)))
    (is (thrown? clojure.lang.ArityException
          ((:update-fn node) "111" "abc" 1)))

    (bind node
      (get-agg-node
        (aor/multi-agg
          (on "a" [curr a b]
            (str curr "-" a "-" b)))))
    (is (nil? ((:init-fn node))))
    (is (= "111-1-2" ((:update-fn node) "111" "a" 1 2)))
  ))

(deftest multi-agg-errors-test
  (ex-info-thrown? #"MultiAgg already has init function specified.*" {}
    (aor/multi-agg
      (init [] "10")
      (init [] "1")
      (on "abc" [curr a b]
        (str curr "-" a "-" b))))
  (ex-info-thrown? #"MultiAgg already has handler for given name.*" {:name "abc"}
    (aor/multi-agg
      (init [] "1")
      (on "abc" [curr a b] curr)
      (on "abc" [curr a b] curr)))
  (try
    (eval
      `(aor/multi-agg
        (~'init [~'this] "1")
        (~'on "abc" [curr a b] curr)))
    (is false)
    (catch clojure.lang.Compiler$CompilerException e
      (let [e (ex-cause e)]
        (is (re-matches #"Invalid binding vector for MultiAgg init.*" (ex-message e)))
        (is (= (ex-data e) {:bindings ['this] :required []}))
        )))
  )

(deftest graph->historical-graph-info-test
  (letlocals
    (bind graph
      (-> (i/mk-agent-graph)
          (aor/agg-start-node "N1" "N2" (fn [agent-node] ))
          (aor/node "N2" "N3" (fn [agent-node a]))
          (aor/agg-node "N3" nil aggs/+sum (fn [agent-node] ))
          i/resolve-agent-graph))
    (bind historical
      (#'i/graph->historical-graph-info graph))

    (is (= historical
          (aor-types/->HistoricalAgentGraphInfo
            {"N1" (aor-types/->HistoricalAgentNodeInfo :agg-start-node #{"N2"} nil)
             "N2" (aor-types/->HistoricalAgentNodeInfo :node #{"N3"} "N1")
             "N3" (aor-types/->HistoricalAgentNodeInfo :agg-node #{} "N1")}
            (:start-node graph)
            (:uuid graph)
            )))
    ))

(deftest built-ins-test
  (is (identical? aggs/+and (.agg BuiltIn/AND_AGG)))
  (is (identical? aggs/+first (.agg BuiltIn/FIRST_AGG)))
  (is (identical? aggs/+last (.agg BuiltIn/LAST_AGG)))
  (is (identical? aggs/+vec-agg (.agg BuiltIn/LIST_AGG)))
  (is (identical? aggs/+map-agg (.agg BuiltIn/MAP_AGG)))
  (is (identical? aggs/+max (.agg BuiltIn/MAX_AGG)))
  (is (identical? aggs/+merge (.agg BuiltIn/MERGE_MAP_AGG)))
  (is (identical? aggs/+min (.agg BuiltIn/MIN_AGG)))
  (is (identical? aggs/+multi-set-agg (.agg BuiltIn/MULTI_SET_AGG)))
  (is (identical? aggs/+or (.agg BuiltIn/OR_AGG)))
  (is (identical? aggs/+set-agg (.agg BuiltIn/SET_AGG)))
  (is (identical? aggs/+sum (.agg BuiltIn/SUM_AGG))))

(deftest graph-versioning-test
  (let [task-counts-atom (atom {})]
    (with-redefs [i/hook:finding-graph-version
                  (fn [task-id]
                    (swap! task-counts-atom
                            #(transform [(keypath task-id) (nil->val 0)]
                                       inc
                                       %)))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
          (bind module
            (aor/agentmodule {:module-name "foo-module"} [topology]
              (-> topology
                  (aor/new-agent "foo")
                  (aor/node "start" "abc"
                    (fn [agent-node arg]
                      (aor/emit! agent-node "abc" (str arg "!"))
                      ))
                  (aor/agg-start-node "abc" "agg"
                    (fn [agent-node arg]
                      (dotimes [_ 3]
                        (aor/emit! agent-node "agg" 1))
                      (str arg "?")))
                  (aor/agg-node "agg" nil aggs/+sum
                    (fn [agent-node agg node-start-res]
                      (aor/result! agent-node [agg node-start-res])))
                  )))
          (rtest/launch-module! ipc module {:tasks 4 :threads 2})
          (bind module-name (get-module-name module))
          (bind depot (foreign-depot ipc module-name (i/agent-depot-task-global-name "foo")))
          (bind invokes-pstate (foreign-pstate ipc module-name (i/agent-invoke-task-global-name "foo")))
          (bind graph-history-pstate (foreign-pstate ipc module-name (i/graph-history-task-global-name "foo")))

          (dotimes [_ 10]
            (let [{[graph-task-id graph-id] "_agents-topology-core"}
                  (foreign-append! depot (aor-types/->AgentInvoke ["hello"] 0))]
              (is (= 0 (foreign-select-one [(keypath graph-id) :graph-version]
                                           invokes-pstate
                                           {:pkey graph-task-id})))))
          (is (-> @task-counts-atom empty? not))
          (doseq [[_ v] @task-counts-atom]
            (is (= 1 v)))

          (is (= [0] (foreign-select MAP-KEYS graph-history-pstate {:pkey 0})))
          (bind hgraph (foreign-select-one (keypath 0) graph-history-pstate {:pkey 0}))

          (is (some? (:uuid hgraph)))
          (bind graph-history1
            (aor-types/->HistoricalAgentGraphInfo
              {"start" (aor-types/->HistoricalAgentNodeInfo :node #{"abc"} nil)
               "abc" (aor-types/->HistoricalAgentNodeInfo :agg-start-node #{"agg"} nil)
               "agg" (aor-types/->HistoricalAgentNodeInfo :agg-node #{} "abc")}
              "start"
              (:uuid hgraph)))
          (is (= hgraph graph-history1))

          (bind module2
            (aor/agentmodule {:module-name "foo-module"} [topology]
              (-> topology
                  (aor/new-agent "foo")
                  (aor/node "start" nil
                    (fn [agent-node]
                      (aor/result! agent-node "done")))
                  )))

          (rtest/update-module! ipc module2)

          (reset! task-counts-atom {})
          (dotimes [_ 10]
            (let [{[graph-task-id graph-id] "_agents-topology-core"}
                  (foreign-append! depot (aor-types/->AgentInvoke [] 0))]
              (is (= 1 (foreign-select-one [(keypath graph-id) :graph-version]
                                           invokes-pstate
                                           {:pkey graph-task-id})))))
          (is (-> @task-counts-atom empty? not))
          (doseq [[_ v] @task-counts-atom]
            (is (= 1 v)))

          (is (= [0 1] (foreign-select MAP-KEYS graph-history-pstate {:pkey 0})))
          (bind hgraph1 (foreign-select-one (keypath 0) graph-history-pstate {:pkey 0}))
          (bind hgraph2 (foreign-select-one (keypath 1) graph-history-pstate {:pkey 0}))

          (is (not= (:uuid hgraph1) (:uuid hgraph2)))

          (bind graph-history2
            (aor-types/->HistoricalAgentGraphInfo
              {"start" (aor-types/->HistoricalAgentNodeInfo :node #{} nil)}
              "start"
              (:uuid hgraph2)))
          (is (= hgraph1 graph-history1))
          (is (= hgraph2 graph-history2))
          )))))

(deftest async-emits-test
  ;; TODO: <<<<<>>>>>
  ;;  - emit regular CF, PState queries, PState transforms, and out of band
  ;;  - verify order of resolution (perhaps just through results of PState ops)
  ;;  - check what gets passed to the next node (just accumulate into result)
  )

(deftest parallel-execution-test
  ;; TODO: <<<<<>>>>>
  )

(deftest looped-test
  ;; TODO: <<<<<>>>>
  )

(deftest aggs-test
  ;; TODO: <<<<>>>>
  )
