(ns com.rpl.tracing-test
  (:use [clojure.test]
        [com.rpl.test-helpers]
        [com.rpl.rama]
        [com.rpl.rama.path])
  (:require
   [com.rpl.agent-o-rama :as aor]
   [com.rpl.agent-o-rama.impl.pobjects :as po]
   [com.rpl.agent-o-rama.impl.queries :as queries]
   [com.rpl.agent-o-rama.impl.types :as aor-types]
   [com.rpl.rama.aggs :as aggs]
   [com.rpl.rama.test :as rtest]
   [meander.epsilon :as m])
  (:import
   [com.rpl.rama.helpers
    TopologyUtils]))

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
          :nested-ops [{:a 2 :start-time-millis 10 :finish-time-millis 20}
                       {:start-time-millis 11 :finish-time-millis 12}]}
      :b {:start-time-millis 2 :finish-time-millis 6 :z 1 :x 9}
      :c {:q 3}})
    {:a {:delta-millis 2
         :q 1
         :nested-ops [{:a 2 :delta-millis 10}
                      {:delta-millis 1}]}
     :b {:delta-millis 4 :z 1 :x 9}
     :c {:q 3}}
   )))


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
                      (po/agent-depot-name "foo")))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))
     (bind [agent-task-id agent-id]
       (invoke-agent-and-wait! depot root-pstate ["xyz"]))
     (bind root-invoke-id
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind res
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root-invoke-id]]
                             10000))

     (is (empty? (:next-task-invoke-pairs res)))
     (is
      (trace-matches?
       (-> res
           :invokes-map
           trace-no-times)
       {!id1  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id2
                 :target-task-id ?agent-task-id
                 :node-name      "node1"
                 :args           ["xyz-0"]}]
               :node          "start"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz"]
               :agent-task-id ?agent-task-id
              }
        !id2  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id3
                 :target-task-id ?agent-task-id
                 :node-name      "node2"
                 :args           ["xyz-0-00"]}
                {:invoke-id      !id4
                 :target-task-id !id2-t1
                 :node-name      "node2"
                 :args           ["xyz-0-01"]}]
               :node          "node1"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz-0"]
               :agent-task-id ?agent-task-id}
        !id3  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id5
                 :target-task-id ?agent-task-id
                 :node-name      "node3"
                 :args           ["xyz-0-00-000"]}]
               :node          "node2"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz-0-00"]
               :agent-task-id ?agent-task-id}
        !id5  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id6
                 :target-task-id ?agent-task-id
                 :node-name      "node4"
                 :args           [1]}
                {:invoke-id      !id7
                 :target-task-id !id5-t1
                 :node-name      "node4"
                 :args           [1]}
                {:invoke-id      !id8
                 :target-task-id !id5-t2
                 :node-name      "node4"
                 :args           [1]}]
               :started-agg?  true
               :node          "node3"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz-0-00-000"]
               :agent-task-id ?agent-task-id}
        !id6  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id9
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
        !id9  {:invoked-agg-invoke-id !agg0}
        !id7  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id10
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
        !id10 {:invoked-agg-invoke-id !agg0}
        !id8  {:agg-invoke-id !agg0
               :emits
               [{:invoke-id      !id11
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
        !id11 {:invoked-agg-invoke-id !agg0}
        !id4  {:agg-invoke-id nil
               :emits
               [{:invoke-id      !id12
                 :target-task-id ?agent-task-id
                 :node-name      "node3"
                 :args           ["xyz-0-01-000"]}]
               :node          "node2"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz-0-01"]
               :agent-task-id ?agent-task-id}
        !id12 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id13
                 :target-task-id ?agent-task-id
                 :node-name      "node4"
                 :args           [1]}
                {:invoke-id      !id14
                 :target-task-id !id12-t1
                 :node-name      "node4"
                 :args           [1]}
                {:invoke-id      !id15
                 :target-task-id !id12-t2
                 :node-name      "node4"
                 :args           [1]}]
               :started-agg?  true
               :node          "node3"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         ["xyz-0-01-000"]
               :agent-task-id ?agent-task-id}
        !id13 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id16
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
        !id16 {:invoked-agg-invoke-id !agg1}
        !id14 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id17
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
        !id17 {:invoked-agg-invoke-id !agg1}
        !id15 {:agg-invoke-id !agg1
               :emits
               [{:invoke-id      !id18
                 :target-task-id ?agent-task-id
                 :node-name      "agg"
                 :args           ["1-a"]}]
               :node          "node4"
               :nested-ops    []
               :result        nil
               :agent-id      ?agent-id
               :input         [1]
               :agent-task-id ?agent-task-id}
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
               :nested-ops      []
               :agg-ack-val     0
               :result          {:val [["1-a" "1-a" "1-a"]
                                       "xyz-0-00-000-0000"]}
               :agg-finished?   true
               :agent-id        ?agent-id
               :agg-state       ["1-a" "1-a" "1-a"]
               :input           [["1-a" "1-a" "1-a"]
                                 "xyz-0-00-000-0000"]
               :agg-start-invoke-id !id5
               :agent-task-id   ?agent-task-id}
        !agg1 {:agg-invoke-id   nil
               :agg-input-count 3
               :agg-start-res   "xyz-0-01-000-0000"
               :emits           []
               :node            "agg"
               :agg-inputs-first-10
               [{:invoke-id !id16' :args ["1-a"]}
                {:invoke-id !id17' :args ["1-a"]}
                {:invoke-id !id18' :args ["1-a"]}]
               :nested-ops      []
               :agg-ack-val     0
               :result          {:val [["1-a" "1-a" "1-a"]
                                       "xyz-0-01-000-0000"]}
               :agg-finished?   true
               :agent-id        ?agent-id
               :agg-state       ["1-a" "1-a" "1-a"]
               :input           [["1-a" "1-a" "1-a"]
                                 "xyz-0-01-000-0000"]
               :agg-start-invoke-id !id12
               :agent-task-id   ?agent-task-id}
       }
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)
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
                      (po/agent-depot-name "foo")))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))
     (bind [agent-task-id agent-id]
       (invoke-agent-and-wait! depot root-pstate ["xy" "-z"]))
     (bind [agent-task-id2 agent-id2]
       (invoke-agent-and-wait! depot root-pstate ["a" "b"]))
     (bind root-invoke-id
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind root-invoke-id2
       (foreign-select-one [(keypath agent-id2) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id2}))
     (bind res
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root-invoke-id]]
                             3))

     (bind res10
       (foreign-invoke-query traces-query
                             agent-task-id2
                             [[agent-task-id2 root-invoke-id2]]
                             3))

     (is
      (trace-matches?
       (-> res
           :invokes-map
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id2
                :target-task-id ?agent-task-id
                :node-name      "node1"
                :args           ["xy-z-0"]}
               {:invoke-id      !id3
                :target-task-id !id1-t1
                :node-name      "node2"
                :args           ["-zxy-1"]}]
              :node          "start"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["xy" "-z"]
              :agent-task-id ?agent-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id4
                :target-task-id ?agent-task-id
                :node-name      "node3"
                :args           ["xy-z-0-a0"]}
               {:invoke-id      !id5
                :target-task-id !id2-t1
                :node-name      "node4"
                :args           ["xy-z-0-a1"]}]
              :node          "node1"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["xy-z-0"]
              :agent-task-id ?agent-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id6
                :target-task-id !id1-t1
                :node-name      "node5"
                :args           ["-zxy-1-b0"]}
               {:invoke-id      !id7
                :target-task-id !id3-t1
                :node-name      "node6"
                :args           ["-zxy-1-b1"]}]
              :node          "node2"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["-zxy-1"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)
             (= !id1 root-invoke-id)))))
     (is (= 3
            (-> res
                :invokes-map
                count)))

     (bind res2
       (foreign-invoke-query traces-query
                             agent-task-id
                             (:next-task-invoke-pairs res)
                             3))
     (is
      (trace-matches?
       (-> res2
           :invokes-map
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node3"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["xy-z-0-a0"]
              :agent-task-id ?agent-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits         []
              :node          "node4"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["xy-z-0-a1"]
              :agent-task-id ?agent-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits         []
              :node          "node5"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["-zxy-1-b0"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))))
     (is (= 3
            (-> res2
                :invokes-map
                count)))


     (bind res3
       (foreign-invoke-query traces-query
                             agent-task-id
                             (:next-task-invoke-pairs res2)
                             3))
     (is
      (trace-matches?
       (-> res3
           :invokes-map
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node6"
              :nested-ops    []
              :result        {:val ["done" "-zxy-1-b1"]}
              :agent-id      ?agent-id
              :input         ["-zxy-1-b1"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))))
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
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id2
                :target-task-id ?agent-task-id
                :node-name      "node1"
                :args           ["ab-0"]}
               {:invoke-id      !id3
                :target-task-id !id1-t1
                :node-name      "node2"
                :args           ["ba-1"]}]
              :node          "start"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["a" "b"]
              :agent-task-id ?agent-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id4
                :target-task-id ?agent-task-id
                :node-name      "node3"
                :args           ["ab-0-a0"]}
               {:invoke-id      !id5
                :target-task-id !id2-t1
                :node-name      "node4"
                :args           ["ab-0-a1"]}]
              :node          "node1"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["ab-0"]
              :agent-task-id ?agent-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits
              [{:invoke-id      !id6
                :target-task-id !id1-t1
                :node-name      "node5"
                :args           ["ba-1-b0"]}
               {:invoke-id      !id7
                :target-task-id !id3-t1
                :node-name      "node6"
                :args           ["ba-1-b1"]}]
              :node          "node2"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["ba-1"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id2)
             (= ?agent-task-id agent-task-id2)
             (= !id1 root-invoke-id2)))))
     (is (= 3
            (-> res10
                :invokes-map
                count)))

     (bind res11
       (foreign-invoke-query traces-query
                             agent-task-id
                             (:next-task-invoke-pairs res10)
                             3))
     (is
      (trace-matches?
       (-> res11
           :invokes-map
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node3"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["ab-0-a0"]
              :agent-task-id ?agent-task-id
             }
        !id2 {:agg-invoke-id nil
              :emits         []
              :node          "node4"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["ab-0-a1"]
              :agent-task-id ?agent-task-id
             }
        !id3 {:agg-invoke-id nil
              :emits         []
              :node          "node5"
              :nested-ops    []
              :result        nil
              :agent-id      ?agent-id
              :input         ["ba-1-b0"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id2)
             (= ?agent-task-id agent-task-id2)))))
     (is (= 3
            (-> res11
                :invokes-map
                count)))


     (bind res12
       (foreign-invoke-query traces-query
                             agent-task-id
                             (:next-task-invoke-pairs res11)
                             3))
     (is
      (trace-matches?
       (-> res12
           :invokes-map
           trace-no-times)
       {!id1 {:agg-invoke-id nil
              :emits         []
              :node          "node6"
              :nested-ops    []
              :result        {:val ["done" "ba-1-b1"]}
              :agent-id      ?agent-id
              :input         ["ba-1-b1"]
              :agent-task-id ?agent-task-id
             }
       }
       (m/guard
        (and (= ?agent-id agent-id2)
             (= ?agent-task-id agent-task-id2)))))
     (is (= 1
            (-> res12
                :invokes-map
                count)))
     (is (-> res12
             :next-task-invoke-pairs
             empty?))
    )))

(def +timing-sum
  (accumulator
   (fn [v]
     (TopologyUtils/advanceSimTime v)
     (term #(+ % v)))
   :init-fn
   (fn [] 0)
  ))

(deftest node-timings-test
  (with-open [ipc (rtest/create-ipc)
              _ (TopologyUtils/startSimTime)]
    (letlocals
     (bind module
       (aor/agentmodule
        [topology]
        (-> topology
            (aor/new-agent "foo")
            (aor/node "start"
                      "node1"
                      (fn [agent-node]
                        (TopologyUtils/advanceSimTime 5)
                        (aor/emit! agent-node "node1")
                      ))
            (aor/agg-start-node "node1"
                                "agg"
                                (fn [agent-node]
                                  (TopologyUtils/advanceSimTime 6)
                                  (aor/emit! agent-node "agg" 1)
                                  (aor/emit! agent-node "agg" 2)
                                ))
            (aor/agg-node "agg"
                          nil
                          +timing-sum
                          (fn [agent-node agg node-start-res]
                            (TopologyUtils/advanceSimTime 10)
                            (aor/result! agent-node agg)))
        )))
     (rtest/launch-module! ipc module {:tasks 4 :threads 2})
     (bind module-name (get-module-name module))
     (bind depot
       (foreign-depot ipc
                      module-name
                      (po/agent-depot-name "foo")))
     (bind root-pstate
       (foreign-pstate ipc
                       module-name
                       (po/agent-root-task-global-name "foo")))
     (bind traces-query
       (foreign-query ipc
                      module-name
                      (queries/tracing-query-name "foo")))
     (bind [agent-task-id agent-id]
       (invoke-agent-and-wait! depot root-pstate []))
     (bind root-invoke-id
       (foreign-select-one [(keypath agent-id) :root-invoke-id]
                           root-pstate
                           {:pkey agent-task-id}))
     (bind res
       (foreign-invoke-query traces-query
                             agent-task-id
                             [[agent-task-id root-invoke-id]]
                             100))

     (is
      (trace-matches?
       (:invokes-map res)
       {!id1 {:agg-invoke-id     nil
              :emits             [{:invoke-id      !id2
                                   :target-task-id ?agent-task-id
                                   :node-name      "node1"
                                   :args           []}]
              :node              "start"
              :nested-ops        []
              :result            nil
              :agent-id          ?agent-id
              :input             []
              :agent-task-id     ?agent-task-id
              :start-time-millis 0
              :finish-time-millis 5
             }
        !id2 {:started-agg?      true
              :agg-invoke-id     !id5
              :emits             [{:invoke-id      !id3
                                   :target-task-id ?agent-task-id
                                   :node-name      "agg"
                                   :args           [1]}
                                  {:invoke-id      !id4
                                   :target-task-id ?agent-task-id
                                   :node-name      "agg"
                                   :args           [2]}]
              :node              "node1"
              :nested-ops        []
              :result            nil
              :agent-id          ?agent-id
              :input             []
              :agent-task-id     ?agent-task-id
              :start-time-millis 5
              :finish-time-millis 11
             }
        !id3 {:invoked-agg-invoke-id !id5}
        !id4 {:invoked-agg-invoke-id !id5}
        !id5 {:agg-invoke-id      nil
              :emits              []
              :agg-input-count    2
              :agg-inputs-first-10
              [{:invoke-id !id3 :args [1]}
               {:invoke-id !id4 :args [2]}]
              :agg-start-res      nil
              :node               "agg"
              :agg-ack-val        0
              :nested-ops         []
              :agent-id           ?agent-id
              :input              [3 nil]
              :agent-task-id      ?agent-task-id
              :agg-state          3
              :agg-start-invoke-id !id2
              :agg-finished?      true
              :start-time-millis  14
              :finish-time-millis 24
              :result             {:val 3 :failure? false}
             }
       }
       (m/guard
        (and (= ?agent-id agent-id)
             (= ?agent-task-id agent-task-id)))))
    )))
