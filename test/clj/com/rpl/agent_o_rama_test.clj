(ns com.rpl.agent-o-rama-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.core :as i]
   [com.rpl.agent-o-rama.impl.graph :as graph]
   [com.rpl.agent-o-rama.impl.helpers :as h]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [com.rpl.ramaspecter :refer [walker]]
   [loom.attr :as lattr]
   [loom.graph :as lgraph]
   [meander.epsilon :as m])
  (:import
   [com.rpl.agentorama
    BuiltIn]
   [com.rpl.agent_o_rama.impl.types
    Node
    NodeAgg
    NodeAggStart]
   [com.rpl.rama.ops
    RamaAccumulatorAgg0
    RamaAccumulatorAgg2
    RamaCombinerAgg]))

(defn parse-var-prefix
  [sym]
  (let [s (name sym)]
    (if-let [[_ prefix] (re-matches #"^(.*?)[0-9]+$" s)]
      prefix
      s)))

(defmacro trace-matches?
  [data & bindings]
  (let [unique-syms   (set (select [(walker symbol?)
                                    #(= \!
                                        (-> %
                                            str
                                            first))]
                                   bindings))
        unique-syms   (setval [ALL NAME FIRST] \? unique-syms)
        unique-groups (vals (group-by parse-var-prefix unique-syms))
        unique-guards (for [group unique-groups]
                        `(m/guard (= ~(count group)
                                     (count (set ~(vec group))))))
        bindings      (setval [(walker symbol?)
                               NAME
                               FIRST
                               #(= \! %)]
                              \?
                              bindings)]
    `(m/find
      ~data
      (m/and
       ~@bindings
       ~@unique-guards)
      true)))

(defn trace-time-deltas
  [trace]
  (transform [MAP-VALS
              (multi-path
               STAY
               [(must :async-ops) ALL (view #(into {} %))])
              (submap [:start-time-millis :finish-time-millis])
              #(= 2 (count %))]
             (fn [{:keys [start-time-millis finish-time-millis]}]
               {:delta-millis (- finish-time-millis start-time-millis)})
             trace))

(defn no-time-deltas
  [trace]
  (setval [MAP-VALS
           (multi-path
            STAY
            [(must :async-ops) ALL (view #(into {} %))])
           (submap [:start-time-millis :finish-time-millis])]
          {}
          trace))

(deftest trace-matches-test
  (is
   (trace-matches?
    {:a {:s 1 :f 2 :emit :b}
     :b {:s 10 :f 11 :emit :c}
     :c {:s 7 :f 8}}
    {!k1 {:s ?s1 :f ?f1 :emit !k2}
     !k2 {:s ?s2 :f ?f2 :emit !k3}
     !k3 {:s ?s3 :f ?f3}}
    (m/guard
     (and (= 1 (- ?f1 ?s1))
          (= 1 (- ?f2 ?s2))
          (= 1 (- ?f3 ?s3)))
    )))
  (is
   (not
    (trace-matches?
     {:a {:s 1 :f 2 :emit :c}
      :b {:s 10 :f 11 :emit :c}
      :c {:s 7 :f 8}}
     {!k1 {:s ?s1 :f ?f1 :emit !k2}
      !k2 {:s ?s2 :f ?f2 :emit !k3}
      !k3 {:s ?s3 :f ?f3}}
     (m/guard
      (and (= 1 (- ?f1 ?s1))
           (= 1 (- ?f2 ?s2))
           (= 1 (- ?f3 ?s3)))
     ))))
  (is
   (trace-matches?
    {:a {:s 1 :f 2 :emit :b}
     :b {:s 10 :f 12 :emit :c}
     :c {:s 7 :f 10}}
    {!k1 {:s ?s1 :f ?f1 :emit !k2}
     !k2 {:s ?s2 :f ?f2 :emit !k3}
     !k3 {:s ?s3 :f ?f3}}
    (m/guard
     (and (= 1 (- ?f1 ?s1))
          (= 2 (- ?f2 ?s2))
          (= 3 (- ?f3 ?s3)))
    )))
  (is
   (not
    (trace-matches?
     {:a {:s 1 :f 2 :emit :b}
      :b {:s 10 :f 12 :emit :c}
      :c {:s 7 :f 10}}
     {!k1 {:s ?s1 :f ?f1 :emit !k2}
      !k2 {:s ?s2 :f ?f2 :emit !k3}
      !k3 {:s ?s3 :f ?f3}}
     (m/guard
      (and (= 1 (- ?f1 ?s1))
           (= 1 (- ?f2 ?s2))
           (= 3 (- ?f3 ?s3)))
     ))))
  (is
   (trace-matches?
    [1 2 3 1 2 3]
    [!id1 !id2 !id3 !a1 !a2 !a3]))
  (is
   (trace-matches?
    [1 2 3 4 5 6]
    [!id1 !id2 !id3 !a1 !a2 !a3]))
  (is
   (not
    (trace-matches?
     [1 2 1 4 5 6]
     [!id1 !id2 !id3 !a1 !a2 !a3])))
  (is
   (trace-matches?
    [1 2 3 4 5 6]
    [!id1-1 !id1-2 !id1-3 !id1 !id2 !id3]))
  (is
   (not
    (trace-matches?
     [1 2 1 4 5 6]
     [!id1-1 !id1-2 !id1-3 !id1 !id2 !id3])))
)

(deftest trace-time-deltas-test
  (is
   (=
    (trace-time-deltas
     {:a {:start-time-millis 3
          :finish-time-millis 5
          :q 1
          :async-ops [{:a 2 :start-time-millis 10 :finish-time-millis 20}
                      {:start-time-millis 11 :finish-time-millis 12}]}
      :b {:start-time-millis 2 :finish-time-millis 6 :z 1 :x 9}
      :c {:q 3}})
    {:a {:delta-millis 2
         :q         1
         :async-ops [{:a 2 :delta-millis 10}
                     {:delta-millis 1}]}
     :b {:delta-millis 4 :z 1 :x 9}
     :c {:q 3}}
   )))

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
                          (po/agent-depot-task-global-name "foo")))
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
                 (foreign-append! depot (aor-types/->AgentInvoke ["hello"] 0))]
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
                 (foreign-append! depot (aor-types/->AgentInvoke [] 0))]
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

(deftest node-traces-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node "start"
                      "node1"
                      (fn [agent-node arg]
                        (aor/emit! agent-node "node1" (str arg "-0"))
                      ))
            (aor/node "node1"
                      "node2"
                      (fn [agent-node arg]
                        (aor/emit! agent-node "node2" (str arg "-00"))
                        (aor/emit! agent-node "node2" (str arg "-01"))
                      ))
            (aor/node "node2"
                      "node3"
                      (fn [agent-node arg]
                        (aor/emit! agent-node "node3" (str arg "-000"))
                      ))
            (aor/agg-start-node "node3"
                                "node4"
                                (fn [agent-node arg]
                                  (dotimes [_ 3]
                                    (aor/emit! agent-node "node4" 1))
                                  (str arg "-0000")))
            (aor/node "node4"
                      "agg"
                      (fn [agent-node arg]
                        (aor/emit! agent-node "agg" (str arg "-a"))
                      ))
            (aor/agg-node "agg"
                          nil
                          aggs/+vec-agg
                          (fn [agent-node agg node-start-res]
                            (aor/result! agent-node [agg node-start-res])))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-task-global-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-topology-name "foo")))
     (bind {[graph-task-id graph-id] "_agents-topology"}
       (foreign-append! depot (aor-types/->AgentInvoke ["xyz"] 0)))
     (bind root-invoke-id
       (foreign-select-one [(keypath graph-id) :root-invoke-id]
                           invokes-pstate
                           {:pkey graph-task-id}))
     (bind res
       (foreign-invoke-query traces-query
                             graph-task-id
                             [[graph-task-id root-invoke-id]]
                             10000))

     (is (empty? (:next-task-invoke-pairs res)))
     (is
      (trace-matches?
       (-> res
           :invokes-map
           no-time-deltas)
       {!id1  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id2
                 :target-task-id ?graph-task-id
                 :node-name      "node1"
                 :args           [{:val "xyz-0"
                                   :async-op-index nil}]}]
               :node          "start"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz"]
               :graph-task-id ?graph-task-id
              }
        !id2  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id3
                 :target-task-id ?graph-task-id
                 :node-name      "node2"
                 :args           [{:val "xyz-0-00"
                                   :async-op-index nil}]}
                {:invoke-id      !id4
                 :target-task-id !id2-t1
                 :node-name      "node2"
                 :args           [{:val "xyz-0-01"
                                   :async-op-index nil}]}]
               :node          "node1"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz-0"]
               :graph-task-id ?graph-task-id}
        !id3  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id5
                 :target-task-id ?graph-task-id
                 :node-name      "node3"
                 :args           [{:val "xyz-0-00-000"
                                   :async-op-index nil}]}]
               :node          "node2"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz-0-00"]
               :graph-task-id ?graph-task-id}
        !id5  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id6
                 :target-task-id ?graph-task-id
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}
                {:invoke-id      !id7
                 :target-task-id !id5-t1
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}
                {:invoke-id      !id8
                 :target-task-id !id5-t2
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}]
               :started-agg?  true
               :node          "node3"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz-0-00-000"]
               :graph-task-id ?graph-task-id}
        !id6  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id9
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id9  {:invoked-agg-invoke-id !agg0}
        !id7  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id10
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id10 {:invoked-agg-invoke-id !agg0}
        !id8  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id11
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id11 {:invoked-agg-invoke-id !agg0}
        !id4  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id12
                 :target-task-id ?graph-task-id
                 :node-name      "node3"
                 :args           [{:val "xyz-0-01-000"
                                   :async-op-index nil}]}]
               :node          "node2"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz-0-01"]
               :graph-task-id ?graph-task-id}
        !id12 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id13
                 :target-task-id ?graph-task-id
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}
                {:invoke-id      !id14
                 :target-task-id !id12-t1
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}
                {:invoke-id      !id15
                 :target-task-id !id12-t2
                 :node-name      "node4"
                 :args           [{:val 1 :async-op-index nil}]}]
               :started-agg?  true
               :node          "node3"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         ["xyz-0-01-000"]
               :graph-task-id ?graph-task-id}
        !id13 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id16
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id16 {:invoked-agg-invoke-id !agg1}
        !id14 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id17
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id17 {:invoked-agg-invoke-id !agg1}
        !id15 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id18
                 :target-task-id ?graph-task-id
                 :node-name      "agg"
                 :args           [{:val "1-a" :async-op-index nil}]}]
               :node          "node4"
               :async-ops     []
               :result        nil
               :graph-id      ?graph-id
               :input         [1]
               :graph-task-id ?graph-task-id}
        !id18 {:invoked-agg-invoke-id !agg1}
        !agg0 {:agg-invoke-id   nil
               :agg-input-count 3
               :agg-start-res   "xyz-0-00-000-0000"
               :emits           []
               :node            "agg"
               :agg-inputs-first-10
               [{:invoke-id !id9' :args ["1-a"]}
                {:invoke-id !id10' :args ["1-a"]}
                {:invoke-id !id11' :args ["1-a"]}]
               :async-ops       []
               :agg-ack-val     0
               :result          {:val [["1-a" "1-a" "1-a"]
                                       "xyz-0-00-000-0000"]}
               :agg-finished?   true
               :graph-id        ?graph-id
               :agg-state       ["1-a" "1-a" "1-a"]
               :input           [["1-a" "1-a" "1-a"]
                                 "xyz-0-00-000-0000"]
               :agg-start-invoke-id !id5
               :graph-task-id   ?graph-task-id}
        !agg1 {:agg-invoke-id   nil
               :agg-input-count 3
               :agg-start-res   "xyz-0-01-000-0000"
               :emits           []
               :node            "agg"
               :agg-inputs-first-10
               [{:invoke-id !id16' :args ["1-a"]}
                {:invoke-id !id17' :args ["1-a"]}
                {:invoke-id !id18' :args ["1-a"]}]
               :async-ops       []
               :agg-ack-val     0
               :result          {:val [["1-a" "1-a" "1-a"]
                                       "xyz-0-01-000-0000"]}
               :agg-finished?   true
               :graph-id        ?graph-id
               :agg-state       ["1-a" "1-a" "1-a"]
               :input           [["1-a" "1-a" "1-a"]
                                 "xyz-0-01-000-0000"]
               :agg-start-invoke-id !id12
               :graph-task-id   ?graph-task-id}
       }
       (m/guard
        (and (= ?graph-id graph-id)
             (= ?graph-task-id graph-task-id)
             (= !id1 root-invoke-id)))
       (m/guard
        (and (= #{!id9 !id10 !id11} #{!id9' !id10' !id11'})
             (= #{!id16 !id17 !id18} #{!id16' !id17' !id18'})
        ))
      ))
    )))

(deftest tracing-topology-pagination-test
  (with-open [ipc (rtest/create-ipc)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node "start"
                      ["node1" "node2"]
                      (fn [agent-node arg1 arg2]
                        (aor/emit! agent-node "node1" (str arg1 arg2 "-0"))
                        (aor/emit! agent-node "node2" (str arg2 arg1 "-1"))
                      ))
            (aor/node "node1"
                      ["node3" "node4"]
                      (fn [agent-node arg]
                        (aor/emit! agent-node "node3" (str arg "-a0"))
                        (aor/emit! agent-node "node4" (str arg "-a1"))
                      ))
            (aor/node "node2"
                      ["node5" "node6"]
                      (fn [agent-node arg]
                        (aor/emit! agent-node "node5" (str arg "-b0"))
                        (aor/emit! agent-node "node6" (str arg "-b1"))
                      ))
            (aor/node "node3"
                      nil
                      (fn [agent-node arg]
                      ))
            (aor/node "node4"
                      nil
                      (fn [agent-node arg]
                      ))
            (aor/node "node5"
                      nil
                      (fn [agent-node arg]
                      ))
            (aor/node "node6"
                      nil
                      (fn [agent-node arg]
                        (aor/result! agent-node ["done" arg])
                      ))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-task-global-name "foo")))
     (bind invokes-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-invoke-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-topology-name "foo")))
     (bind {[graph-task-id graph-id] "_agents-topology"}
       (foreign-append! depot (aor-types/->AgentInvoke ["xy" "-z"] 0)))
     (bind {[graph-task-id2 graph-id2] "_agents-topology"}
       (foreign-append! depot (aor-types/->AgentInvoke ["a" "b"] 0)))
     (bind root-invoke-id
       (foreign-select-one [(keypath graph-id) :root-invoke-id]
                           invokes-pstate
                           {:pkey graph-task-id}))
     (bind root-invoke-id2
       (foreign-select-one [(keypath graph-id2) :root-invoke-id]
                           invokes-pstate
                           {:pkey graph-task-id2}))
     (bind res
       (foreign-invoke-query traces-query
                             graph-task-id
                             [[graph-task-id root-invoke-id]]
                             3))

     (bind res10
       (foreign-invoke-query traces-query
                             graph-task-id2
                             [[graph-task-id2 root-invoke-id2]]
                             3))

     (is
      (trace-matches?
       (-> res
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id2
                :target-task-id ?graph-task-id
                :node-name      "node1"
                :args           [{:val "xy-z-0"
                                  :async-op-index nil}]}
               {:invoke-id      !id3
                :target-task-id !id1-t1
                :node-name      "node2"
                :args           [{:val "-zxy-1"
                                  :async-op-index nil}]}]
              :node          "start"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["xy" "-z"]
              :graph-task-id ?graph-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id4
                :target-task-id ?graph-task-id
                :node-name      "node3"
                :args           [{:val "xy-z-0-a0"
                                  :async-op-index nil}]}
               {:invoke-id      !id5
                :target-task-id !id2-t1
                :node-name      "node4"
                :args           [{:val "xy-z-0-a1"
                                  :async-op-index nil}]}]
              :node          "node1"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["xy-z-0"]
              :graph-task-id ?graph-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id6
                :target-task-id !id1-t1
                :node-name      "node5"
                :args           [{:val "-zxy-1-b0"
                                  :async-op-index nil}]}
               {:invoke-id      !id7
                :target-task-id !id3-t1
                :node-name      "node6"
                :args           [{:val "-zxy-1-b1"
                                  :async-op-index nil}]}]
              :node          "node2"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["-zxy-1"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id)
             (= ?graph-task-id graph-task-id)
             (= !id1 root-invoke-id)))))
     (is (= 3
            (-> res
                :invokes-map
                count)))

     (bind res2
       (foreign-invoke-query traces-query
                             graph-task-id
                             (:next-task-invoke-pairs res)
                             3))
     (is
      (trace-matches?
       (-> res2
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node3"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["xy-z-0-a0"]
              :graph-task-id ?graph-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits         []
              :node          "node4"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["xy-z-0-a1"]
              :graph-task-id ?graph-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits         []
              :node          "node5"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["-zxy-1-b0"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id)
             (= ?graph-task-id graph-task-id)))))
     (is (= 3
            (-> res2
                :invokes-map
                count)))


     (bind res3
       (foreign-invoke-query traces-query
                             graph-task-id
                             (:next-task-invoke-pairs res2)
                             3))
     (is
      (trace-matches?
       (-> res3
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node6"
              :async-ops     []
              :result        {:val ["done" "-zxy-1-b1"]}
              :graph-id      ?graph-id
              :input         ["-zxy-1-b1"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id)
             (= ?graph-task-id graph-task-id)))))
     (is (= 1
            (-> res3
                :invokes-map
                count)))
     (is (-> res3
             :next-task-invoke-pairs
             empty?))



     ;; check other invoke trace
     (is
      (trace-matches?
       (-> res10
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id2
                :target-task-id ?graph-task-id
                :node-name      "node1"
                :args           [{:val "ab-0"
                                  :async-op-index nil}]}
               {:invoke-id      !id3
                :target-task-id !id1-t1
                :node-name      "node2"
                :args           [{:val "ba-1"
                                  :async-op-index nil}]}]
              :node          "start"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["a" "b"]
              :graph-task-id ?graph-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id4
                :target-task-id ?graph-task-id
                :node-name      "node3"
                :args           [{:val "ab-0-a0"
                                  :async-op-index nil}]}
               {:invoke-id      !id5
                :target-task-id !id2-t1
                :node-name      "node4"
                :args           [{:val "ab-0-a1"
                                  :async-op-index nil}]}]
              :node          "node1"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["ab-0"]
              :graph-task-id ?graph-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id6
                :target-task-id !id1-t1
                :node-name      "node5"
                :args           [{:val "ba-1-b0"
                                  :async-op-index nil}]}
               {:invoke-id      !id7
                :target-task-id !id3-t1
                :node-name      "node6"
                :args           [{:val "ba-1-b1"
                                  :async-op-index nil}]}]
              :node          "node2"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["ba-1"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id2)
             (= ?graph-task-id graph-task-id2)
             (= !id1 root-invoke-id2)))))
     (is (= 3
            (-> res10
                :invokes-map
                count)))

     (bind res11
       (foreign-invoke-query traces-query
                             graph-task-id
                             (:next-task-invoke-pairs res10)
                             3))
     (is
      (trace-matches?
       (-> res11
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node3"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["ab-0-a0"]
              :graph-task-id ?graph-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits         []
              :node          "node4"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["ab-0-a1"]
              :graph-task-id ?graph-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits         []
              :node          "node5"
              :async-ops     []
              :result        nil
              :graph-id      ?graph-id
              :input         ["ba-1-b0"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id2)
             (= ?graph-task-id graph-task-id2)))))
     (is (= 3
            (-> res11
                :invokes-map
                count)))


     (bind res12
       (foreign-invoke-query traces-query
                             graph-task-id
                             (:next-task-invoke-pairs res11)
                             3))
     (is
      (trace-matches?
       (-> res12
           :invokes-map
           no-time-deltas)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node6"
              :async-ops     []
              :result        {:val ["done" "ba-1-b1"]}
              :graph-id      ?graph-id
              :input         ["ba-1-b1"]
              :graph-task-id ?graph-task-id
             }
       }
       (m/guard
        (and (= ?graph-id graph-id2)
             (= ?graph-task-id graph-task-id2)))))
     (is (= 1
            (-> res12
                :invokes-map
                count)))
     (is (-> res12
             :next-task-invoke-pairs
             empty?))
    )))


(deftest async-emits-test
         ;; TODO: <<<<<>>>>>
         ;;  - emit regular CF, PState queries, PState transforms, and out of
         ;;  band
         ;;  - verify order of resolution (perhaps just through results of
         ;;  PState ops)
         ;;  - check what gets passed to the next node (just accumulate into
         ;;  result)
         ;;  - check node traces
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
