(ns com.rpl.agent-o-rama.ui.state-test
  (:require
   [cljs.test :refer-macros [deftest is]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.agent-o-rama.ui.re-frame]
   [com.rpl.agent-o-rama.ui.events]
   [re-frame.core :as rf]
   [re-frame.db :as rdb]
   [com.rpl.agent-o-rama.ui.dom])) ; Load DOM setup before tests

(deftest test-initial-db
  (let [db state/initial-db]
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
      (is (contains? ui :forking-mode?))
      (is (boolean? (:forking-mode? ui))))))

(deftest test-event-system
  (let [test-event-id ::test-event
        handler-called (atom false)
        handler-fn (fn [db & _args]
                     (reset! handler-called true)
                     db)]
    (state/reg-event test-event-id handler-fn)
    (rf/dispatch-sync [test-event-id])
    (is @handler-called "Event handler should be called on dispatch")))

(deftest test-db-set-value-event
  (state/reset-db!)
  (let [test-uuid (random-uuid)]
    (rf/dispatch-sync [:db/set-value [:ui :selected-node-id] test-uuid])
    (is (= test-uuid (get-in @rdb/app-db [:ui :selected-node-id]))))
  (state/reset-db!)
  (rf/dispatch-sync [:db/set-value [:current-invocation :invoke-id] "invoke-456"])
  (is (= "invoke-456" (get-in @rdb/app-db [:current-invocation :invoke-id]))))

(deftest test-toggle-forking-mode
  (state/reset-db!)
  (is (false? (get-in @rdb/app-db [:ui :forking-mode?])))
  (rf/dispatch-sync [:ui/toggle-forking-mode])
  (is (true? (get-in @rdb/app-db [:ui :forking-mode?])))
  (rf/dispatch-sync [:ui/toggle-forking-mode])
  (is (false? (get-in @rdb/app-db [:ui :forking-mode?]))))
