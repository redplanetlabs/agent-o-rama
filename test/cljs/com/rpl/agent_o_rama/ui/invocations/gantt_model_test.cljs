(ns com.rpl.agent-o-rama.ui.invocations.gantt-model-test
  (:require
   [cljs.test :refer-macros [deftest is testing]]
   [com.rpl.agent-o-rama.ui.invocations.gantt-model :as model]))

(def root-id #uuid "00000000-0000-0000-0000-000000000001")
(def start-id #uuid "00000000-0000-0000-0000-000000000002")
(def branch-a-id #uuid "00000000-0000-0000-0000-000000000003")
(def branch-a-leaf-id #uuid "00000000-0000-0000-0000-000000000004")
(def branch-b-id #uuid "00000000-0000-0000-0000-000000000005")
(def agg-id #uuid "00000000-0000-0000-0000-000000000006")
(def after-agg-id #uuid "00000000-0000-0000-0000-000000000007")

(def graph-data
  {root-id {:node "root"
            :start-time-millis 0
            :finish-time-millis 100}
   start-id {:node "fan-out"
             :started-agg? true
             :agg-invoke-id agg-id
             :start-time-millis 5
             :finish-time-millis 10}
   branch-a-id {:node "branch-a"
                :start-time-millis 20
                :finish-time-millis 30}
   branch-a-leaf-id {:node "branch-a-leaf"
                     :start-time-millis 21
                     :finish-time-millis 22}
   branch-b-id {:node "branch-b"
                :start-time-millis 25
                :finish-time-millis 35}
   agg-id {:node "collect"
           :agg-state {:items []}
           :agg-start-invoke-id start-id
           :start-time-millis 60
           :finish-time-millis 70}
   after-agg-id {:node "after-agg"
                 :start-time-millis 75
                 :finish-time-millis 80}})

(def real-edges
  [{:id "root-start" :source (str root-id) :target (str start-id)}
   {:id "start-a" :source (str start-id) :target (str branch-a-id)}
   {:id "a-leaf" :source (str branch-a-id) :target (str branch-a-leaf-id)}
   {:id "start-b" :source (str start-id) :target (str branch-b-id)}
   {:id "agg-after" :source (str agg-id) :target (str after-agg-id)}])

(def implicit-edges
  [{:id "a-agg" :source (str branch-a-id) :target (str agg-id) :implicit? true}
   {:id "b-agg" :source (str branch-b-id) :target (str agg-id) :implicit? true}])

(deftest aggregation-finalizer-is-owned-by-start-node
  (testing "the Gantt tree uses the agg start as the finalizer parent"
    (let [{:keys [children-map rows]} (model/build-row-model graph-data
                                                             real-edges
                                                             implicit-edges
                                                             root-id
                                                             #{})]
      (is (= [branch-a-id branch-b-id agg-id]
             (get children-map start-id)))
      (is (= [branch-a-leaf-id]
             (get children-map branch-a-id)))
      (is (not (some #(= agg-id %) (get children-map branch-a-id))))
      (is (not (some #(= agg-id %) (get children-map branch-b-id))))
      (is (= [root-id start-id branch-a-id branch-a-leaf-id branch-b-id agg-id after-agg-id]
             (mapv :node-id rows)))
      (is (= [0 1 2 3 2 2 3]
             (mapv :depth rows))))))

(deftest collapse-state-is-applied-to-the-data-model
  (testing "collapsing a branch hides only that branch's descendants"
    (let [rows (:rows (model/build-row-model graph-data
                                             real-edges
                                             implicit-edges
                                             root-id
                                             #{branch-a-id}))]
      (is (= [root-id start-id branch-a-id branch-b-id agg-id after-agg-id]
             (mapv :node-id rows)))))

  (testing "collapsing the aggregation start hides the aggregation region"
    (let [rows (:rows (model/build-row-model graph-data
                                             real-edges
                                             implicit-edges
                                             root-id
                                             #{start-id}))]
      (is (= [root-id start-id]
             (mapv :node-id rows))))))

(deftest row-model-keeps-timing-pure
  (let [rows (:rows (model/build-row-model graph-data
                                           real-edges
                                           implicit-edges
                                           root-id
                                           #{}))]
    (is (= [0 100] (model/trace-time-bounds rows 50)))
    (is (= 100 (model/total-root-ms rows)))))

(deftest rows-include-descendant-time-summary
  (let [rows (:rows (model/build-row-model graph-data
                                           real-edges
                                           implicit-edges
                                           root-id
                                           #{}))
        rows-by-id (into {} (map (juxt :node-id identity) rows))]
    (is (= {:start-time-millis 20
            :finish-time-millis 80
            :in-progress? false}
           (:descendant-time-summary (get rows-by-id start-id))))
    (is (= {:start-time-millis 21
            :finish-time-millis 22
            :in-progress? false}
           (:descendant-time-summary (get rows-by-id branch-a-id))))
    (is (nil? (:descendant-time-summary (get rows-by-id after-agg-id))))))

(deftest collapsed-descendant-times-extend-visible-bounds
  (let [parent-id #uuid "00000000-0000-0000-0000-000000000101"
        child-id #uuid "00000000-0000-0000-0000-000000000102"
        grandchild-id #uuid "00000000-0000-0000-0000-000000000103"
        graph {parent-id {:node "parent"
                          :start-time-millis 0
                          :finish-time-millis 10}
               child-id {:node "child"
                         :start-time-millis 20
                         :finish-time-millis 40}
               grandchild-id {:node "grandchild"
                              :start-time-millis 50}}
        edges [{:source (str parent-id) :target (str child-id)}
               {:source (str child-id) :target (str grandchild-id)}]
        rows (:rows (model/build-row-model graph
                                           edges
                                           []
                                           parent-id
                                           #{parent-id}))
        parent-row (first rows)]
    (is (= [parent-id] (mapv :node-id rows)))
    (is (= {:start-time-millis 20
            :finish-time-millis 40
            :in-progress? true}
           (:descendant-time-summary parent-row)))
    (is (= [0 100] (model/trace-time-bounds rows 100)))))
