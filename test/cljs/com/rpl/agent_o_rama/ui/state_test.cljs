(ns com.rpl.agent-o-rama.ui.state-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [com.rpl.agent-o-rama.ui.re-frame :as aor-rf]
   [com.rpl.agent-o-rama.ui.events]
   [com.rpl.agent-o-rama.ui.invocations.subs]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [com.rpl.agent-o-rama.ui.dom])) ; Load DOM setup before tests

(deftest test-initial-db
  (let [db aor-rf/default-app-db]
    (is (contains? db :current-invocation))
    (is (contains? (:current-invocation db) :invoke-id))
    (is (contains? (:current-invocation db) :module-id))
    (is (contains? (:current-invocation db) :agent-name))
    (is (contains? db :invocations-data))
    (is (map? (:invocations-data db)))
    (is (contains? db :invocations))
    (let [inv (:invocations db)]
      (is (contains? inv :all-invokes))
      (is (vector? (:all-invokes inv)))
      (is (contains? inv :has-more?))
      (is (boolean? (:has-more? inv)))
      (is (contains? inv :loading?))
      (is (boolean? (:loading? inv))))
    (is (contains? db :ui))
    (let [ui (:ui db)]
      (is (contains? ui :invocations))
      (is (map? (:invocations ui))))))

(deftest test-event-system
  (let [test-event-id ::test-event
        handler-called (atom false)
        handler-fn (fn [db & _args]
                     (reset! handler-called true)
                     db)]
    (rf/reg-event-db test-event-id
      (fn [db event-vec]
        (let [result (apply handler-fn db (rest event-vec))]
          (or result db))))
    (rf/dispatch-sync [test-event-id])
    (is @handler-called "Event handler should be called on dispatch")))

(deftest test-db-set-value-event
  (reset! rdb/app-db aor-rf/default-app-db)
  (let [test-uuid (random-uuid)
        invoke-id "test-invoke"]
    (rf/dispatch-sync [:invocation/select-node invoke-id test-uuid])
    (is (= test-uuid (get-in @rdb/app-db [:ui :invocations invoke-id :selected-node-id]))))
  (reset! rdb/app-db aor-rf/default-app-db)
  (rf/dispatch-sync [:db/set-value [:current-invocation :invoke-id] "invoke-456"])
  (is (= "invoke-456" (get-in @rdb/app-db [:current-invocation :invoke-id]))))

(deftest test-toggle-forking-mode
  (reset! rdb/app-db aor-rf/default-app-db)
  (let [invoke-id "test-invoke"]
    (is (not (get-in @rdb/app-db [:ui :invocations invoke-id :forking-mode?])))
    (rf/dispatch-sync [:ui/toggle-forking-mode invoke-id])
    (is (true? (get-in @rdb/app-db [:ui :invocations invoke-id :forking-mode?])))
    (rf/dispatch-sync [:ui/toggle-forking-mode invoke-id])
    (is (false? (get-in @rdb/app-db [:ui :invocations invoke-id :forking-mode?])))))
