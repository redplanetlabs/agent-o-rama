(ns com.rpl.agent-o-rama.ui.invocations.invocation-flow-test
  "Tests for invocation graph subscriptions, polling, and re-frame data flow."
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.dom]
   [com.rpl.agent-o-rama.ui.events :as events]
   [com.rpl.agent-o-rama.ui.invocations.subs :as inv-subs]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]))

(def invoke-id "test-invoke-1")
(def root-id #uuid "00000000-0000-0000-0000-000000000010")
(def worker-id #uuid "00000000-0000-0000-0000-000000000011")

(defn- sample-nodes []
  {root-id {:node "root"
            :start-time-millis 100
            :finish-time-millis 500}
   worker-id {:node "worker"
              :start-time-millis 200
              :finish-time-millis 400}})

(defn- db-with-graph [nodes & {:keys [is-complete]}]
  (assoc-in aor-rf/default-app-db
            [:invocations-data invoke-id]
            {:status :success
             :graph {:nodes nodes
                     :edges [{:source (str root-id) :target (str worker-id)}]}
             :implicit-edges []
             :root-invoke-id root-id
             :is-complete (boolean is-complete)}))

(deftest invocation-graph-data-sub-test
  (reset! rdb/app-db (db-with-graph (sample-nodes)))
  (is (= (sample-nodes)
         (get-in @rdb/app-db [:invocations-data invoke-id :graph :nodes])))
  (is (= (sample-nodes)
         @(rf/subscribe [:invocation/graph-data invoke-id]))))

(deftest invocation-selected-node-data-test
  (let [nodes (sample-nodes)
        db (-> (db-with-graph nodes)
               (assoc-in [:ui :invocations invoke-id :selected-node-id] worker-id))]
    (reset! rdb/app-db db)
    (let [graph-data (get-in db [:invocations-data invoke-id :graph :nodes])
          selected-id (get-in db [:ui :invocations invoke-id :selected-node-id])
          node-data (get graph-data selected-id)]
      (is (= worker-id selected-id))
      (is (= "worker" (:node (assoc node-data :node-id selected-id))))))

(deftest should-schedule-poll-test
  (testing "poll while agent not complete"
    (let [db (db-with-graph (sample-nodes) :is-complete false)]
      (is (events/should-schedule-poll? db invoke-id false))))

  (testing "poll until graph payload merged"
    (let [db (assoc-in aor-rf/default-app-db
                       [:invocations-data invoke-id]
                       {:status :loading
                        :summary {}
                        :is-complete true})]
      (is (events/should-schedule-poll? db invoke-id true))))

  (testing "drain poll when agent complete but worker in progress"
    (let [nodes (assoc (sample-nodes) worker-id
                       {:node "worker" :start-time-millis 200})
          db (db-with-graph nodes :is-complete true)]
      (is (events/should-schedule-poll? db invoke-id true))))

  (testing "stop poll when agent and all nodes finished"
    (let [db (db-with-graph (sample-nodes) :is-complete true)]
      (is (not (events/should-schedule-poll? db invoke-id true))))))

(deftest invocation-cleanup-test
  (reset! rdb/app-db
          (-> (db-with-graph (sample-nodes))
              (assoc-in [:ui :invocations invoke-id :selected-node-id] worker-id)
              (assoc :current-invocation {:invoke-id invoke-id})))
  (rf/dispatch-sync [:invocation/cleanup {:invoke-id invoke-id}])
  (is (nil? (get-in @rdb/app-db [:invocations-data invoke-id])))
  (is (nil? (get-in @rdb/app-db [:ui :invocations invoke-id]))))

(deftest trace-view-mode-event-test
  (reset! rdb/app-db aor-rf/default-app-db)
  (rf/dispatch-sync [:invocation/set-trace-view-mode invoke-id "gantt"])
  (is (= "gantt" (get-in @rdb/app-db [:ui :invocations invoke-id :trace-view-mode]))))

(deftest build-drawable-graph-string-keys-test
  (let [root-id #uuid "00000000-0000-0000-0000-000000000010"
        worker-id #uuid "00000000-0000-0000-0000-000000000011"
        raw {"00000000-0000-0000-0000-000000000010"
             {:node "root"
              :start-time-millis 100
              :finish-time-millis 500
              :emits [{:invoke-id "00000000-0000-0000-0000-000000000011"}]}
             "00000000-0000-0000-0000-000000000011"
             {:node "worker"
              :start-time-millis 200
              :finish-time-millis 400}}
        {:keys [nodes edges]} (events/build-drawable-graph raw root-id nil)]
    (is (= 2 (count nodes)))
    (is (contains? nodes root-id))
    (is (contains? nodes worker-id))
    (is (= 1 (count edges)))))

(deftest build-drawable-graph-root-without-node-test
  (let [root-id #uuid "00000000-0000-0000-0000-000000000020"
        worker-id #uuid "00000000-0000-0000-0000-000000000021"
        raw {root-id {:started-agg? true
                      :emits [{:invoke-id worker-id}]}
             worker-id {:node "stress-worker"
                        :start-time-millis 100
                        :finish-time-millis 200}}
        {:keys [nodes edges]} (events/build-drawable-graph raw root-id nil)]
    (is (= 1 (count nodes)))
    (is (contains? nodes worker-id))
    (is (= 1 (count edges)))))

(deftest invocation-ui-path-test
  (is (= [:ui :invocations invoke-id :selected-node-id]
         (inv-subs/invocation-ui-path invoke-id :selected-node-id)))))
