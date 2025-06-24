(ns com.rpl.agent-o-rama-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [clojure.set :as set]
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.store :as store]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.store-impl :as simpl]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.ops :as ops]
   [com.rpl.rama.test :as rtest]
   [loom.attr :as lattr]
   [loom.graph :as lgraph]
   [meander.epsilon :as m])
  (:import
   [com.rpl.agentorama
    AgentInvoke
    BuiltIn
    StreamingChunk]
   [com.rpl.agentorama.impl
    AgentNodeExecutorTaskGlobal]
   [com.rpl.agent_o_rama.impl.types
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.aortest
    EarlySumAccum
    EarlySumCombiner]
   [com.rpl.rama.helpers
    TopologyUtils]
   [com.rpl.rama.ops
    RamaAccumulatorAgg0
    RamaAccumulatorAgg2
    RamaCombinerAgg]
   [java.util.concurrent
    CompletableFuture]))

(deftest graph-test
  (letlocals
   (bind res (volatile! []))
   (bind ag
     (-> (graph/mk-agent-graph)
         (aor/node "N1"
                   "N2"
                   (fn [agent-node]
                     (vswap! res conj "N1")))
         (aor/agg-start-node "N2"
                             "N3"
                             (fn [agent-node]
                               (vswap! res conj "N2")))
         (aor/node "N3"
                   "N4"
                   (fn [agent-node arg1]
                     (vswap! res conj "N3")))
         (aor/agg-node "N4"
                       nil
                       aggs/+sum
                       (fn [agent-node agg node-start-res]
                         (vswap! res conj "N4")))
     ))
   (bind graph (graph/resolve-agent-graph ag))
   (is (= "N1" (:start-node graph)))
   (is (some? (:uuid graph)))
   (is (some? (java.util.UUID/fromString (:uuid graph))))
   (bind node-map (:node-map graph))
   (is (= #{"N1" "N2" "N3" "N4"}
          (-> node-map
              keys
              set)))
   (let [node (get node-map "N1")]
     (is (= #{"N2"} (:output-nodes node)))
     (is (nil? (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N1"] @res))
     (vreset! res []))
   (let [node (get node-map "N2")]
     (is (= #{"N3"} (:output-nodes node)))
     (is (nil? (:agg-context node)))
     (is (instance? NodeAggStart (:node node)))
     (is (= "N4"
            (-> node
                :node
                :agg-node-name)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N2"] @res))
     (vreset! res []))
   (let [node (get node-map "N3")]
     (is (= #{"N4"} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil
      1)
     (is (= ["N3"] @res))
     (vreset! res []))
   (let [node (get node-map "N4")]
     (is (= #{} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? NodeAgg (:node node)))
     (is (= 0
            ((-> node
                 :node
                 :init-fn))))
     (is (= 14
            ((-> node
                 :node
                 :update-fn)
             3
             11)))
     ((-> node
          :node
          :node-fn)
      nil
      nil
      nil)
     (is (= ["N4"] @res))
     (vreset! res []))


   ;; test nested aggs
   (bind ag
     (-> (graph/mk-agent-graph)
         (aor/node "N1"
                   "N2"
                   (fn [agent-node]
                     (vswap! res conj "N1")))
         (aor/agg-start-node "N2"
                             "N3"
                             (fn [agent-node]
                               (vswap! res conj "N2")))
         (aor/node "N3"
                   "N4"
                   (fn [agent-node arg1]
                     (vswap! res conj "N3")))
         (aor/agg-start-node "N4"
                             "N5"
                             (fn [agent-node]
                               (vswap! res conj "N4")))
         (aor/agg-node "N5"
                       "N6"
                       aggs/+sum
                       (fn [agent-node agg node-start-res]
                         (vswap! res conj "N5")))
         (aor/agg-start-node "N6"
                             "N7"
                             (fn [agent-node]
                               (vswap! res conj "N6")))
         (aor/node "N7"
                   "N8"
                   (fn [agent-node]
                     (vswap! res conj "N7")))
         (aor/agg-node "N8"
                       "N9"
                       aggs/+vec-agg
                       (fn [agent-node agg node-start-res]
                         (vswap! res conj "N8")))
         (aor/agg-node "N9"
                       nil
                       aggs/+sum
                       (fn [agent-node agg node-start-res]
                         (vswap! res conj "N9")))
     ))
   (bind graph (graph/resolve-agent-graph ag))
   (is (= "N1" (:start-node graph)))
   (is (some? (:uuid graph)))
   (is (some? (java.util.UUID/fromString (:uuid graph))))
   (bind node-map (:node-map graph))
   (is (= #{"N1" "N2" "N3" "N4" "N5" "N6" "N7" "N8" "N9"}
          (-> node-map
              keys
              set)))
   (let [node (get node-map "N1")]
     (is (= #{"N2"} (:output-nodes node)))
     (is (nil? (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N1"] @res))
     (vreset! res []))
   (let [node (get node-map "N2")]
     (is (= #{"N3"} (:output-nodes node)))
     (is (nil? (:agg-context node)))
     (is (instance? NodeAggStart (:node node)))
     (is (= "N9"
            (-> node
                :node
                :agg-node-name)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N2"] @res))
     (vreset! res []))
   (let [node (get node-map "N3")]
     (is (= #{"N4"} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil
      1)
     (is (= ["N3"] @res))
     (vreset! res []))
   (let [node (get node-map "N4")]
     (is (= #{"N5"} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? NodeAggStart (:node node)))
     (is (= "N5"
            (-> node
                :node
                :agg-node-name)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N4"] @res))
     (vreset! res []))
   (let [node (get node-map "N5")]
     (is (= #{"N6"} (:output-nodes node)))
     (is (= "N4" (:agg-context node)))
     (is (instance? NodeAgg (:node node)))
     (is (= 0
            ((-> node
                 :node
                 :init-fn))))
     (is (= 14
            ((-> node
                 :node
                 :update-fn)
             3
             11)))
     ((-> node
          :node
          :node-fn)
      nil
      nil
      nil)
     (is (= ["N5"] @res))
     (vreset! res []))
   (let [node (get node-map "N6")]
     (is (= #{"N7"} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? NodeAggStart (:node node)))
     (is (= "N8"
            (-> node
                :node
                :agg-node-name)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N6"] @res))
     (vreset! res []))
   (let [node (get node-map "N7")]
     (is (= #{"N8"} (:output-nodes node)))
     (is (= "N6" (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N7"] @res))
     (vreset! res []))
   (let [node (get node-map "N8")]
     (is (= #{"N9"} (:output-nodes node)))
     (is (= "N6" (:agg-context node)))
     (is (instance? NodeAgg (:node node)))
     (is (= []
            ((-> node
                 :node
                 :init-fn))))
     (is (= [1 2 3]
            ((-> node
                 :node
                 :update-fn)
             [1 2]
             3)))
     ((-> node
          :node
          :node-fn)
      nil
      nil
      nil)
     (is (= ["N8"] @res))
     (vreset! res []))
   (let [node (get node-map "N9")]
     (is (= #{} (:output-nodes node)))
     (is (= "N2" (:agg-context node)))
     (is (instance? NodeAgg (:node node)))
     (is (= 0
            ((-> node
                 :node
                 :init-fn))))
     (is (= 12
            ((-> node
                 :node
                 :update-fn)
             5
             7)))
     ((-> node
          :node
          :node-fn)
      nil
      nil
      nil)
     (is (= ["N9"] @res))
     (vreset! res []))

   ;; starting with aggStartNode
   (bind ag
     (-> (graph/mk-agent-graph)
         (aor/agg-start-node "N10"
                             "N1"
                             (fn [agent-node arg1 arg2 arg3]
                               (vswap! res conj "N10")))
         (aor/node "N1"
                   "N2"
                   (fn [agent-node]
                     (vswap! res conj "N1")))
         (aor/agg-node "N2"
                       nil
                       aggs/+sum
                       (fn [agent-node agg node-start-res]
                         (vswap! res conj "N2")))
     ))
   (bind graph (graph/resolve-agent-graph ag))
   (is (= "N10" (:start-node graph)))
   (is (some? (:uuid graph)))
   (is (some? (java.util.UUID/fromString (:uuid graph))))
   (bind node-map (:node-map graph))
   (is (= #{"N1" "N2" "N10"}
          (-> node-map
              keys
              set)))
   (let [node (get node-map "N10")]
     (is (= #{"N1"} (:output-nodes node)))
     (is (nil? (:agg-context node)))
     (is (instance? NodeAggStart (:node node)))
     (is (= "N2"
            (-> node
                :node
                :agg-node-name)))
     ((-> node
          :node
          :node-fn)
      nil
      1
      2
      3)
     (is (= ["N10"] @res))
     (vreset! res []))
   (let [node (get node-map "N1")]
     (is (= #{"N2"} (:output-nodes node)))
     (is (= "N10" (:agg-context node)))
     (is (instance? Node (:node node)))
     ((-> node
          :node
          :node-fn)
      nil)
     (is (= ["N1"] @res))
     (vreset! res []))
   (let [node (get node-map "N2")]
     (is (= #{} (:output-nodes node)))
     (is (= "N10" (:agg-context node)))
     (is (instance? NodeAgg (:node node)))
     (is (= 0
            ((-> node
                 :node
                 :init-fn))))
     (is (= 14
            ((-> node
                 :node
                 :update-fn)
             3
             11)))
     ((-> node
          :node
          :node-fn)
      nil
      nil
      nil)
     (is (= ["N2"] @res))
     (vreset! res []))
  ))

(deftest branching-graph-test
  (letlocals
   (bind ag
     (-> (graph/mk-agent-graph)
         (aor/node "N1" ["A1" "B1"] (fn [agent-node]))
         (aor/node "A1" "A2" (fn [agent-node]))
         (aor/node "A2" ["A3" "A4"] (fn [agent-node]))
         (aor/node "A3" nil (fn [agent-node]))
         (aor/node "A4" nil (fn [agent-node]))

         (aor/node "B1" ["B2" "B3"] (fn [agent-node]))
         (aor/agg-start-node "B2" "B4" (fn [agent-node]))
         (aor/agg-node "B4" nil aggs/+sum (fn [agent-node agg node-start-res]))
         (aor/node "B3" nil (fn [agent-node]))
     ))
   (bind graph (graph/resolve-agent-graph ag))
   (is (= "N1" (:start-node graph)))
   (is (some? (:uuid graph)))
   (is (some? (java.util.UUID/fromString (:uuid graph))))
   (bind node-map (:node-map graph))
   (is (= #{"N1" "A1" "A2" "A3" "A4" "B1" "B2" "B3" "B4"}
          (-> node-map
              keys
              set)))
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
     (is (= "B4"
            (-> node
                :node
                :agg-node-name))))
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
     (-> (graph/mk-agent-graph)
         (aor/node "N1" ["A1" "B1"] (fn [agent-node]))
         (aor/node "A1" "A2" (fn [agent-node]))
         (aor/node "A2" "A3" (fn [agent-node]))
         (aor/node "A3" ["A1" "A2"] (fn [agent-node]))

         (aor/agg-start-node "B1" "B2" (fn [agent-node]))
         (aor/node "B2" "B3" (fn [agent-node]))
         (aor/node "B3" ["B2" "B4"] (fn [agent-node]))
         (aor/agg-node "B4" "B1" aggs/+sum (fn [agent-node agg node-start-res]))
     ))
   (bind graph (graph/resolve-agent-graph ag))
   (is (= "N1" (:start-node graph)))
   (is (some? (:uuid graph)))
   (is (some? (java.util.UUID/fromString (:uuid graph))))
   (bind node-map (:node-map graph))
   (is (= #{"N1" "A1" "A2" "A3" "B1" "B2" "B3" "B4"}
          (-> node-map
              keys
              set)))
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
  (ex-info-thrown? #"Undefined node.*"
                   {:node "N2" :path ["N1"]}
                   (graph/resolve-agent-graph
                    (-> (graph/mk-agent-graph)
                        (aor/node "N1" "N2" (fn [agent-node]))
                    )))
  (ex-info-thrown? #"No corresponding agg node.*"
                   {:start-agg-node "N1"}
                   (graph/resolve-agent-graph
                    (-> (graph/mk-agent-graph)
                        (aor/agg-start-node "N1" nil (fn [agent-node]))
                    )))
  (ex-info-thrown?
   #"Invalid loop to different agg context.*"
   {:agg1 "N1" :agg2 nil :node "N1" :path ["N1" "N2"]}
   (graph/resolve-agent-graph
    (-> (graph/mk-agent-graph)
        (aor/agg-start-node "N1" "N2" (fn [agent-node]))
        (aor/node "N2" ["N1" "N3"] (fn [agent-node]))
        (aor/agg-node "N3" nil aggs/+sum (fn [agent-node agg node-start-res]))
    )))
  (ex-info-thrown?
   #"Invalid loop to different agg context.*"
   {:agg1 "N1" :agg2 "A1" :node "N1" :path ["A1" "N1" "N2"]}
   (graph/resolve-agent-graph
    (-> (graph/mk-agent-graph)
        (aor/agg-start-node "A1" "N1" (fn [agent-node]))
        (aor/agg-start-node "N1" "N2" (fn [agent-node]))
        (aor/node "N2" ["N1" "N3"] (fn [agent-node]))
        (aor/agg-node "N3" "A2" aggs/+sum (fn [agent-node agg node-start-res]))
        (aor/agg-node "A2" nil aggs/+sum (fn [agent-node agg node-start-res]))
    )))
  (ex-info-thrown?
   #"Reached AggNode outside of agg context.*"
   {:name "N1" :path []}
   (graph/resolve-agent-graph
    (-> (graph/mk-agent-graph)
        (aor/agg-node "N1" nil aggs/+sum (fn [agent-node agg node-start-res]))
    )))
  (ex-info-thrown?
   #"Invalid loop to different agg context.*"
   {:agg1 nil :agg2 "C1" :node "N3" :path ["N1" "N2"]}
   (graph/resolve-agent-graph
    (-> (graph/mk-agent-graph)
        (aor/node "N1" ["C1" "N2"] (fn [agent-node]))
        (aor/agg-start-node "C1" "N3" (fn [agent-node agg node-start-res]))
        (aor/node "N3" "N4" (fn [agent-node]))
        (aor/agg-node "N4" nil aggs/+sum (fn [agent-node agg node-start-res]))

        (aor/node "N2" "N3" (fn [agent-node]))
    )))
  (ex-info-thrown?
   #"Invalid loop to different agg context.*"
   {:agg1 nil :agg2 "N1" :node "N2" :path ["N1" "N2"]}
   (graph/resolve-agent-graph
    (-> (graph/mk-agent-graph)
        (aor/agg-start-node "N1" "N2" (fn [agent-node]))
        (aor/agg-node "N2" "N2" aggs/+sum (fn [agent-node agg node-start-res]))
    )))
)

(deftest agg-types-test
  (letlocals
   (bind get-agg-node
     (fn [agg]
       (-> (graph/mk-agent-graph)
           (aor/agg-start-node "N1" "N2" (fn [agent-node]))
           (aor/agg-node "N2" nil agg (fn [agent-node]))
           graph/resolve-agent-graph
           :node-map
           (get "N2")
           :node)
     ))

   (bind jaccum1
     (reify
      RamaAccumulatorAgg2
      (initVal [this] 10)
      (accumulate [this val arg1 arg2]
        (* arg2 (+ val arg1)))))

   (bind jaccum2
     (reify
      RamaAccumulatorAgg0
      (initVal [this] 11)
      (accumulate [this val]
        (* val 2))))

   (bind jcombiner
     (reify
      RamaCombinerAgg
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
       (on "abc"
           [curr a b]
           (str curr "-" a "-" b))
       (on "def"
           [curr a]
           (str curr "!" a)))))
   (is (= "10" ((:init-fn node))))
   (is (= "111-1-2" ((:update-fn node) "111" "abc" 1 2)))
   (is (= "111!3" ((:update-fn node) "111" "def" 3)))
   (ex-info-thrown? #"Invalid dispatch name for MultiAgg.*"
                    {:valid-names ["abc" "def"] :name "not-a-dispatch"}
                    ((:update-fn node) "111" "not-a-dispatch"))
   (is (thrown? clojure.lang.ArityException
                ((:update-fn node) "111" "abc" 1 2 3)))
   (is (thrown? clojure.lang.ArityException
                ((:update-fn node) "111" "abc" 1)))

   (bind node
     (get-agg-node
      (aor/multi-agg
       (on "a"
           [curr a b]
           (str curr "-" a "-" b)))))
   (is (nil? ((:init-fn node))))
   (is (= "111-1-2" ((:update-fn node) "111" "a" 1 2)))
  ))

(deftest multi-agg-errors-test
  (ex-info-thrown? #"MultiAgg already has init function specified.*"
                   {}
                   (aor/multi-agg
                    (init [] "10")
                    (init [] "1")
                    (on "abc"
                        [curr a b]
                        (str curr "-" a "-" b))))
  (ex-info-thrown? #"MultiAgg already has handler for given name.*"
                   {:name "abc"}
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
        (is (re-matches #"Invalid binding vector for MultiAgg init.*"
                        (ex-message e)))
        (is (= (ex-data e) {:bindings ['this] :required []}))
      )))
)

(deftest graph->historical-graph-info-test
  (letlocals
   (bind graph
     (-> (graph/mk-agent-graph)
         (aor/agg-start-node "N1" "N2" (fn [agent-node]))
         (aor/node "N2" "N3" (fn [agent-node a]))
         (aor/agg-node "N3" nil aggs/+sum (fn [agent-node]))
         graph/resolve-agent-graph))
   (bind historical
     (graph/graph->historical-graph-info graph))

   (is
    (= historical
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
           (aor/agentmodule
            {:module-name "foo-module"}
            [topology]
            (-> topology
                (aor/new-agent "foo")
                (aor/node "start"
                          "abc"
                          (fn [agent-node arg]
                            (aor/emit! agent-node "abc" (str arg "!"))
                          ))
                (aor/agg-start-node "abc"
                                    "agg"
                                    (fn [agent-node arg]
                                      (dotimes [_ 3]
                                        (aor/emit! agent-node "agg" 1))
                                      (str arg "?")))
                (aor/agg-node "agg"
                              nil
                              aggs/+sum
                              (fn [agent-node agg node-start-res]
                                (aor/result! agent-node [agg node-start-res])))
            )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind depot
           (foreign-depot ipc
                          module-name
                          (po/agent-depot-name "foo")))
         (bind invokes-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-invoke-task-global-name "foo")))
         (bind graph-history-pstate
           (foreign-pstate ipc
                           module-name
                           (po/graph-history-task-global-name "foo")))

         (dotimes [_ 10]
           (let [{[graph-task-id graph-id] "_agents-topology"}
                 (foreign-append! depot
                                  (aor-types/->AgentInvoke ["hello"] 0 nil))]
             (is (= 0
                    (foreign-select-one [(keypath graph-id) :graph-version]
                                        invokes-pstate
                                        {:pkey graph-task-id})))))
         (is (-> @task-counts-atom
                 empty?
                 not))
         (doseq [[_ v] @task-counts-atom]
           (is (= 1 v)))

         (is (= [0] (foreign-select MAP-KEYS graph-history-pstate {:pkey 0})))
         (bind hgraph
           (foreign-select-one (keypath 0) graph-history-pstate {:pkey 0}))

         (is (some? (:uuid hgraph)))
         (bind graph-history1
           (aor-types/->HistoricalAgentGraphInfo
            {"start" (aor-types/->HistoricalAgentNodeInfo :node #{"abc"} nil)
             "abc"   (aor-types/->HistoricalAgentNodeInfo :agg-start-node
                                                          #{"agg"}
                                                          nil)
             "agg"   (aor-types/->HistoricalAgentNodeInfo :agg-node #{} "abc")}
            "start"
            (:uuid hgraph)))
         (is (= hgraph graph-history1))

         (bind module2
           (aor/agentmodule {:module-name "foo-module"}
                            [topology]
                            (-> topology
                                (aor/new-agent "foo")
                                (aor/node "start"
                                          nil
                                          (fn [agent-node]
                                            (aor/result! agent-node "done")))
                            )))

         (rtest/update-module! ipc module2)

         (reset! task-counts-atom {})
         (dotimes [_ 10]
           (let [{[graph-task-id graph-id] "_agents-topology"}
                 (foreign-append! depot (aor-types/->AgentInvoke [] 0 nil))]
             (is (= 1
                    (foreign-select-one [(keypath graph-id) :graph-version]
                                        invokes-pstate
                                        {:pkey graph-task-id})))))
         (is (-> @task-counts-atom
                 empty?
                 not))
         (doseq [[_ v] @task-counts-atom]
           (is (= 1 v)))

         (is (= [0 1] (foreign-select MAP-KEYS graph-history-pstate {:pkey 0})))
         (bind hgraph1
           (foreign-select-one (keypath 0) graph-history-pstate {:pkey 0}))
         (bind hgraph2
           (foreign-select-one (keypath 1) graph-history-pstate {:pkey 0}))

         (is (not= (:uuid hgraph1) (:uuid hgraph2)))

         (bind graph-history2
           (aor-types/->HistoricalAgentGraphInfo
            {"start" (aor-types/->HistoricalAgentNodeInfo :node #{} nil)}
            "start"
            (:uuid hgraph2)))
         (is (= hgraph1 graph-history1))
         (is (= hgraph2 graph-history2))
        )))))

(deftest finish-test
  (let [results-atom (atom [])]
    (with-redefs [i/hook:writing-result
                  (fn [graph-task-id graph-id result]
                    (swap! results-atom conj result)
                  )]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (->
              topology
              (aor/new-agent "foo")
              (aor/node "start"
                        ["node1" "node2" "node3"]
                        (fn [agent-node arg]
                          (cond (= arg :regular)
                                (aor/emit! agent-node "node1")

                                (= arg :halt)
                                (aor/emit! agent-node "node2")

                                :else
                                (aor/emit! agent-node "node3")
                          )
                        ))
              (aor/node "node1"
                        nil
                        (fn [agent-node]
                          (aor/result! agent-node "result1")
                        ))
              (aor/node "node2"
                        nil
                        (fn [agent-node]
                        ))
              (aor/node "node3"
                        ["node2" "node4" "node5"]
                        (fn [agent-node]
                          ;; this makes the node4 and node5 emits happen on
                          ;; different tasks
                          (aor/emit! agent-node "node2")
                          (aor/emit! agent-node "node4")
                          (aor/emit! agent-node "node5")
                        ))
              (aor/node "node4"
                        nil
                        (fn [agent-node]
                          (aor/result! agent-node "result2")
                        ))
              (aor/node "node5"
                        nil
                        (fn [agent-node]
                          (aor/result! agent-node "result3")
                        ))
            )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind depot
           (foreign-depot ipc
                          module-name
                          (po/agent-depot-name "foo")))
         (bind invokes-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-invoke-task-global-name "foo")))

         (is (= (aor-types/->AgentResult "result1" false)
                (invoke-agent-and-return! depot invokes-pstate [:regular])))
         (is (= (aor-types/->AgentResult "Agent completed without result" true)
                (invoke-agent-and-return! depot invokes-pstate [:halt])))

         (reset! results-atom [])
         (bind res (invoke-agent-and-return! depot invokes-pstate [:fork]))
         (is (or (= (aor-types/->AgentResult "result2" false) res)
                 (= (aor-types/->AgentResult "result3" false) res)))
         (is (= (first @results-atom) res))
        )))))

(deftest multiple-agents-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (->
          topology
          (aor/new-agent "foo")
          (aor/node "start"
                    "node1"
                    (fn [agent-node arg]
                      (aor/emit! agent-node "node1" (inc arg))
                    ))
          (aor/node "node1"
                    nil
                    (fn [agent-node arg]
                      (aor/result! agent-node (* 2 arg))
                    )))
        (->
          topology
          (aor/new-agent "bar")
          (aor/agg-start-node "start"
                              "node1"
                              (fn [agent-node arg]
                                (aor/emit! agent-node "node1" arg)
                                (aor/emit! agent-node "node1" (inc arg))
                                (aor/emit! agent-node "node1" (* 2 arg))
                              ))
          (aor/agg-node "node1"
                        nil
                        aggs/+sum
                        (fn [agent-node agg node-start-res]
                          (aor/result! agent-node agg)))
        )))
     (rtest/launch-module! ipc module {:tasks 1 :threads 1})
     (bind module-name (get-module-name module))
     (bind depot-foo
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind invokes-pstate-foo
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))
     (bind depot-bar
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "bar")))
     (bind invokes-pstate-bar
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "bar")))

     (bind [graph-task-id-foo1 graph-id-foo1]
       (invoke-agent-and-wait! depot-foo invokes-pstate-foo [10]))
     (bind [graph-task-id-foo2 graph-id-foo2]
       (invoke-agent-and-wait! depot-foo invokes-pstate-foo [20]))
     (bind [graph-task-id-bar1 graph-id-bar1]
       (invoke-agent-and-wait! depot-bar invokes-pstate-bar [5]))
     (bind [graph-task-id-bar2 graph-id-bar2]
       (invoke-agent-and-wait! depot-bar invokes-pstate-bar [10]))

     (is (= 22
            (foreign-select-one
             [(keypath graph-id-foo1) :result :val]
             invokes-pstate-foo
             {:pkey graph-task-id-foo1})))
     (is (= 42
            (foreign-select-one
             [(keypath graph-id-foo2) :result :val]
             invokes-pstate-foo
             {:pkey graph-task-id-foo2})))
     (is (= 21
            (foreign-select-one
             [(keypath graph-id-bar1) :result :val]
             invokes-pstate-bar
             {:pkey graph-task-id-bar1})))
     (is (= 41
            (foreign-select-one
             [(keypath graph-id-bar2) :result :val]
             invokes-pstate-bar
             {:pkey graph-task-id-bar2})))
    )))

(deftest stores-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (aor/declare-key-value-store
         topology
         "$$kv"
         clojure.lang.Keyword
         Object)
        (aor/declare-document-store
         topology
         "$$doc"
         clojure.lang.Keyword
         :a Long
         :b java.util.List)
        (aor/declare-pstate-store
         topology
         "$$p"
         {clojure.lang.Keyword (map-schema Long Long {:subindex? true})})
        (->
          topology
          (aor/new-agent "foo")
          (aor/node
           "kv"
           "doc"
           (fn [agent-node arg]
             (let [kv (aor/get-store agent-node "$$kv")
                   b  (store/get kv :b [])]
               (store/put! kv :a arg)
               (store/put! kv :b (conj b arg))
               (store/update! kv :d #(+ (or % 0) arg))
               (store/pstate-transform! [:zz (termval arg)] kv :e)
               (aor/emit! agent-node
                          "doc"
                          arg
                          {:kv
                           {:a (store/get kv :a)
                            :b (store/get kv :b)
                            :c (store/get kv :c)
                            :d (store/get kv :d)
                            :e (store/pstate-select [:b ALL] kv)
                            :f (store/pstate-select-one :a kv)
                            :g [(store/pstate-select :zz kv :e)
                                (store/pstate-select :zz kv)]
                            :h [(store/pstate-select-one :zz kv :e)
                                (store/pstate-select-one :zz kv)]
                            :i (store/contains? kv :a)
                            :j (store/contains? kv :abcde)
                           }})
             )))
          (aor/node
           "doc"
           "pstate"
           (fn [agent-node arg res]
             (let [doc (aor/get-store agent-node "$$doc")
                   s   (store/get doc :s {:a 2 :b [10]})
                   ma  (store/get-document-field doc :m :a 100)
                   mb  (store/get-document-field doc :m :b [])]
               (store/put! doc
                           :s
                           (-> s
                               (update :a inc)
                               (update :b #(conj % arg))))
               (store/update! doc :s #(update % :a (fn [v] (* 2 v))))
               (store/put-document-field! doc :m :a (+ ma 2))
               (store/update-document-field! doc :m :a #(* 2 %))
               (store/put-document-field! doc :m :b (conj mb arg))
               (store/pstate-transform! [:zz (termval {:a arg :b [(inc arg)]})]
                                        doc
                                        :e)
               (aor/emit!
                agent-node
                "pstate"
                arg
                (assoc
                 res
                 :doc {:s     (store/get doc :s)
                       :t     (store/get doc :t)
                       :y     (store/contains? doc :s)
                       :z     (store/contains? doc :abcde)
                       :ma    (store/get-document-field doc :m :a)
                       :mb    (store/get-document-field doc :m :b)
                       :mc    (store/get-document-field doc :m :c)
                       :ma?   (store/contains-document-field? doc :m :a)
                       :mc?   (store/contains-document-field? doc :m :c)
                       :psa   (store/pstate-select-one [:s :a] doc)
                       :psb   (store/pstate-select [:s :b ALL] doc)
                       :pzz   [(store/pstate-select :zz doc :e)
                               (store/pstate-select :zz doc)]
                       :pzz2  [(store/pstate-select-one :zz doc :e)
                               (store/pstate-select-one :zz doc)]
                       :error (try
                                (store/put-document-field! doc :qq :c 1)
                                nil
                                (catch Exception e
                                  (ex-message e)
                                ))
                      }))
             )))
          (aor/node
           "pstate"
           "end"
           (fn [agent-node arg res]
             (let [p (aor/get-store agent-node "$$p")
                   _ (store/pstate-transform! [:a (keypath 0) (nil->val 50)
                                               (term #(+ % arg))]
                                              p
                                              :a)
                   i (store/pstate-select-one [:a LAST FIRST] p)]
               (store/pstate-transform! [:a (keypath (inc i)) (termval arg)]
                                        p
                                        :a)
               (store/pstate-transform! [:zz 0 (termval arg)]
                                        p
                                        :e)
               (aor/emit!
                agent-node
                "end"
                (assoc
                 res
                 :pstate {:ks   (store/pstate-select [:a MAP-KEYS] p)
                          :kv   (store/pstate-select [:a MAP-VALS] p)
                          :k0   (store/pstate-select-one [:a 0] p)
                          :zz0  [(store/pstate-select [:zz 0] p :e)
                                 (store/pstate-select [:zz 0] p)]
                          :zz02 [(store/pstate-select-one [:zz 0] p :e)
                                 (store/pstate-select-one [:zz 0] p)]
                         }))
             )))
          (aor/node "end"
                    nil
                    (fn [agent-node res]
                      (aor/result! agent-node res)
                    ))
        )
       ))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))
     (bind kv
       (foreign-pstate ipc
                       module-name
                       "$$kv"))
     (bind doc
       (foreign-pstate ipc
                       module-name
                       "$$doc"))
     (bind p
       (foreign-pstate ipc
                       module-name
                       "$$p"))

     (is (= {:kv     {:a 3
                      :b [3]
                      :c nil
                      :d 3
                      :e [3]
                      :f 3
                      :g [[3] [nil]]
                      :h [3 nil]
                      :i true
                      :j false}
             :doc    {:s     {:a 6
                              :b [10 3]}
                      :t     nil
                      :y     true
                      :z     false
                      :ma    204
                      :mb    [3]
                      :mc    nil
                      :ma?   true
                      :mc?   false
                      :psa   6
                      :psb   [10 3]
                      :pzz   [[{:a 3 :b [4]}]
                              [nil]]
                      :pzz2  [{:a 3 :b [4]}
                              nil]
                      :error "Invalid key"
                     }
             :pstate {:ks   [0 1]
                      :kv   [53 3]
                      :k0   53
                      :zz0  [[3] [nil]]
                      :zz02 [3 nil]}}
            (:val (invoke-agent-and-return! depot invokes-pstate [3]))))
     (is (= {:kv     {:a 1
                      :b [3 1]
                      :c nil
                      :d 4
                      :e [3 1]
                      :f 1
                      :g [[1] [nil]]
                      :h [1 nil]
                      :i true
                      :j false}
             :doc    {:s     {:a 14
                              :b [10 3 1]}
                      :t     nil
                      :y     true
                      :z     false
                      :ma    412
                      :mb    [3 1]
                      :mc    nil
                      :ma?   true
                      :mc?   false
                      :psa   14
                      :psb   [10 3 1]
                      :pzz   [[{:a 1 :b [2]}]
                              [nil]]
                      :pzz2  [{:a 1 :b [2]}
                              nil]
                      :error "Invalid key"
                     }
             :pstate {:ks   [0 1 2]
                      :kv   [54 3 1]
                      :k0   54
                      :zz0  [[1] [nil]]
                      :zz02 [1 nil]}}
            (:val (invoke-agent-and-return! depot invokes-pstate [1]))))
     (is (= 1 (foreign-select-one :a kv)))
     (is (= [3 1] (foreign-select-one :b kv)))
     (is (= [10 3 1] (foreign-select-one [:s :b] doc)))
     (is (= 54 (foreign-select-one [:a 0] p)))
     (is (= 1 (foreign-select-one [:zz 0] p {:pkey :e})))
    )))

(deftest store-traces-test
  (let [advance-vol (volatile! 1)
        advance-fn  (fn [& args]
                      (TopologyUtils/advanceSimTime @advance-vol)
                      (vswap! advance-vol inc))]
    (with-redefs [simpl/hook:initiating-pstate-write advance-fn
                  simpl/hook:initiating-pstate-query advance-fn]
      (with-open [ipc (rtest/create-ipc)
                  _ (TopologyUtils/startSimTime)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (aor/declare-key-value-store
             topology
             "$$kv"
             clojure.lang.Keyword
             Object)
            (aor/declare-document-store
             topology
             "$$doc"
             clojure.lang.Keyword
             :a Object
             :b Object)
            (aor/declare-pstate-store
             topology
             "$$p"
             {clojure.lang.Keyword Object})
            (->
              topology
              (aor/new-agent "foo")
              (aor/node
               "kv"
               "doc"
               (fn [agent-node]
                 (let [kv (aor/get-store agent-node "$$kv")
                       b  (store/get kv :b [])
                       c  (store/get kv :b)
                       d  (store/contains? kv :a)]
                   (store/put! kv :a 1)
                   (store/update! kv :d str)
                   (aor/emit! agent-node "doc")
                 )))
              (aor/node
               "doc"
               "pstate"
               (fn [agent-node]
                 (let [doc (aor/get-store agent-node "$$doc")
                       ma  (store/get-document-field doc :m :a)
                       mb  (store/get-document-field doc :m :b [])
                       ma? (store/contains-document-field? doc :m :a)]
                   (store/put-document-field! doc :m :a 1)
                   (store/update-document-field! doc :m :a str)
                   (aor/emit! agent-node "pstate")
                 )))
              (aor/node
               "pstate"
               "end"
               (fn [agent-node]
                 (let [p (aor/get-store agent-node "$$p")
                       _ (store/pstate-transform! [:a (termval 1)] p :a)
                       _ (store/pstate-transform! [:b (termval 2)] p :a)
                       i (store/pstate-select-one :a p)
                       j (store/pstate-select :a p)
                       k (store/pstate-select-one :b p :a)
                       j (store/pstate-select :b p :a)]
                   (aor/emit! agent-node "end")
                 )))
              (aor/node "end"
                        nil
                        (fn [agent-node]
                          (aor/result! agent-node "done")))
            )
           ))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))
         (bind depot
           (foreign-depot ipc
                          module-name
                          (po/agent-depot-name "foo")))
         (bind invokes-pstate
           (foreign-pstate ipc
                           module-name
                           (po/agent-invoke-task-global-name "foo")))
         (bind traces-query
           (foreign-query ipc
                          module-name
                          (queries/tracing-query-topology-name "foo")))
         (bind [graph-task-id graph-id]
           (invoke-agent-and-wait! depot invokes-pstate []))
         (bind root-invoke-id
           (foreign-select-one [(keypath graph-id) :root-invoke-id]
                               invokes-pstate
                               {:pkey graph-task-id}))
         (bind res
           (foreign-invoke-query traces-query
                                 graph-task-id
                                 [[graph-task-id root-invoke-id]]
                                 10000))
         (is
          (trace-matches?
           (:invokes-map res)
           {!id1
            {:agg-invoke-id     nil
             :emits             [{:invoke-id      !id2
                                  :target-task-id ?graph-task-id
                                  :node-name      "doc"
                                  :args           []}]
             :node              "kv"
             :nested-ops
             [{:start-time-millis 0
               :finish-time-millis 1
               :info
               {"type" "store-query" "op" "get" "params" [:b] "result" []}}
              {:start-time-millis 1
               :finish-time-millis 3
               :info
               {"type" "store-query" "op" "get" "params" [:b] "result" nil}}
              {:start-time-millis 3
               :finish-time-millis 6
               :info
               {"type"   "store-query"
                "op"     "contains?"
                "params" [:a]
                "result" false}}
              {:start-time-millis 6
               :finish-time-millis 10
               :info {"type" "store-write" "op" "put" "params" [:a 1]}}
              {:start-time-millis 10
               :finish-time-millis 15
               :info {"type" "store-write" "op" "update" "params" [:d]}}]
             :result            nil
             :graph-id          ?graph-id
             :input             []
             :graph-task-id     ?graph-task-id
             :start-time-millis 0
             :finish-time-millis 15
            }
            !id2
            {:agg-invoke-id     nil
             :emits             [{:invoke-id      !id3
                                  :target-task-id ?graph-task-id
                                  :node-name      "pstate"
                                  :args           []}]
             :node              "doc"
             :nested-ops
             [{:start-time-millis 15
               :finish-time-millis 21
               :info
               {"type"   "store-query"
                "op"     "get-document-field"
                "params" [:m :a {:default nil}]
                "result" nil}}
              {:start-time-millis 21
               :finish-time-millis 28
               :info
               {"type"   "store-query"
                "op"     "get-document-field"
                "params" [:m :b {:default []}]
                "result" []}}
              {:start-time-millis 28
               :finish-time-millis 36
               :info
               {"type"   "store-query"
                "op"     "contains-document-field?"
                "params" [:m :a]
                "result" false}}
              {:start-time-millis 36
               :finish-time-millis 45
               :info
               {"type"   "store-write"
                "op"     "put-document-field"
                "params" [:m :a 1]}}
              {:start-time-millis 45
               :finish-time-millis 55
               :info
               {"type"   "store-write"
                "op"     "update-document-field"
                "params" [:m :a]}}]
             :result            nil
             :graph-id          ?graph-id
             :input             []
             :graph-task-id     ?graph-task-id
             :start-time-millis 15
             :finish-time-millis 55
            }
            !id3
            {:agg-invoke-id     nil
             :emits             [{:invoke-id      !id4
                                  :target-task-id ?graph-task-id
                                  :node-name      "end"
                                  :args           []}]
             :node              "pstate"
             :nested-ops
             [{:start-time-millis 55
               :finish-time-millis 66
               :info
               {"type" "store-write" "op" "pstate-transform" "params" [:a]}}
              {:start-time-millis 66
               :finish-time-millis 78
               :info
               {"type" "store-write" "op" "pstate-transform" "params" [:a]}}
              {:start-time-millis 78
               :finish-time-millis 91
               :info
               {"type"   "store-query"
                "op"     "pstate-select-one"
                "params" []
                "result" 1}}
              {:start-time-millis 91
               :finish-time-millis 105
               :info
               {"type"   "store-query"
                "op"     "pstate-select"
                "params" []
                "result" [1]}}
              {:start-time-millis 105
               :finish-time-millis 120
               :info
               {"type"   "store-query"
                "op"     "pstate-select-one"
                "params" [{:pkey :a}]
                "result" 2}}
              {:start-time-millis 120
               :finish-time-millis 136
               :info
               {"type"   "store-query"
                "op"     "pstate-select"
                "params" [{:pkey :a}]
                "result" [2]}}]
             :result            nil
             :graph-id          ?graph-id
             :input             []
             :graph-task-id     ?graph-task-id
             :start-time-millis 55
             :finish-time-millis 136
            }
            !id4
            {:agg-invoke-id     nil
             :emits             []
             :node              "end"
             :nested-ops        []
             :result            {:val "done" :failure? false}
             :graph-id          ?graph-id
             :input             []
             :graph-task-id     ?graph-task-id
             :start-time-millis 136
             :finish-time-millis 136
            }
           }
           (m/guard
            (and (= ?graph-id graph-id)
                 (= ?graph-task-id graph-task-id)))))
        )))))

(deftest looped-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (->
          topology
          (aor/new-agent "foo")
          (aor/node
           "start"
           ["node1" "AS1"]
           (fn [agent-node arg res]
             (if (= arg 2)
               (aor/emit! agent-node "AS1" (inc arg) (conj res "start"))
               (aor/emit! agent-node "node1" (inc arg) (conj res "start")))))
          (aor/node
           "node1"
           "start"
           (fn [agent-node arg res]
             (aor/emit! agent-node "start" arg (conj res "node1"))))
          (aor/agg-start-node
           "AS1"
           "AS1-n1"
           (fn [agent-node arg res]
             (aor/emit! agent-node "AS1-n1" 0)
             {:arg arg :res res}))
          (aor/node
           "AS1-n1"
           ["AS1-n2" "AS2"]
           (fn [agent-node n]
             (when (< n 2)
               (aor/emit! agent-node "AS1-n2" (inc n)))
             (aor/emit! agent-node "AS2" 0)
           ))
          (aor/node
           "AS1-n2"
           "AS1-n3"
           (fn [agent-node n]
             (aor/emit! agent-node "AS1-n3" n)))
          (aor/node
           "AS1-n3"
           "AS1-n1"
           (fn [agent-node n]
             (aor/emit! agent-node "AS1-n1" n)))
          (aor/agg-start-node
           "AS2"
           "AS2-n1"
           (fn [agent-node n]
             (aor/emit! agent-node "AS2-n1" n)
             {}))
          (aor/node
           "AS2-n1"
           ["AS2-n2" "AS2-agg"]
           (fn [agent-node n]
             (aor/emit! agent-node "AS2-agg" 1)
             (when (< n 2)
               (aor/emit! agent-node "AS2-n2" (inc n)))
           ))
          (aor/node
           "AS2-n2"
           "AS2-n1"
           (fn [agent-node n]
             (aor/emit! agent-node "AS2-n1" n)
           ))
          (aor/agg-node
           "AS2-agg"
           ["AS1-agg" "AS2"]
           aggs/+sum
           (fn [agent-node agg node-start-res]
             ;; will loop once
             (when (= agg 3)
               (aor/emit! agent-node "AS2" 1))
             (aor/emit! agent-node "AS1-agg" agg)
           ))
          (aor/agg-node
           "AS1-agg"
           nil
           aggs/+sum
           (fn [agent-node agg {:keys [arg res]}]
             (aor/result! agent-node (conj res agg))
           ))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))

     (is (= ["start" "node1" "start" "node1" "start" 15]
            (:val (invoke-agent-and-return! depot invokes-pstate [0 []]))))
    )))


(def +early-sum-accum
  (accumulator
   (fn [v]
     (term (fn [curr]
             (let [ret (+ curr v)]
               (if (> ret 10)
                 (reduced ret)
                 ret
               ))
           )))
   :init-fn
   (constantly 0)))

(def +early-sum-combiner
  (combiner
   (fn [v1 v2]
     (let [ret (+ v1 v2)]
       (if (> ret 5)
         (reduced ret)
         ret
       )))
   :init-fn
   (constantly 0)))

(deftest early-aggs-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (let [g
              (->
                topology
                (aor/new-agent "foo")
                (aor/agg-start-node
                 "start"
                 ["ca" "cc" "ja" "jc" "ma"]
                 (fn [agent-node]
                   (aor/emit! agent-node "ca")
                   (aor/emit! agent-node "cc")
                   (aor/emit! agent-node "ja")
                   (aor/emit! agent-node "jc")
                   (aor/emit! agent-node "ma")
                 ))
                (aor/agg-start-node
                 "ma"
                 "ma-agg"
                 (fn [agent-node]
                   (dotimes [i 5]
                     (aor/emit! agent-node "ma-agg" "a" i)
                     (aor/emit! agent-node "ma-agg" "b" i)
                     (aor/emit! agent-node "ma-agg" "c" i))
                 ))
                (aor/agg-node
                 "ma-agg"
                 "agg"
                 (aor/multi-agg
                  (init [] 0)
                  (on "a"
                      [curr v]
                      (let [ret (+ curr v)]
                        (if (> ret 20)
                          (reduced ret)
                          ret)))
                  (on "b"
                      [curr v]
                      (let [ret (+ curr (* 2 v))]
                        (if (> ret 20)
                          (reduced ret)
                          ret)))
                  (on "c"
                      [curr v]
                      (let [ret (+ curr (* 3 v))]
                        (if (> ret 20)
                          (reduced ret)
                          ret))))
                 (fn [agent-node agg node-start-res]
                   (aor/emit! agent-node "agg" ["ma" agg])))
              )]
          (doseq [[label the-agg] [["ca" +early-sum-accum]
                                   ["cc" +early-sum-combiner]
                                   ["ja" (EarlySumAccum.)]
                                   ["jc" (EarlySumCombiner.)]]]
            (let [agg-label (str label "-agg")]
              (-> g
                  (aor/agg-start-node label
                                      agg-label
                                      (fn [agent-node]
                                        (dotimes [i 7]
                                          (aor/emit! agent-node agg-label i))
                                      ))
                  (aor/agg-node agg-label
                                "agg"
                                the-agg
                                (fn [agent-node agg node-start-res]
                                  (aor/emit! agent-node "agg" [label agg])
                                )))
            ))
          (aor/agg-node
           g
           "agg"
           nil
           aggs/+set-agg
           (fn [agent-node agg node-start-res]
             (aor/result! agent-node agg)))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))

     (bind ret
       (invoke-agent-and-return! depot invokes-pstate []))


     (is (=
          #{["ca" 15]
            ["cc" 6]
            ["ja" 15]
            ["jc" 6]
            ["ma" 21]
           }
          (:val ret)))
    )))

(deftest multi-agg-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (->
          topology
          (aor/new-agent "foo")
          (aor/agg-start-node
           "start"
           ["a" "b" "c"]
           (fn [agent-node]
             (aor/emit! agent-node "a" 1)
             (aor/emit! agent-node "b" 2)
             (aor/emit! agent-node "c" 3)
             (aor/emit! agent-node "a" 4)
             (aor/emit! agent-node "a" 5)
             (aor/emit! agent-node "c" 6)
             (aor/emit! agent-node "a" 7)
             (aor/emit! agent-node "b" 8)
           ))
          (aor/node
           "a"
           "agg"
           (fn [agent-node v]
             (aor/emit! agent-node "agg" "a" v)))
          (aor/node
           "b"
           "agg"
           (fn [agent-node v]
             (aor/emit! agent-node "agg" "b" v)))
          (aor/node
           "c"
           "agg"
           (fn [agent-node v]
             (aor/emit! agent-node "agg" "c" v)))
          (aor/agg-node
           "agg"
           nil
           (aor/multi-agg
            (init [] [[] [] []])
            (on "a"
                [[a b c] v]
                [(conj a v) b c])
            (on "b"
                [[a b c] v]
                [a (conj b v) c])
            (on "c"
                [[a b c] v]
                [a b (conj c v)]))
           (fn [agent-node agg node-start-res]
             (aor/result! agent-node agg)))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))

     (bind ret
       (:val (invoke-agent-and-return! depot invokes-pstate [])))

     (is (= 3 (count ret)))
     (bind [a b c] ret)
     (is (= #{1 4 5 7} (set a)))
     (is (= 4 (count a)))
     (is (= #{2 8} (set b)))
     (is (= 2 (count b)))
     (is (= #{3 6} (set c)))
     (is (= 2 (count c)))
    )))

(defn opens-matches-closes?
  [opens closes]
  (let [opens      (mapv System/identityHashCode opens)
        closes     (mapv System/identityHashCode closes)
        opens-map  (transform MAP-VALS count (group-by identity opens))
        closes-map (transform MAP-VALS count (group-by identity closes))]
    (and
     ;; sanity check
     (every? #(= % 1) (vals opens-map))
     (= opens-map closes-map))
  ))

(deftest basic-agent-client-test
  (let [opens-atom  (atom [])
        closes-atom (atom [])
        results-vol (atom 0)
        orig-close! close!]
    (with-redefs [i/hook:agent-result-proxy
                  (fn [proxy]
                    (swap! opens-atom conj proxy))
                  close! (fn [item]
                           (swap! closes-atom conj item)
                           (orig-close! item))
                  i/hook:writing-result (fn [& args] (swap! results-vol inc))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (->
              topology
              (aor/new-agent "foo")
              (aor/node "start"
                        nil
                        (fn [agent-node]
                          (aor/result! agent-node "abcd"))))
            (->
              topology
              (aor/new-agent "bar")
              (aor/node "start"
                        nil
                        (fn [agent-node v1 v2]
                          (aor/result! agent-node (+ v1 v2)))))
           ))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))

         (bind agent-manager (aor/agent-manager ipc module-name))
         (is (= #{"foo" "bar"} (aor/agent-names agent-manager)))

         (bind foo (aor/agent-client agent-manager "foo"))
         (bind bar (aor/agent-client agent-manager "bar"))

         (is (thrown? clojure.lang.ExceptionInfo
                      (aor/agent-client agent-manager "car")))

         (is (= "abcd" (aor/agent-invoke foo)))
         (is (= 11 (aor/agent-invoke bar 3 8)))

         (reset! results-vol 0)
         ;; verify agent-result can be called after agent has already finished
         (bind inv (aor/agent-initiate bar 10 12))
         (is (condition-attained? (= 1 @results-vol)))
         (is (= 22 (aor/agent-result bar inv)))
         (is (opens-matches-closes? @opens-atom @closes-atom))
         (is (= 22 (aor/agent-result bar inv)))
         (is (opens-matches-closes? @opens-atom @closes-atom))


         (bind cf (aor/agent-invoke-async foo))
         (is (instance? CompletableFuture cf))
         (is (= "abcd" (.get cf)))

         (bind cf (aor/agent-initiate-async bar 2 12))
         (is (instance? CompletableFuture cf))
         (bind inv (.get cf))

         (bind cf (aor/agent-result-async bar inv))
         (is (instance? CompletableFuture cf))
         (is (= 14 (.get cf)))

         (is (= (count @opens-atom) 6))
         (is (opens-matches-closes? @opens-atom @closes-atom))
        )))))

(defn matching-ascending-seq?
  ([items final-seq] (matching-ascending-seq? items final-seq <))
  ([items final-seq comp-fn]
   (and (apply comp-fn (mapv count items))
        (= final-seq (last items))
        (every?
         (fn [s]
           (= s (subvec final-seq 0 (count s))))
         items))))

(def SEM)
(def SEM2)

(defn- sc->data
  [chunks]
  (mapv (fn [^StreamingChunk sc]
          [(.getIndex sc) (.getChunk sc)])
        chunks))

(defn- sc->full-data
  [chunks]
  (mapv (fn [^StreamingChunk sc]
          [(.getInvokeId sc) (.getIndex sc) (.getChunk sc)])
        chunks))

(deftest node-streaming-fault-tolerance-test
  (let [processed-atom (atom [])
        streaming-index-mod-atom (atom 0)
        override-retry-num-atom (atom nil)]
    (with-redefs [SEM (h/mk-semaphore 0)
                  i/hook:processing-streaming
                  (fn [_ streaming-index value]
                    (swap! processed-atom conj [streaming-index value])
                  )

                  i/identity-streaming-index
                  (fn [v]
                    (- v @streaming-index-mod-atom))

                  i/identity-retry-num
                  (fn [v]
                    (if @override-retry-num-atom
                      @override-retry-num-atom
                      v))
                 ]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (module
             [setup topologies]
             (declare-depot setup *reset-depot :random)
             (let [topology (aor/agents-topology setup topologies)
                   s        (aor/underlying-stream-topology topology)]
               (->
                 topology
                 (aor/new-agent "foo")
                 (aor/node
                  "start"
                  nil
                  (fn [agent-node]
                    (aor/stream-chunk! agent-node "a")
                    (aor/stream-chunk! agent-node "b")
                    (aor/stream-chunk! agent-node "c")
                    (h/acquire-semaphore SEM 1)

                    (aor/stream-chunk! agent-node "d")
                    (aor/stream-chunk! agent-node "e")
                    (h/acquire-semaphore SEM 1)

                    (aor/stream-chunk! agent-node "f")
                    (aor/stream-chunk! agent-node "g")
                    (h/acquire-semaphore SEM 1)

                    (aor/stream-chunk! agent-node "h")
                    (h/acquire-semaphore SEM 1)

                    (aor/stream-chunk! agent-node "i")
                    (aor/stream-chunk! agent-node "j")
                    (h/acquire-semaphore SEM 1)

                    (aor/result! agent-node "abcd")
                  )))
               (aor/define-agents! topology)
               (<<sources s
                (source> *reset-depot
                         :> [*agent-name *graph-task-id *graph-id])
                 (|direct *graph-task-id)
                 (this-module-pobject-task-global
                  (po/agent-invoke-task-global-name *agent-name)
                  :> $$root)
                 (this-module-pobject-task-global
                  (po/agent-streaming-results-task-global-name *agent-name)
                  :> $$streaming)
                 (local-transform>
                  [(keypath *graph-id) :retry-num (term inc)]
                  $$root)
                 (local-transform>
                  [(keypath *graph-id)
                   MAP-VALS
                   (multi-path [:all NONE>]
                               [:invokes NONE>])]
                  $$streaming)
               )
             )))
         (rtest/launch-module! ipc module {:tasks 4 :threads 2})
         (bind module-name (get-module-name module))

         (bind reset-depot (foreign-depot ipc module-name "*reset-depot"))
         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))

         (bind inv (aor/agent-initiate foo))
         (bind all-chunks-atom (atom []))
         (bind chunks-atom (atom []))
         (bind meta-atom (atom []))
         (bind clear!
           (fn []
             (reset! all-chunks-atom [])
             (reset! chunks-atom [])
             (reset! meta-atom [])))

         (bind as
           (aor/agent-stream
            foo
            inv
            "start"
            (fn [all-chunks new-chunks reset? complete?]
              (swap! all-chunks-atom conj (sc->data all-chunks))
              (swap! meta-atom conj [reset? complete?])
              (doseq [^StreamingChunk sc new-chunks]
                (swap! chunks-atom conj
                  [(.getInvokeId sc)
                   (.getIndex sc)
                   (.getChunk sc)]))
            )))

         (is (condition-attained? (= 3 (count @chunks-atom))))
         (is (matching-ascending-seq? @all-chunks-atom
                                      [[0 "a"] [1 "b"] [2 "c"]]))
         (bind inv-id (select-any [FIRST FIRST] @chunks-atom))
         (is (apply = inv-id (select [ALL FIRST] @chunks-atom)))
         (is (= [[0 "a"] [1 "b"] [2 "c"]]
                (setval [ALL FIRST] NONE @chunks-atom)))
         (doseq [m @meta-atom]
           (= [false false] m))
         (is (= [[0 "a"] [1 "b"] [2 "c"]] (sc->data @as)))

         (clear!)
         (h/release-semaphore SEM 1)
         (is (condition-attained? (= 2 (count @chunks-atom))))
         (is (matching-ascending-seq? @all-chunks-atom
                                      [[0 "a"] [1 "b"] [2 "c"] [3 "d"]
                                       [4 "e"]]))
         (is (apply = inv-id (select [ALL FIRST] @chunks-atom)))
         (is (= [[3 "d"] [4 "e"]] (setval [ALL FIRST] NONE @chunks-atom)))
         (doseq [m @meta-atom]
           (= [false false] m))
         (is (= [[0 "a"] [1 "b"] [2 "c"] [3 "d"] [4 "e"]] (sc->data @as)))


         (reset! processed-atom [])
         (clear!)
         (foreign-append! reset-depot
                          ["foo" (.getTaskId inv) (.getAgentInvokeId inv)])
         (h/release-semaphore SEM 1)
         (is (condition-attained? (= [[5 "f"] [6 "g"]] @processed-atom)))
         (is (condition-attained? (= 1 (count @meta-atom))))
         (is (= [] @chunks-atom))
         (is (= [[]] @all-chunks-atom))
         (is (= [] @as))
         (is (= [[true false]] @meta-atom))

         ;; verify these don't get through because streaming-index is wrong
         (clear!)
         (reset! streaming-index-mod-atom 6)
         (reset! override-retry-num-atom 1)
         (reset! processed-atom [])
         (h/release-semaphore SEM 1)
         (is (condition-attained? (= [[1 "h"]] @processed-atom)))
         (is (= [] @meta-atom))
         (is (= [] @chunks-atom))
         (is (= [] @all-chunks-atom))
         (is (= [] @as))

         (clear!)
         (reset! streaming-index-mod-atom 8)
         (h/release-semaphore SEM 1)
         (is (condition-attained? (= 2 (count @chunks-atom))))
         (is (matching-ascending-seq? @all-chunks-atom
                                      [[0 "i"] [1 "j"]]))
         (is (apply = inv-id (select [ALL FIRST] @chunks-atom)))
         (is (= [[0 "i"] [1 "j"]] (setval [ALL FIRST] NONE @chunks-atom)))
         (doseq [m @meta-atom]
           (= [false false] m))
         (is (= [[0 "i"] [1 "j"]] (sc->data @as)))

         (clear!)
         (h/release-semaphore SEM 1)
         (is (= "abcd" (aor/agent-result foo inv)))
         (is (condition-attained? (= 1 (count @meta-atom))))
         (is (= [[false true]] @meta-atom))
         (is (= [[[0 "i"] [1 "j"]]] @all-chunks-atom))
         (is (= [] @chunks-atom))
         (is (= [[0 "i"] [1 "j"]] (sc->data @as)))
        )))))

(deftest many-nodes-streaming-test
  (let [orig-close! close!
        closes-atom (atom 0)]
    (with-redefs [SEM    (h/mk-semaphore 0)
                  close! (fn [item]
                           (swap! closes-atom inc)
                           (orig-close! item))]
      (with-open [ipc (rtest/create-ipc)]
        (letlocals
         (bind module
           (aor/agentmodule
            [topology]
            (->
              topology
              (aor/new-agent "foo")
              (aor/node
               "start"
               ["node1" "node2" "node3"]
               (fn [agent-node]
                 (aor/emit! agent-node "node3")
                 (aor/emit! agent-node "node1")
                 (aor/emit! agent-node "node1")
                 (aor/emit! agent-node "node2")
                 (aor/emit! agent-node "node2")
               ))
              (aor/node
               "node1"
               nil
               (fn [agent-node]
                 (dotimes [i 50]
                   (when (= 0 (mod i 10))
                     (Thread/sleep ^Long (rand-int 10)))
                   (aor/stream-chunk! agent-node i))))
              (aor/node
               "node2"
               nil
               (fn [agent-node]
                 (dotimes [i 50]
                   (when (= 0 (mod i 10))
                     (Thread/sleep ^Long (rand-int 10)))
                   (aor/stream-chunk! agent-node (+ 200 i)))))
              (aor/node
               "node3"
               nil
               (fn [agent-node]
                 (aor/result! agent-node "aaa")))
            )
            (->
              topology
              (aor/new-agent "bar")
              (aor/node
               "start"
               ["node1" "node2" "node3"]
               (fn [agent-node]
                 (aor/emit! agent-node "node1")
                 (aor/emit! agent-node "node1")
                 (aor/emit! agent-node "node2")
                 (aor/emit! agent-node "node2")
                 (aor/emit! agent-node "node3")
               ))
              (aor/node
               "node1"
               nil
               (fn [agent-node]
                 (dotimes [i 50]
                   (when (= 0 (mod i 10))
                     (Thread/sleep ^Long (rand-int 10)))
                   (aor/stream-chunk! agent-node (+ 1000 i)))))
              (aor/node
               "node2"
               nil
               (fn [agent-node]
                 (dotimes [i 50]
                   (when (= 0 (mod i 10))
                     (Thread/sleep ^Long (rand-int 10)))
                   (aor/stream-chunk! agent-node (+ 1200 i)))))
              (aor/node
               "node3"
               nil
               (fn [agent-node]
                 (aor/result! agent-node "bbb")))
            )
           ))
         (rtest/launch-module! ipc module {:tasks 8 :threads 8})
         (bind module-name (get-module-name module))

         (bind agent-manager (aor/agent-manager ipc module-name))
         (bind foo (aor/agent-client agent-manager "foo"))
         (bind bar (aor/agent-client agent-manager "bar"))


         (bind m
           {"foo" {0 (aor/agent-initiate foo)
                   1 (aor/agent-initiate foo)}
            "bar" {0 (aor/agent-initiate bar)
                   1 (aor/agent-initiate bar)}})

         (bind m2
           (transform
            [ALL (collect-one FIRST) LAST MAP-VALS]
            (fn [agent-name inv]
              (reduce
               (fn [m n]
                 (let [a (atom [])]
                   (aor/agent-stream
                    (if (= "foo" agent-name) foo bar)
                    inv
                    n
                    (fn [all-chunks new-chunks reset? complete?]
                      (swap! a conj
                        [(mapv (fn [^StreamingChunk sc]
                                 [(.getInvokeId sc) (.getIndex sc)
                                  (.getChunk sc)])
                               all-chunks)
                         (mapv (fn [^StreamingChunk sc]
                                 [(.getInvokeId sc) (.getIndex sc)
                                  (.getChunk sc)])
                               new-chunks)
                         reset?
                         complete?])))
                   (assoc m n a)))
               {}
               ["node1" "node2"]))
            m))

         (bind res-map
           (transform [ALL (collect-one FIRST) LAST MAP-VALS]
                      (fn [agent-name inv]
                        (aor/agent-result
                         (if (= "foo" agent-name) foo bar)
                         inv
                        ))
                      m))

         (is (= {"foo" {0 "aaa"
                        1 "aaa"}
                 "bar" {0 "bbb"
                        1 "bbb"}}
                res-map))

         (is (condition-attained?
              (= 8
                 (count (select [MAP-VALS
                                 MAP-VALS
                                 MAP-VALS
                                 (view deref)
                                 LAST
                                 (nthpath 3)
                                 (pred= true)]
                                m2)))))


         (bind m2 (transform [MAP-VALS MAP-VALS MAP-VALS] deref m2))

         (bind expected-map
           {"foo" {"node1" (mapv (fn [i] [i i]) (range 50))
                   "node2" (mapv (fn [i] [i (+ 200 i)]) (range 50))}
            "bar" {"node1" (mapv (fn [i] [i (+ 1000 i)]) (range 50))
                   "node2" (mapv (fn [i] [i (+ 1200 i)]) (range 50))}})

         (bind separate-by-invoke-id*
           (fn [chunks]
             (let [grouped (group-by first chunks)]
               (setval [MAP-VALS ALL FIRST] NONE grouped))))

         (bind separate-by-invoke-id
           (fn [chunks]
             (let [g (mapv separate-by-invoke-id* chunks)]
               (reduce
                (fn [res maps]
                  (reduce-kv
                   (fn [res k elems]
                     (setval [(keypath k) NIL->VECTOR AFTER-ELEM] elems res))
                   res
                   maps))
                {}
                g
               ))))

         (doseq [[agent-name inv-map] m2]
           (doseq [[_ node-map] inv-map]
             (doseq [[node res] node-map]
               (letlocals
                (bind expected
                  (-> expected-map
                      (get agent-name)
                      (get node)))
                (bind metas
                  (mapv #(select-any (srange 2 4) %) res))
                (is (= [false true] (last metas)))
                (is (every? #(= % [false false]) (butlast metas)))
                (bind all-chunks (separate-by-invoke-id (mapv first res)))
                (bind chunks (separate-by-invoke-id (mapv second res)))

                (doseq [[_ inv-all-chunks] all-chunks]
                  ;; <= because last one could just be the completion one
                  (is (matching-ascending-seq? inv-all-chunks expected <=)))

                (doseq [[_ inv-chunks] chunks]
                  (is (= expected (apply concat inv-chunks))))
               ))))


         ;; 4 for results, 8 for streams
         (is (= 12 @closes-atom))


         (bind as
           (aor/agent-stream foo (select-any (keypath "foo" 0) m) "node1"))
         (is (= 13 @closes-atom))

         (bind foo-node1-expected
           (select-any (keypath "foo" "node1") expected-map))

         (doseq [[_ [elems]] (separate-by-invoke-id [(sc->full-data @as)])]
           (is (= foo-node1-expected elems)))

         (bind res-atom (atom []))
         (bind as
           (aor/agent-stream foo
                             (select-any (keypath "foo" 0) m)
                             "node1"
                             (fn [all-chunks new-chunks reset? complete?]
                               (swap! res-atom conj
                                 [all-chunks new-chunks reset? complete?])
                             )))
         (is (= 14 @closes-atom))
         (is (= 1 (count @res-atom)))
         (bind res (first @res-atom))
         (doseq [data [@as (first res) (second res)]]
           (doseq [[_ [elems]] (separate-by-invoke-id [(sc->full-data data)])]
             (is (= foo-node1-expected elems))))
         (is (= false (nth res 2)))
         (is (= true (nth res 3)))
        )))))

(deftest stream-close-test
  (with-redefs [SEM (h/mk-semaphore 0)]
    (with-open [ipc (rtest/create-ipc)]
      (letlocals
       (bind module
         (aor/agentmodule
          [topology]
          (->
            topology
            (aor/new-agent "foo")
            (aor/node
             "start"
             nil
             (fn [agent-node]
               (aor/stream-chunk! agent-node "a")
               (aor/stream-chunk! agent-node "b")
               (aor/stream-chunk! agent-node "c")
               (h/acquire-semaphore SEM 1)
               (aor/stream-chunk! agent-node "d")
               (aor/stream-chunk! agent-node "e")
               (aor/result! agent-node "abcd")
             )))))
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))

       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))

       (bind inv (aor/agent-initiate foo))
       (bind res-atom (atom []))
       (bind as
         (aor/agent-stream foo
                           inv
                           "start"
                           (fn [all-chunks new-chunks reset? complete?]
                             (swap! res-atom conj (sc->data new-chunks))
                           )))

       (is (condition-attained? (= [[0 "a"] [1 "b"] [2 "c"]]
                                   (apply concat @res-atom))))
       (close! as)
       (reset! res-atom [])
       (h/release-semaphore SEM 1)
       (is (= "abcd" (aor/agent-result foo inv)))
       (is (empty? @res-atom))
       ;; verify close is idempotetent
       (close! as)
      ))))

(defn get-executing-node-ids
  [^AgentNodeExecutorTaskGlobal node-exec]
  (.getRunningInvokeIds node-exec))

(deftest agent-pending-tracking-test
  (with-redefs [SEM  (h/mk-semaphore 0)
                SEM2 (h/mk-semaphore 0)]
    (with-open [ipc (rtest/create-ipc)
                _ (TopologyUtils/startSimTime)]
      (letlocals
       (bind module
         (module
           [setup topologies]
           (let [topology   (aor/agents-topology setup topologies)
                 node-exec  (symbol (po/agent-node-executor-name))
                 active-foo (symbol (po/agent-active-invokes-task-global-name
                                     "foo"))
                 active-bar (symbol (po/agent-active-invokes-task-global-name
                                     "bar"))]
             (->
               topology
               (aor/new-agent "foo")
               (aor/node
                "start"
                "node1"
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 1)
                  (h/acquire-semaphore SEM 1)
                  (aor/emit! agent-node "node1")
                ))
               (aor/node
                "node1"
                nil
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 2)
                  (h/acquire-semaphore SEM 1)
                  (aor/result! agent-node "abc")
                )))
             (->
               topology
               (aor/new-agent "bar")
               (aor/node
                "start"
                "node1"
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 10)
                  (h/acquire-semaphore SEM2 1)
                  (aor/emit! agent-node "node1")
                ))
               (aor/node
                "node1"
                "node2"
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 20)
                  (h/acquire-semaphore SEM2 1)
                  (aor/emit! agent-node "node2")
                ))
               (aor/agg-start-node
                "node2"
                "node3"
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 30)
                  (h/acquire-semaphore SEM2 1)
                  (aor/emit! agent-node "node3")
                  (aor/emit! agent-node "node3")))
               (aor/node
                "node3"
                "node4"
                (fn [agent-node]
                  (TopologyUtils/advanceSimTime 1)
                  (h/acquire-semaphore SEM2 1)
                  (TopologyUtils/advanceSimTime 40)
                  (aor/emit! agent-node "node4" 1)))
               (aor/agg-node
                "node4"
                nil
                aggs/+sum
                (fn [agent-node agg node-start-res]
                  (h/acquire-semaphore SEM2 1)
                  (TopologyUtils/advanceSimTime 10)
                  (aor/result! agent-node "def"))))
             (aor/define-agents! topology)
             (<<query-topology topologies
               "pending-invoke-ids"
               [:> *invoke-ids]
               (|all)
               (get-executing-node-ids node-exec :> *s)
               (ops/explode *s :> *invoke-id)
               (|origin)
               (aggs/+set-agg *invoke-id :> *invoke-ids))
             (<<query-topology topologies
               "pending-agent-count"
               [:> *res]
               (|all)
               (local-select> MAP-KEYS active-foo :> *agent-id)
               (identity "foo" :> *name)
               (anchor> <foo>)

               (gen>)
               (|all)
               (local-select> MAP-KEYS active-bar :> *agent-id)
               (identity "bar" :> *name)
               (anchor> <bar>)

               (unify> <foo> <bar>)
               (|origin)
               (+compound {*name (aggs/+count)} :> *res))
           )))
       (rtest/launch-module! ipc module {:tasks 4 :threads 2})
       (bind module-name (get-module-name module))
       (bind pending-invoke-ids
         (foreign-query ipc module-name "pending-invoke-ids"))
       (bind pending-invokes
         (fn []
           (foreign-invoke-query pending-invoke-ids)))
       (bind pending-agent-count-q
         (foreign-query ipc module-name "pending-agent-count"))
       (bind pending-agent-count
         (fn []
           (foreign-invoke-query pending-agent-count-q)))
       (bind foo-invokes-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-invoke-task-global-name "foo")))
       (bind bar-invokes-pstate
         (foreign-pstate ipc
                         module-name
                         (po/agent-invoke-task-global-name "bar")))
       (bind agent-manager (aor/agent-manager ipc module-name))
       (bind foo (aor/agent-client agent-manager "foo"))
       (bind bar (aor/agent-client agent-manager "bar"))

       (bind last-progress-time
         (fn [pstate ^AgentInvoke inv]
           (let [task-id (.getTaskId inv)
                 inv-id  (.getAgentInvokeId inv)]
             (foreign-select-one [(keypath inv-id)
                                  :last-progress-time-millis]
                                 pstate
                                 {:pkey task-id}))))
       (bind release-and-change-invokes!
         (fn this
           ([sem]
            (this sem 0))
           ([sem delta]
            (let [invokes (pending-invokes)
                  c       (count invokes)]
              (h/release-semaphore sem 1)
              (when-not
                (condition-attained?
                 (let [new-invokes (pending-invokes)]
                   (and (= (count new-invokes) (+ c delta))
                        (= (count (set/intersection invokes new-invokes))
                           (dec c))
                   )))
                (throw (ex-info "Failed" {})))
            ))))

       (bind inv-foo1 (aor/agent-initiate foo))

       (is (condition-attained?
            (= 1 (count (pending-invokes)))))
       (is (= 0 (last-progress-time foo-invokes-pstate inv-foo1)))
       (is (= {"foo" 1} (pending-agent-count)))

       (release-and-change-invokes! SEM)
       (is (= {"foo" 1} (pending-agent-count)))
       (is (condition-attained?
            (= 1 (last-progress-time foo-invokes-pstate inv-foo1))))

       (bind inv-bar1 (aor/agent-initiate bar))
       (is (condition-attained?
            (= 2 (count (pending-invokes)))))
       (is (= 3 (last-progress-time bar-invokes-pstate inv-bar1)))
       (is (= {"foo" 1 "bar" 1} (pending-agent-count)))

       (release-and-change-invokes! SEM2)
       (is (= {"foo" 1 "bar" 1} (pending-agent-count)))
       (is (condition-attained?
            (= 1 (last-progress-time foo-invokes-pstate inv-foo1))))
       (is (condition-attained?
            (= 13 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM2)
       (is (= {"foo" 1 "bar" 1} (pending-agent-count)))
       (is (condition-attained?
            (= 1 (last-progress-time foo-invokes-pstate inv-foo1))))
       (is (condition-attained?
            (= 33 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM2 1)
       (is (= {"foo" 1 "bar" 1} (pending-agent-count)))
       (is (condition-attained?
            (= 1 (last-progress-time foo-invokes-pstate inv-foo1))))
       (is (condition-attained?
            (= 63 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM -1)
       (is (= "abc" (aor/agent-result foo inv-foo1)))
       (is (condition-attained? (= {"bar" 1} (pending-agent-count))))
       (is (= 65 (last-progress-time foo-invokes-pstate inv-foo1)))
       (is (condition-attained?
            (= 63 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM2 -1)
       (is (= {"bar" 1} (pending-agent-count)))
       (is (= 65 (last-progress-time foo-invokes-pstate inv-foo1)))
       (is (condition-attained?
            (= 105 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM2)
       (is (= {"bar" 1} (pending-agent-count)))
       (is (= 65 (last-progress-time foo-invokes-pstate inv-foo1)))
       (is (condition-attained?
            (= 145 (last-progress-time bar-invokes-pstate inv-bar1))))

       (release-and-change-invokes! SEM2 -1)
       (is (= "def" (aor/agent-result bar inv-bar1)))
       (is (condition-attained? (= {} (pending-agent-count))))
       (is (= 65 (last-progress-time foo-invokes-pstate inv-foo1)))
       (is (= 155 (last-progress-time bar-invokes-pstate inv-bar1)))

       (bind inv-foo1 (aor/agent-initiate foo))
       (bind inv-foo2 (aor/agent-initiate foo))
       (bind inv-bar1 (aor/agent-initiate bar))
       (is (condition-attained? (= {"foo" 2 "bar" 1} (pending-agent-count))))
       (h/release-semaphore SEM 3)
       (is (condition-attained? (= {"foo" 1 "bar" 1} (pending-agent-count))))

       (h/release-semaphore SEM 1000)
       (h/release-semaphore SEM2 1000)

       (aor/agent-result foo inv-foo1)
       (aor/agent-result foo inv-foo2)
       (aor/agent-result bar inv-bar1)




       ;; TODO: <<<<<>>>>
       ;;   - test and verify pending agents, pending nodes, and
       ;;   last-updated-ack-val times
       ;;     - actually, is this necessary with how execution will work?
       ;;       - it should in theory reduce the amount of work the checker
       ;;       agent does, but does that matter?
       ;;       - if it's a long-running model call, it won't make a
       ;;       difference
       ;;         - though if it's many short model calls, it will make a
       ;;         difference as those are all separate node invokes

       ;; TODO: <<<<<>>>>
       ;;   - test failed node cleans up active node tracking
      ))))

(deftest traced-out-of-band-test


         ;; TODO: <<<<<>>>> do custom CF thing with custom tracing
         ;;  - need to make API for this
)
