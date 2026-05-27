(ns com.rpl.agent-o-rama.impl.ui.rpc.invocations-test
  (:require
   [clojure.test :refer [deftest is testing]]
   [com.rpl.agent-o-rama.impl.ui.rpc.invocations :as inv]))

(def root-id #uuid "00000000-0000-0000-0000-000000000001")
(def child-id #uuid "00000000-0000-0000-0000-000000000002")

(deftest trace-graph-complete-test
  (testing "all reachable drawable nodes must have finish time"
    (let [nodes {root-id {:node "root"
                          :start-time-millis 0
                          :finish-time-millis 100
                          :emits [{:invoke-id child-id}]}
                 child-id {:node "child"
                           :start-time-millis 10
                           :finish-time-millis 50
                           :emits []}}]
      (is (inv/trace-graph-complete? nodes root-id))))

  (testing "in-progress child makes graph incomplete"
    (let [nodes {root-id {:node "root"
                          :start-time-millis 0
                          :finish-time-millis 100
                          :emits [{:invoke-id child-id}]}
                 child-id {:node "child"
                           :start-time-millis 10
                           :emits []}}]
      (is (not (inv/trace-graph-complete? nodes root-id)))))

  (testing "incomplete nodes without :node are skipped"
    (let [phantom-id #uuid "00000000-0000-0000-0000-000000000099"
          nodes {root-id {:node "root"
                          :start-time-millis 0
                          :finish-time-millis 100
                          :emits [{:invoke-id phantom-id}]}
                 phantom-id {}}]
      (is (inv/trace-graph-complete? nodes root-id)))))
