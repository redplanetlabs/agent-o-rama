(ns com.rpl.agent-o-rama.ui.trace-analytics-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.trace-analytics :as ta]))

;;; Test data generators

(defn mock-aggregated-stats
  "Create mock aggregated stats (already computed by backend)"
  ([ops-map token-counts]
   (mock-aggregated-stats ops-map token-counts {}))
  ([ops-map token-counts node-stats]
   {:nested-op-stats    ops-map
    :input-token-count  (:input token-counts 0)
    :output-token-count (:output token-counts 0)
    :total-token-count  (:total token-counts 0)
    :node-stats         node-stats}))

(defn mock-subagent-basic-stats
  "Create mock basic stats for a sub-agent"
  ([ops-map token-counts]
   (mock-subagent-basic-stats ops-map token-counts {}))
  ([ops-map token-counts node-stats]
   {:nested-op-stats    ops-map
    :input-token-count  (:input token-counts 0)
    :output-token-count (:output token-counts 0)
    :total-token-count  (:total token-counts 0)
    :node-stats         node-stats}))

(defn mock-subagent-stats
  [count basic-stats]
  {:count       count
   :basic-stats basic-stats})

(defn mock-agent-ref
  [module-name agent-name]
  {:module-name module-name
   :agent-name  agent-name})

;;; Tests for helper functions

(deftest has-operations-test
  (testing "has-operations?"
    (testing "returns true when operations exist"
      (let [aggregated-ops {:nested-op-stats
                            {:db-read    {:count 5 :total-time-millis 100}
                             :db-write   {:count 3 :total-time-millis 50}
                             :model-call {:count 0 :total-time-millis 0}}}]
        (is (ta/has-operations? aggregated-ops [:db-read :db-write]))
        (is (ta/has-operations? aggregated-ops [:db-read]))
        (is (ta/has-operations? aggregated-ops [:db-write]))))

    (testing "returns false when no operations exist"
      (let [aggregated-ops {:nested-op-stats
                            {:db-read    {:count 0 :total-time-millis 0}
                             :db-write   {:count 0 :total-time-millis 0}
                             :model-call {:count 0 :total-time-millis 0}}}]
        (is (not (ta/has-operations? aggregated-ops [:db-read :db-write])))
        (is (not (ta/has-operations? aggregated-ops [:model-call])))))

    (testing "returns true when at least one operation exists"
      (let [aggregated-ops {:nested-op-stats
                            {:db-read    {:count 5 :total-time-millis 100}
                             :db-write   {:count 0 :total-time-millis 0}
                             :model-call {:count 0 :total-time-millis 0}}}]
        (is (ta/has-operations? aggregated-ops [:db-read :db-write]))
        (is (ta/has-operations? aggregated-ops [:db-read :model-call]))))

    (testing "handles missing operation keys"
      (let [aggregated-ops {:nested-op-stats
                            {:db-read {:count 5 :total-time-millis 100}}}]
        (is (ta/has-operations? aggregated-ops [:db-read]))
        (is (not (ta/has-operations? aggregated-ops [:missing-key])))
        (is (ta/has-operations? aggregated-ops [:db-read :missing-key]))))))

(deftest get-op-stats-test
  (testing "get-op-stats"
    (testing "returns stats for existing operation"
      (let [basic-stats {:nested-op-stats {:db-read {:count 5 :total-time-millis 100}}}
            result      (ta/get-op-stats basic-stats :db-read)]
        (is (= 5 (:count result)))
        (is (= 100 (:total-time-millis result)))))

    (testing "returns default empty stats for missing operation"
      (let [basic-stats {:nested-op-stats {:db-read {:count 5 :total-time-millis 100}}}
            result      (ta/get-op-stats basic-stats :missing)]
        (is (= 0 (:count result)))
        (is (= 0 (:total-time-millis result)))))

    (testing "returns default empty stats for empty map"
      (let [result (ta/get-op-stats {} :db-read)]
        (is (= 0 (:count result)))
        (is (= 0 (:total-time-millis result)))))))

(deftest format-op-stats-test
  (testing "format-op-stats"
    (testing "formats stats correctly"
      (is (= "5x  ·  100ms" (:display (ta/format-op-stats {:count 5 :total-time-millis 100}))))
      (is (= "0x  ·  0ms" (:display (ta/format-op-stats {:count 0 :total-time-millis 0})))))

    (testing "handles nil time"
      (is (= "5x  ·  0ms" (:display (ta/format-op-stats {:count 5 :total-time-millis nil})))))))

;;; Integration tests with aggregated stats

(deftest aggregated-stats-usage-test
  (testing "using pre-aggregated stats from backend"
    (let [;; Backend provides aggregated stats
          aggregated-ops {:db-read     {:count 7 :total-time-millis 140}
                          :db-write    {:count 3 :total-time-millis 60}
                          :store-read  {:count 10 :total-time-millis 200}
                          :store-write {:count 5 :total-time-millis 100}
                          :model-call  {:count 5 :total-time-millis 1250}
                          :tool-call   {:count 1 :total-time-millis 50}
                          :agent-call  {:count 1 :total-time-millis 200}}

          basic-stats    (mock-aggregated-stats aggregated-ops
                                                {:input 225 :output 115 :total 340})]

      ;; Verify we can access aggregated stats directly
      (is (= 7 (get-in basic-stats [:nested-op-stats :db-read :count])))
      (is (= 5 (get-in basic-stats [:nested-op-stats :model-call :count])))

      ;; Verify has-operations? works with aggregated stats
      (is (ta/has-operations? basic-stats [:db-read :db-write]))
      (is (ta/has-operations? basic-stats [:model-call]))
      (is (ta/has-operations? basic-stats [:tool-call :agent-call]))

      ;; Verify get-op-stats works with aggregated stats
      (let [get-op (partial ta/get-op-stats basic-stats)]
        (is (= 7 (:count (get-op :db-read))))
        (is (= 140 (:total-time-millis (get-op :db-read))))
        (is (= 5 (:count (get-op :model-call))))
        (is (= 1250 (:total-time-millis (get-op :model-call))))))))

(deftest subagent-display-data-structure-test
  (testing "subagent display data structure"
    (testing "has-subagents? logic"
      (let [;; Sub-agent stats (not aggregated, just for that sub-agent)
            sub-ops            {:model-call {:count 2 :total-time-millis 300}
                                :db-read    {:count 3 :total-time-millis 50}}
            sub-basic          (mock-subagent-basic-stats sub-ops {:input 50 :output 25 :total 75})
            sub-stats          (mock-subagent-stats 2 sub-basic)
            agent-ref          (mock-agent-ref "example-module" "example-agent")

            ;; Main stats with sub-agents
            stats-with-subagents {:subagent-stats {agent-ref sub-stats}}
            stats-no-subagents {:subagent-stats {}}
            stats-nil-subagents {:subagent-stats nil}]

        ;; Test with sub-agents
        (let [subagent-stats-map (:subagent-stats stats-with-subagents)]
          (is (some? subagent-stats-map))
          (is (= 1 (count subagent-stats-map)))
          (is (pos? (count subagent-stats-map))))

        ;; Test without sub-agents (empty map)
        (let [subagent-stats-map (:subagent-stats stats-no-subagents)]
          (is (some? subagent-stats-map))
          (is (= 0 (count subagent-stats-map)))
          (is (not (pos? (count subagent-stats-map)))))

        ;; Test with nil sub-agents
        (let [subagent-stats-map (:subagent-stats stats-nil-subagents)]
          (is (nil? subagent-stats-map)))))

    (testing "sub-agent data structure completeness"
      (let [sub-ops   {:model-call  {:count 2 :total-time-millis 300}
                       :db-read     {:count 3 :total-time-millis 50}
                       :store-write {:count 1 :total-time-millis 20}}
            sub-basic (mock-subagent-basic-stats sub-ops {:input 50 :output 25 :total 75})
            sub-stats (mock-subagent-stats 2 sub-basic)
            agent-ref (mock-agent-ref "example-module" "example-agent")]

        ;; Verify agent-ref structure
        (is (= "example-module" (:module-name agent-ref)))
        (is (= "example-agent" (:agent-name agent-ref)))

        ;; Verify sub-stats structure
        (is (= 2 (:count sub-stats)))
        (is (some? (:basic-stats sub-stats)))

        ;; Verify basic-stats has nested-op-stats
        (let [basic-stats (:basic-stats sub-stats)]
          (is (some? (:nested-op-stats basic-stats)))
          (is (= 50 (:input-token-count basic-stats)))
          (is (= 25 (:output-token-count basic-stats)))
          (is (= 75 (:total-token-count basic-stats)))

          ;; Verify we can access nested ops directly
          (is (= 2 (get-in basic-stats [:nested-op-stats :model-call :count])))
          (is (= 3 (get-in basic-stats [:nested-op-stats :db-read :count])))
          (is (= 1 (get-in basic-stats [:nested-op-stats :store-write :count]))))))

    (testing "multiple sub-agents structure"
      (let [sub1-ops           {:model-call {:count 1 :total-time-millis 200}}
            sub1-basic         (mock-subagent-basic-stats sub1-ops {:input 30 :output 15 :total 45})
            sub1-stats         (mock-subagent-stats 1 sub1-basic)

            sub2-ops           {:db-read {:count 5 :total-time-millis 100}}
            sub2-basic         (mock-subagent-basic-stats sub2-ops {:input 40 :output 20 :total 60})
            sub2-stats         (mock-subagent-stats 3 sub2-basic)

            agent-ref1         (mock-agent-ref "module-a" "agent-1")
            agent-ref2         (mock-agent-ref "module-b" "agent-2")

            subagent-stats-map {agent-ref1 sub1-stats
                                agent-ref2 sub2-stats}]

        (is (= 2 (count subagent-stats-map)))

        ;; Verify we can iterate over the map
        (doseq [[agent-ref sa-stats] subagent-stats-map]
          (is (some? (:module-name agent-ref)))
          (is (some? (:agent-name agent-ref)))
          (is (number? (:count sa-stats)))
          (is (some? (:basic-stats sa-stats))))))))

(deftest node-stats-display-test
  ;; Test that node stats are properly structured and accessible
  (testing "node-stats display data"
    (testing "basic stats with node-stats"
      (let [node-stats  {"start"  {:count 1 :total-time-millis 100}
                         "node1"  {:count 1 :total-time-millis 250}
                         "finish" {:count 1 :total-time-millis 50}}
            basic-stats (mock-aggregated-stats {} {} node-stats)]

        (is (some? (:node-stats basic-stats)))
        (is (= 3 (count (:node-stats basic-stats))))
        (is (= 100 (get-in basic-stats [:node-stats "start" :total-time-millis])))
        (is (= 1 (get-in basic-stats [:node-stats "start" :count])))))

    (testing "basic stats without node-stats"
      (let [basic-stats (mock-aggregated-stats {} {})]
        (is (= {} (:node-stats basic-stats)))
        (is (empty? (:node-stats basic-stats)))))

    (testing "node stats sorting and formatting"
      (let [node-stats  {"z-node" {:count 1 :total-time-millis 100}
                         "a-node" {:count 2 :total-time-millis 200}
                         "m-node" {:count 3 :total-time-millis 300}}
            basic-stats (mock-aggregated-stats {} {} node-stats)
            sorted-nodes (sort-by first (:node-stats basic-stats))]

        (is (= ["a-node" "m-node" "z-node"] (map first sorted-nodes)))
        (is (= "1x  ·  100ms" (:display (ta/format-op-stats (second (first (filter #(= "z-node" (first %)) sorted-nodes)))))))
        (is (= "2x  ·  200ms" (:display (ta/format-op-stats (second (first (filter #(= "a-node" (first %)) sorted-nodes)))))))
        (is (= "3x  ·  300ms" (:display (ta/format-op-stats (second (first (filter #(= "m-node" (first %)) sorted-nodes)))))))))

    (testing "subagent stats with node-stats"
      (let [node-stats  {"sub-start" {:count 2 :total-time-millis 150}
                         "sub-end"   {:count 2 :total-time-millis 75}}
            sub-ops     {:model-call {:count 2 :total-time-millis 300}}
            sub-basic   (mock-subagent-basic-stats sub-ops {:input 50 :output 25 :total 75} node-stats)
            sub-stats   (mock-subagent-stats 2 sub-basic)]

        (is (some? (get-in sub-stats [:basic-stats :node-stats])))
        (is (= 2 (count (get-in sub-stats [:basic-stats :node-stats]))))
        (is (= 150 (get-in sub-stats [:basic-stats :node-stats "sub-start" :total-time-millis])))))))
