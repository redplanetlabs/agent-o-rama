(ns com.rpl.agent-o-rama.ui.state-test
  (:require
   [cljs.test :refer-macros [deftest testing is]]
   [com.rpl.agent-o-rama.ui.state :as state]
   [com.rpl.specter :as s]
   [com.rpl.agent-o-rama.ui.dom])); Load DOM setup before tests

(deftest test-initial-db
  ;; Tests the initial-db structure to ensure critical keys exist and have expected shapes
  (testing "initial-db"
    (testing "contains current-invocation with required keys"
      (is (contains? state/initial-db :current-invocation))
      (is (contains? (:current-invocation state/initial-db) :invoke-id))
      (is (contains? (:current-invocation state/initial-db) :module-id))
      (is (contains? (:current-invocation state/initial-db) :agent-name)))

    (testing "contains invocations-data map"
      (is (contains? state/initial-db :invocations-data))
      (is (map? (:invocations-data state/initial-db))))

    (testing "contains invocations with pagination state"
      (is (contains? state/initial-db :invocations))
      (let [invocations (:invocations state/initial-db)]
        (is (contains? invocations :all-invokes))
        (is (vector? (:all-invokes invocations)))
        (is (contains? invocations :has-more?))
        (is (boolean? (:has-more? invocations)))
        (is (contains? invocations :loading?))
        (is (boolean? (:loading? invocations)))))

    (testing "contains UI state"
      (is (contains? state/initial-db :ui))
      (let [ui (:ui state/initial-db)]
        (is (contains? ui :forking-mode?))
        (is (boolean? (:forking-mode? ui)))))))

(deftest test-event-system
  ;; Tests basic event registration and dispatch functionality
  (testing "reg-event"
    (testing "registers a new event handler"
      (let [test-event-id  ::test-event
            handler-called (atom false)
            handler-fn     (fn [db & args]
                             (reset! handler-called true)
                             nil)] ; Return nil to indicate no state change
        (state/reg-event test-event-id handler-fn)
        (state/dispatch [test-event-id])
        (is @handler-called "Event handler should be called on dispatch")))))

(deftest test-db-set-value-event
  ;; Tests the :db/set-value event which sets values at Specter paths
  (testing ":db/set-value"
    (testing "sets a value at a simple path"
      ;; Remove and re-add the console-logger watch to avoid window reference issues
      (state/reset-db!)
      (let [test-uuid (random-uuid)]
        (state/dispatch [:db/set-value [:ui :selected-node-id] test-uuid])
        (is (= test-uuid (s/select-one [:ui :selected-node-id] (state/get-db))))))

    (testing "sets a nested value"
      (state/reset-db!)
      (state/dispatch [:db/set-value [:current-invocation :invoke-id] "invoke-456"])
      (is (= "invoke-456" (s/select-one [:current-invocation :invoke-id] (state/get-db)))))))

(deftest test-toggle-forking-mode
  ;; Tests the :ui/toggle-forking-mode event
  (testing ":ui/toggle-forking-mode"
    (testing "toggles forking mode from false to true"
      (remove-watch state/app-db :console-logger)
      (state/reset-db!)
      (add-watch state/app-db
                 :console-logger
                 (fn [key atom old-state new-state]
                   (when (exists? js/window)
                     (aset js/window
                           "db"
                           (clj->js new-state
                                    {:keyword-fn (fn [k]
                                                   (clojure.string/replace (name k) "-" "_"))})))))
      (is (false? (s/select-one [:ui :forking-mode?] (state/get-db))))
      (state/dispatch [:ui/toggle-forking-mode])
      (is (true? (s/select-one [:ui :forking-mode?] (state/get-db)))))

    (testing "toggles forking mode from true to false"
      (state/dispatch [:ui/toggle-forking-mode])
      (is (false? (s/select-one [:ui :forking-mode?] (state/get-db)))))))
