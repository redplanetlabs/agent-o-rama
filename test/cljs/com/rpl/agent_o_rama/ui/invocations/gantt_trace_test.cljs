(ns com.rpl.agent-o-rama.ui.invocations.gantt-trace-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.invocations.gantt-model :as gantt-model]
   [com.rpl.agent-o-rama.ui.invocations.gantt-trace :as gantt]))

(def root-id #uuid "00000000-0000-0000-0000-000000000001")
(def child-a #uuid "00000000-0000-0000-0000-000000000002")
(def child-b #uuid "00000000-0000-0000-0000-000000000003")
(def agg-id #uuid "00000000-0000-0000-0000-000000000004")
(def starter-id #uuid "00000000-0000-0000-0000-000000000005")

(defn- stress-like-graph []
  {root-id {:node "stress-root"
            :start-time-millis 1000
            :finish-time-millis 5000}
   starter-id {:node "stress-fanout"
               :started-agg? true
               :start-time-millis 1100
               :finish-time-millis 2000}
   child-a {:node "stress-worker"
            :start-time-millis 1200
            :finish-time-millis 1800}
   child-b {:node "stress-worker"
            :start-time-millis 1250
            :finish-time-millis 1900}
   agg-id {:node "stress-agg"
           :agg-state {}
           :agg-start-invoke-id starter-id
           :start-time-millis 2000
           :finish-time-millis 4500}})

(defn- stress-like-edges []
  {:real [{:source (str root-id) :target (str starter-id)}
          {:source (str starter-id) :target (str child-a)}
          {:source (str starter-id) :target (str child-b)}
          {:source (str child-a) :target (str agg-id)}
          {:source (str child-b) :target (str agg-id)}]
   :implicit [{:source (str starter-id) :target (str agg-id) :implicit? true}]})

(deftest format-duration-ms-test
  (testing "duration formatting"
    (is (= "—" (gantt/format-duration-ms nil)))
    (is (= "42ms" (gantt/format-duration-ms 42)))
    (is (= "1.50s" (gantt/format-duration-ms 1500)))
    (is (= "2.00m" (gantt/format-duration-ms 120000)))))

(deftest collect-visible-rows-test
  (testing "DFS row order and labels"
    (let [graph (stress-like-graph)
          {:keys [real implicit]} (stress-like-edges)
          children (gantt-model/gantt-children-map graph real implicit)
          rows (gantt-model/collect-visible-rows graph children root-id #{})]
      (is (= 5 (count rows)))
      (is (= "stress-root" (:label (first rows))))
      (is (= #{"stress-root" "stress-fanout" "stress-worker" "stress-agg"}
             (set (map :label rows))))
      (is (= 0 (:depth (first rows))))
      (is (some #(> (:depth %) 0) rows))))

  (testing "collapse hides descendant rows"
    (let [graph (stress-like-graph)
          {:keys [real implicit]} (stress-like-edges)
          children (gantt-model/gantt-children-map graph real implicit)
          rows-collapsed (gantt-model/collect-visible-rows graph children root-id #{root-id})]
      (is (= 1 (count rows-collapsed)))
      (is (= "stress-root" (:label (first rows-collapsed)))))))

(deftest gantt-children-map-fan-in-test
  (testing "agg appears under only one parent when multiple edges exist"
    (let [graph (stress-like-graph)
          {:keys [real implicit]} (stress-like-edges)
          children (gantt-model/gantt-children-map graph real implicit)
          parents-with-agg (->> children
                                (filter (fn [[_ child-ids]]
                                          (some #(= % agg-id) child-ids)))
                                (map first)
                                vec)]
      (is (= 1 (count parents-with-agg))
          "agg should be listed once in the children map")
      (is (contains? #{starter-id child-a child-b}
                     (first parents-with-agg))))))

(deftest trace-time-bounds-test
  (testing "bounds span finished nodes"
    (let [graph (stress-like-graph)
          {:keys [real implicit]} (stress-like-edges)
          children (gantt-model/gantt-children-map graph real implicit)
          rows (gantt-model/collect-visible-rows graph children root-id #{})
          [t0 t1] (gantt-model/trace-time-bounds rows 99999)]
      (is (= 1000 t0))
      (is (= 5000 t1))))

  (testing "in-progress row extends bounds to now-ms"
    (let [in-progress-id #uuid "00000000-0000-0000-0000-000000000099"
          graph {root-id {:node "root"
                          :start-time-millis 1000
                          :finish-time-millis 2000}
                 in-progress-id {:node "worker"
                                 :start-time-millis 1500}}
          edges [{:source (str root-id) :target (str in-progress-id)}]
          children (gantt-model/gantt-children-map graph edges [])
          rows (gantt-model/collect-visible-rows graph children root-id #{})
          now 5000
          [t0 t1] (gantt-model/trace-time-bounds rows now)]
      (is (= 1000 t0))
      (is (= now t1)))))

(deftest empty-graph-rows-test
  (testing "no root yields empty rows"
    (is (empty? (gantt-model/collect-visible-rows {} {} nil #{})))))
